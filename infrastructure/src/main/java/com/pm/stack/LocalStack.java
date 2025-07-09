package com.pm.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.Vpc;

public class LocalStack extends Stack {
    private final Vpc vpc;

    // boilerplate code to create a new stack
    public LocalStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props); // scope is the stack we're currently in

        this.vpc = createVpc();
    }

    // VPC creates routing and networks required for our internal services to communicate with each other
    private Vpc createVpc() {
        return Vpc.Builder.create(this, "PatientManagementVPC")
                .vpcName("PatientManagementVPC")
                .maxAzs(2) // max 2 availability zones
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
