package com.pm.stack;

import software.amazon.awscdk.*;

public class LocalStack extends Stack {
    // boilerplate code to create a new stack
    public LocalStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props);
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
