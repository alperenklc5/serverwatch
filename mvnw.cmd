@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file to
@REM you under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET "__MVNW_ARG0_NAME__=%~nx0")
@SET ___MVNW_QDOT__=.
@SET __MVNW_Z__=
@SET "MAVEN_PROJECTBASEDIR=%__MVNW_Z__%"
@SET __MVNW_Z__=

@IF NOT "%MAVEN_BASEDIR%"=="" SET "MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%"
@IF "%MAVEN_PROJECTBASEDIR%"=="" SET "MAVEN_PROJECTBASEDIR=%~dp0"

@SET WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain
@SET DOWNLOAD_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"

@IF EXIST %WRAPPER_JAR% (
    @SET INIT_SCRIPT=
) ELSE (
    @ECHO Downloading Maven Wrapper...
    @IF NOT "%MVNW_VERBOSE%"=="" ECHO "Downloading from: %DOWNLOAD_URL%"
    @powershell -Command "&{"^
        "$webclient = new-object System.Net.WebClient;"^
        "if (-not ([string]::IsNullOrEmpty('%MVNW_USERNAME%') -and [string]::IsNullOrEmpty('%MVNW_PASSWORD%'))) {"^
        "$webclient.Credentials = new-object System.Net.NetworkCredential('%MVNW_USERNAME%', '%MVNW_PASSWORD%');"^
        "}"^
        "$webclient.DownloadFile('%DOWNLOAD_URL%', '%WRAPPER_JAR%')"^
        "}"
    @IF "%MVNW_VERBOSE%"=="true" ECHO Finished Downloading Maven Wrapper
)

@IF "%JAVA_HOME%"=="" (
    @SET JAVACMD=java.exe
) ELSE (
    @SET "JAVACMD=%JAVA_HOME%\bin\java.exe"
)

"%JAVACMD%" %MAVEN_OPTS% %MAVEN_DEBUG_OPTS% ^
  -classpath %WRAPPER_JAR% ^
  "-Dmaven.user.home=%USERPROFILE%\.m2" ^
  "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
  "%JAVACMD%" %MAVEN_OPTS% %MAVEN_DEBUG_OPTS% -classpath %WRAPPER_JAR% "-Dmaven.user.home=%USERPROFILE%\.m2" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" %WRAPPER_LAUNCHER% %MAVEN_CONFIG% %*
