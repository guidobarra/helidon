# 1st stage, build the app
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /helidon

# Cache Maven dependencies
COPY pom.xml .
RUN mvn package -Dmaven.test.skip -Declipselink.weave.skip -q || true

# Build the application
COPY src src
RUN mvn package -DskipTests -q

# 2nd stage, runtime image
FROM eclipse-temurin:21-jre
WORKDIR /helidon

COPY --from=build /helidon/target/helidon-nima.jar ./
COPY --from=build /helidon/target/libs ./libs

CMD java ${HELIDON_CONFIG_PROFILE:+-Dconfig.profile=$HELIDON_CONFIG_PROFILE} -jar helidon-nima.jar

EXPOSE 9292
