FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .

# cache dependencies unless a change has been detected
RUN mvn dependency:go-offline -B

COPY src ./src

# package src code into a single .jar
RUN mvn clean package


# use openjdk runtime to run code
FROM openjdk:21-jdk AS runner

WORKDIR /app

# copy over api-gateway snapshot jar from builder step and place it in runner step at ./app.jar
COPY --from=builder ./app/target/api-gateway-0.0.1-SNAPSHOT.jar ./app.jar

# run on port 4004
EXPOSE 4004

# use Java cli to start the jdk
ENTRYPOINT ["java", "-jar", "app.jar"]
