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

RUN addgroup -g 10000 bsfm && \
    addgroup -g 10100 nas-files && \
    addgroup -g 10101 nas-specials && \
    adduser -g "" -G bsfm -S -D -H -u 10000 bsfm && \
    adduser bsfm nas-files && \
    adduser bsfm nas-specials

RUN mkdir /logs && mkdir /nas-drive

EXPOSE 8080

CMD chown bsfm /logs /nas-drive && sleep 5 && su bsfm -s /bin/sh -c "java -jar FileStorageManager-0.0.1-SNAPSHOT.jar"

COPY --from=build /workspace/target/FileStorageManager-0.0.2-SNAPSHOT.jar /workspace
