# maven:3.5-jdk-8-alpine
FROM maven:3.5-jdk-8-alpine AS build

WORKDIR /workspace

COPY ./pom.xml /workspace/pom.xml
#RUN mvn dependency:go-offline
#RUN mvn verify clean --fail-never
RUN mvn package --fail-never

COPY ./src/ /workspace/src/
RUN mvn package

FROM openjdk:8-jre-alpine
WORKDIR /workspace
COPY --from=build /workspace/target/FileStorageManager-0.0.1-SNAPSHOT.jar /workspace

RUN mkdir /logs
RUN mkdir /nas-drive

EXPOSE 8080

CMD sleep 5 && java -jar FileStorageManager-0.0.1-SNAPSHOT.jar