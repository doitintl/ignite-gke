FROM maven:3.6.3-jdk-14 AS build  
COPY pom.xml /usr/src/app/pom.xml
RUN mvn -f /usr/src/app/pom.xml dependency:resolve-plugins dependency:go-offline
COPY src /usr/src/app/src
RUN mvn -f /usr/src/app/pom.xml dependency:copy-dependencies package -o

FROM openjdk:14-alpine
COPY --from=build /usr/src/app/target/igniteTestClient-0.1.0.jar /usr/app/igniteTestClient-0.1.0.jar
COPY --from=build /usr/src/app/target/dependency /usr/app/dependency
ENTRYPOINT ["java","--add-opens=java.base/java.nio=ALL-UNNAMED", "-XX:MaxRAMPercentage=95.0","-jar","/usr/app/igniteTestClient-0.1.0.jar"]
