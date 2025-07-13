package com.pm.stack;

import io.github.cdimascio.dotenv.Dotenv;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LocalStack extends Stack {
    private final Vpc vpc;
    private final Cluster ecsCluster;

    // boilerplate code to create a new stack
    public LocalStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props); // scope is the stack we're currently in

        this.vpc = createVpc();

        DatabaseInstance authServiceDb = createDatabase("AuthServiceDB", "auth-service-db");
        DatabaseInstance patientServiceDb = createDatabase("PatientServiceDB", "patient-service-db");

        // can pass health checks to any dependent services
        CfnHealthCheck authServiceDbHealthCheck = createDbHealthCheck(authServiceDb, "AuthServiceDBHealthCheck");
        CfnHealthCheck patientServiceDbHealthCheck = createDbHealthCheck(patientServiceDb, "PatientServiceDBHealthCheck");

        CfnCluster mskCluster = createMskCluster();

        this.ecsCluster = createEcsCluster();

        Dotenv dotenv = Dotenv.load();
        String jwtSecret = dotenv.get("JWT_SECRET");

        FargateService authService = createFargateService("AuthService",
                "auth-service",
                List.of(4005),
                authServiceDb,
                Map.of("JWT_SECRET", jwtSecret));
        authService.getNode().addDependency(authServiceDbHealthCheck);
        authService.getNode().addDependency(authServiceDb);

        FargateService billingService = createFargateService("BillingService",
                "billing-service",
                List.of(4001, 9001), // gRPC runs on port 9001
                null,
                null);

        FargateService analyticsService = createFargateService("AnalyticsService",
                "analytics-service",
                List.of(4002),
                null,
                null);
        analyticsService.getNode().addDependency(mskCluster); // receives patient created events

        FargateService patientService = createFargateService("PatientService",
                "patient-service",
                List.of(4000),
                patientServiceDb,
                Map.of(
                        "BILLING_SERVICE_ADDRESS", "host.docker.internal",
                        "BILLING_SERVICE_GRPC_PORT", "9001"
                ));
        patientService.getNode().addDependency(patientServiceDb);
        patientService.getNode().addDependency(patientServiceDbHealthCheck);
        patientService.getNode().addDependency(billingService); // whenever patient created, gRPC request sent to billingService
        patientService.getNode().addDependency(mskCluster); // sends patient created events

        createApiGatewayService();
    }

    // VPC creates routing and networks required for our internal services to communicate with each other
    private Vpc createVpc() {
        return Vpc.Builder.create(this, "PatientManagementVPC")
                .vpcName("PatientManagementVPC")
                .maxAzs(2) // max 2 availability zones
                .build();
    }

    private DatabaseInstance createDatabase(String id, String dbName) {
        // builder pattern
        return DatabaseInstance.Builder
                .create(this, id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_17_2)
                                .build()
                        )
                )
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO)) // specify compute levels
                .allocatedStorage(20) // amount of storage allocated to this database
                .credentials(Credentials.fromGeneratedSecret("admin_user"))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY) // remove database storage when stack is removed. in prod, need to specify backup policy
                .build();
    }

    // create health check construct to return health status of db
    private CfnHealthCheck createDbHealthCheck(DatabaseInstance db, String id) {
        return CfnHealthCheck.Builder.create(this, id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP") // use TCP endpoint to check if port online
                        .port(Token.asNumber(db.getDbInstanceEndpointPort()))
                        .ipAddress(db.getDbInstanceEndpointAddress())
                        .requestInterval(30) // check db every 30 sec
                        .failureThreshold(3) // report failure if already tried 3 times
                        .build())
                .build();
    }

    // creates an Amazon Managed Streaming for Kafka (MSK) cluster
    private CfnCluster createMskCluster() {
        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("kafka-cluster")
                .kafkaVersion("2.8.0")
                .numberOfBrokerNodes(2) // in prod, usually have higher # of broker nodes for resiliency reasons
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.m5.xlarge") // specify compute power this Kafka cluster is going to use
                        .clientSubnets(vpc.getPrivateSubnets().stream()
                                .map(ISubnet::getSubnetId)
                                .collect(Collectors.toList())) // connect Kafka broker to VPC using VPC private subnet
                        .brokerAzDistribution("DEFAULT") // specify which brokers belong to which availability zones
                        .build())
                .build();
    }

    // creates am Amazon Elastic Container Service (ECS) cluster
    // e.g. when we create a service, other microservices can find this service by using:
    // auth-service.patient-management.local
    // we don't need to know IPs and internal addresses of our ECS services, all managed by cloud map service discovery
    private Cluster createEcsCluster() {
        return Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(vpc)
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder() // sets up cloud map namespace for service
                        .name("patient-management.local") // discovery in AWS ECS allowing microservices to find and
                        .build()) // communicate with each other using this domain
                .build();
    }

    // FargateService is a type of ECS service
    private FargateService createFargateService(String id,
                                                String imageName,
                                                List<Integer> ports,
                                                DatabaseInstance db,
                                                Map<String, String> additionalEnvVars) {

        // ECS task runs container
        // create task definition (blueprint)
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder.create(this, id + "Task")
                .cpu(256) // 256 CPU units
                .memoryLimitMiB(512) // 512 MB
                .build();

        ContainerDefinitionOptions.Builder containerOptions = ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry(imageName)) // image the container will be created from
                .portMappings(ports.stream()
                        .map(port -> PortMapping.builder()
                                .containerPort(port)
                                .hostPort(port) // container exposes this port so other services can access it
                                .protocol(Protocol.TCP)
                                .build())
                        .toList())
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                                        .logGroupName("/ecs/" + imageName)
                                        .removalPolicy(RemovalPolicy.DESTROY) // destroy logs when stack destroyed
                                        .retention(RetentionDays.ONE_DAY) // keep logs
                                        .build())
                                .streamPrefix(imageName)
                        .build()));
//                .build(); // keep container options as a Builder instance

        Map<String, String> envVars = new HashMap<>();
        // specify where the Kafka boostrap brokers are located
        envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS",
                "localhost.localstack.cloud:4510, localhost.localstack.cloud:4511, localhost.localstack.cloud:4512");

        if (additionalEnvVars != null) {
            envVars.putAll(additionalEnvVars); // merge all environment variables together
        }

        /*
        Configuring environment variables here very similar to adding env vars to our run configuration whenever
        we created an image and container for a given service during development in the IDE.
        */
        // if service requires database, configure environment variables for that db
        if (db != null) {
            envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://%s:%s/%s-db".formatted( // %s are placeholders for jdbc connection string
                    db.getDbInstanceEndpointAddress(), // gets added to first %s
                    db.getDbInstanceEndpointPort(), // gets added to second %s
                    imageName // added to third %s
            ));
            envVars.put("SPRING_DATASOURCE_USERNAME", "admin_user");
            envVars.put("SPRING_DATASOURCE_PASSWORD", db.getSecret().secretValueFromJson("password").toString());
            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update"); // run JPA hibernate stuff
            envVars.put("SPRING_SQL_INIT_MODE", "always"); // run data.sql script
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000"); // retries to spin up database a few times instead of failing right away
        }

        containerOptions.environment(envVars);
        taskDefinition.addContainer(imageName + "Container", containerOptions.build()); // this is how we link image to container and link container to a task definition

        return FargateService.Builder.create(this, id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false) // since service is internal
                .serviceName(imageName)
                .build();
    }

    private void createApiGatewayService() {
        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this, "APIGatewayTaskDefinition")
                .cpu(256) // 256 CPU units
                .memoryLimitMiB(512) // 512 MB
                .build();

        ContainerDefinitionOptions containerOptions = ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("api-gateway")) // image the container will be created from
                .environment(Map.of(
                        "SPRING_PROFILES_ACTIVE", "prod", // Spring will look for application-prod.yml file
                        "AUTH_SERVICE_URL", "http://host.docker.internal:4005" // LocalStack doesn't implement service discovery very well, just use Docker internal service discovery with port
                )) // add env vars inline
                .portMappings(List.of(4004).stream()
                        .map(port -> PortMapping.builder()
                                .containerPort(port)
                                .hostPort(port) // container exposes this port so other services can access it
                                .protocol(Protocol.TCP)
                                .build())
                        .toList())
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(LogGroup.Builder.create(this, "ApiGatewayLogGroup")
                                .logGroupName("/ecs/api-gateway")
                                .removalPolicy(RemovalPolicy.DESTROY) // destroy logs when stack destroyed
                                .retention(RetentionDays.ONE_DAY) // keep logs
                                .build())
                        .streamPrefix("api-gateway")
                        .build()))
                .build();

        taskDefinition.addContainer("APIGatewayContainer", containerOptions);

        ApplicationLoadBalancedFargateService apiGateway =
                ApplicationLoadBalancedFargateService.Builder.create(this, "APIGatewayService")
                        .cluster(ecsCluster)
                        .serviceName("api-gateway")
                        .taskDefinition(taskDefinition)
                        .desiredCount(1) // run 1 instance, can increase if more traffic
                        .healthCheckGracePeriod(Duration.seconds(60)) // how long the application load balancer will wait for API gateway container to start before it fails
                        .build();
    }

    public static void main(final String[] args) {
        // create new AWS CDK app
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        // synthesizer converts code into a cloud formation template
        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();

        new LocalStack(app, "localstack", props);
        app.synth();
        System.out.println("App synthesizing in progress...");
    }
}
