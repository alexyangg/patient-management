package com.pm.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.rds.*;

public class LocalStack extends Stack {
    private final Vpc vpc;

    // boilerplate code to create a new stack
    public LocalStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props); // scope is the stack we're currently in

        this.vpc = createVpc();

        DatabaseInstance authServiceDb = createDatabase("AuthServiceDB", "auth-service-db");
        DatabaseInstance patientServiceDb = createDatabase("PatientServiceDB", "patient-service-db");
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
