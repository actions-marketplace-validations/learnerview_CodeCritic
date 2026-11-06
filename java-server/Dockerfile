FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
ENV MAVEN_OPTS="-Xmx512m"
COPY java-server/pom.xml .
COPY java-server/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# Install SpotBugs CLI (required for static bug detection) and curl (used by the
# Compose healthcheck). The base JDK image provides javac, which SpotBugsRunner
# uses to compile submitted source before analysis.
ARG SPOTBUGS_VERSION=4.10.4
ARG SPOTBUGS_SHA256=72bc0d4edd686e462c0f71f42a049b27bf4da6708797ff7b2b56dd202714b4e5
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    curl -fsSL "https://repo1.maven.org/maven2/com/github/spotbugs/spotbugs/${SPOTBUGS_VERSION}/spotbugs-${SPOTBUGS_VERSION}.tgz" -o /tmp/spotbugs.tgz && \
    echo "${SPOTBUGS_SHA256}  /tmp/spotbugs.tgz" | sha256sum -c - && \
    tar xzf /tmp/spotbugs.tgz -C /opt && \
    rm /tmp/spotbugs.tgz && \
    chmod +x "/opt/spotbugs-${SPOTBUGS_VERSION}/bin/spotbugs" && \
    ln -s "/opt/spotbugs-${SPOTBUGS_VERSION}/bin/spotbugs" /usr/local/bin/spotbugs && \
    javac --version && spotbugs -version

EXPOSE 8080
# Render free tier = 512 MB RAM / 0.1 CPU: cap the JVM hard so growth
# can never kill the instance. Serial GC is the lowest-overhead choice
# for a small heap.
ENTRYPOINT ["java", "-Xmx256m", "-Xms64m", "-XX:MaxMetaspaceSize=128m", "-XX:ReservedCodeCacheSize=64m", "-XX:+UseSerialGC", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]