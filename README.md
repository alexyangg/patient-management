﻿# Patient Management System

A microservices-based Patient Management System built using Java, Spring Boot, Kafka, PostgreSQL, Docker, and AWS. This project features a production-ready architecture with modular services for managing patients, billing, authentication, and analytics using REST, gRPC, and Kafka message streaming.

## Tech Stack
- **Languages & Frameworks**: Java, Spring Boot, gRPC, JPA
- **Messaging**: Apache Kafka
- **Database**: PostgreSQL (containerized with Docker volumes)
- **Infrastructure**: Docker, AWS (via LocalStack), CloudFormation (IaC)
- **API Gateway**: Spring Cloud Gateway
- **Testing**: JUnit, REST Assured
- **Authentication**: JWT via custom Auth Service
- **Deployment**: Docker Compose, AWS ECS (via LocalStack)

## Microservices
- `patient-service`: Exposes REST endpoints to manage patients. Calls `billing-service` via gRPC and produces Kafka events.
- `billing-service`: Handles billing accounts using gRPC.
- `auth-service`: Handles login, JWT generation and validation.
- `analytics-service`: Consumes Kafka events and logs them.
- `api-gateway`: Secures routes and handles routing via Spring Cloud Gateway.

## Features
- REST API for managing patient data
- JWT-based authentication
- gRPC communication between services
- Kafka-based event producing/consumption
- PostgreSQL for persistent data storage (Docker volumes with health checks)
- AWS deployment using LocalStack + CloudFormation

## Deployment
This project includes an `infrastructure` folder that sets up:
- VPC
- RDS (Postgres)
- MSK (Kafka)
- ECS Services
- Load Balancer

The provided CloudFormation template is used to simulate AWS deployment in LocalStack.
