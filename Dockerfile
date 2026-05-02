FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/mas-online-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
