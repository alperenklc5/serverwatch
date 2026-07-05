#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file to
# you under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ----------------------------------------------------------------------------

# OS specific support.
darwin=false
case "$(uname)" in
  Darwin*) darwin=true ;;
esac

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ]; then
  if [ -x "$JAVA_HOME/jre/sh/java" ]; then
    JAVACMD="$JAVA_HOME/jre/sh/java"
  else
    JAVACMD="$JAVA_HOME/bin/java"
  fi
  if [ ! -x "$JAVACMD" ]; then
    die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
  fi
else
  JAVACMD="java"
  which java > /dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(pwd)}"

# Find the project base directory
MAVEN_PROJECTBASEDIR=$(find_maven_basedir "$(pwd)")
if [ $? -ne 0 ]; then
  exit 1
fi

WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

MAVEN_USER_HOME="${MAVEN_USER_HOME:-${HOME}/.m2}"
WRAPPER_DIR="$MAVEN_USER_HOME/wrapper/dists"

# Download the wrapper jar if not present
if [ ! -f "$WRAPPER_JAR" ]; then
  WRAPPER_URL=$(grep wrapperUrl "$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties" | cut -d= -f2-)
  "$JAVACMD" -classpath "$WRAPPER_JAR" \
    "-Dmaven.user.home=$MAVEN_USER_HOME" \
    "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR" \
    $WRAPPER_LAUNCHER "$@"
fi

exec "$JAVACMD" \
  $MAVEN_OPTS \
  $MAVEN_DEBUG_OPTS \
  -classpath "$WRAPPER_JAR" \
  "-Dmaven.user.home=$MAVEN_USER_HOME" \
  "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR" \
  $WRAPPER_LAUNCHER "$@"
