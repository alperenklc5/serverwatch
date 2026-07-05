@echo off
set JAVA_HOME=C:\Java\jdk21
set PATH=%JAVA_HOME%\bin;C:\MAVEN\apache-maven-3.9.16\bin;%PATH%
cd /d C:\Users\Alperen\serverwatch
echo Starting PostgreSQL...
docker-compose -f docker-compose.dev.yml up -d
echo Starting ServerWatch...
mvn clean spring-boot:run -Dspring-boot.run.profiles=dev
pause