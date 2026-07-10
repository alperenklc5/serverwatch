FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY target/serverwatch-*.jar app.jar
COPY host-shell.sh /usr/local/bin/host-shell
RUN chmod +x /usr/local/bin/host-shell
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
