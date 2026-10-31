FROM eclipse-temurin:25-jre
ARG MODULE
WORKDIR /app
COPY ${MODULE}/build/libs/${MODULE}.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
