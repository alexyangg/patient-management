#!/bin/bash

set -e # stops the script if any command fails

aws --endpoint-url=http://localhost:4566 cloudformation delete-stack \
    --stack-name patient-management

# need to set endpoint since we're using LocalStack and not a real AWS instance
aws --endpoint-url=http://localhost:4566 cloudformation deploy \
    --stack-name patient-management \
    --template-file "./cdk.out/localstack.template.json" # tell aws the template file in this directory

# elbv2 - elastic load balancer v2 command
aws --endpoint-url=http://localhost:4566 elbv2 describe-load-balancers \
    --query "LoadBalancers[0].DNSName" --output text # since we only have 1 loadbalancer, query the first one
    # print out load balancer address we use to access API gateway in our stack as text