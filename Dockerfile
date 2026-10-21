# syntax=docker/dockerfile:1

# ---- Build: compila el JAR y lo descompone en capas ----
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace

COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package \
    && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination /extracted

# ---- Runtime: JRE sobre Ubuntu (no Alpine: OpenPDF y fontconfig) ----
FROM eclipse-temurin:21-jre-noble
WORKDIR /app

RUN useradd -r -u 10001 app

COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
