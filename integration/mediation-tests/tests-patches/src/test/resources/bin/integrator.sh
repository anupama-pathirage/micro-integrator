#!/bin/sh
# ----------------------------------------------------------------------------
#  Copyright 2005-2012 WSO2, Inc. http://www.wso2.org
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# ----------------------------------------------------------------------------
# Main Script for the WSO2 Carbon Server
#
# Environment Variable Prequisites
#
#   CARBON_HOME   Home of WSO2 Carbon installation. If not set I will  try
#                   to figure it out.
#
#   JAVA_HOME       Must point at your Java Development Kit installation.
#
#   JAVA_OPTS       (Optional) Java runtime options used when the commands
#                   is executed.
#
# NOTE: Borrowed generously from Apache Tomcat startup scripts.
# -----------------------------------------------------------------------------

# OS specific support.  $var _must_ be set to either true or false.
#ulimit -n 100000

cygwin=false;
darwin=false;
os400=false;
mingw=false;
case "`uname`" in
CYGWIN*) cygwin=true;;
MINGW*) mingw=true;;
OS400*) os400=true;;
Darwin*) darwin=true
        if [ -z "$JAVA_VERSION" ] ; then
             JAVA_VERSION="CurrentJDK"
           else
             echo "Using Java version: $JAVA_VERSION"
           fi
           if [ -z "$JAVA_HOME" ] ; then
             JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Versions/${JAVA_VERSION}/Home
           fi
           ;;
esac

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '.*/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

# Get standard environment variables
PRGDIR=`dirname "$PRG"`

# Only set CARBON_HOME if not already set
[ -z "$CARBON_HOME" ] && CARBON_HOME=`cd "$PRGDIR/.." ; pwd`

# Set AXIS2_HOME. Needed for One Click JAR Download
AXIS2_HOME="$CARBON_HOME"

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
  [ -n "$CARBON_HOME" ] && CARBON_HOME=`cygpath --unix "$CARBON_HOME"`
  [ -n "$AXIS2_HOME" ] && CARBON_HOME=`cygpath --unix "$CARBON_HOME"`
fi

# For OS400
if $os400; then
  # Set job priority to standard for interactive (interactive - 6) by using
  # the interactive priority - 6, the helper threads that respond to requests
  # will be running at the same priority as interactive jobs.
  COMMAND='chgjob job('$JOBNAME') runpty(6)'
  system $COMMAND

  # Enable multi threading
  QIBM_MULTI_THREADED=Y
  export QIBM_MULTI_THREADED
fi

# For Migwn, ensure paths are in UNIX format before anything is touched
if $mingw ; then
  [ -n "$CARBON_HOME" ] &&
    CARBON_HOME="`(cd "$CARBON_HOME"; pwd)`"
  [ -n "$JAVA_HOME" ] &&
    JAVA_HOME="`(cd "$JAVA_HOME"; pwd)`"
  [ -n "$AXIS2_HOME" ] &&
    CARBON_HOME="`(cd "$CARBON_HOME"; pwd)`"
  # TODO classpath?
fi

if [ -z "$JAVACMD" ] ; then
  if [ -n "$JAVA_HOME"  ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
      # IBM's JDK on AIX uses strange locations for the executables
      JAVACMD="$JAVA_HOME/jre/sh/java"
    else
      JAVACMD="$JAVA_HOME/bin/java"
    fi
  else
    JAVACMD=java
  fi
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly."
  echo " CARBON cannot execute $JAVACMD"
  exit 1
fi

# if JAVA_HOME is not set we're not happy
if [ -z "$JAVA_HOME" ]; then
  echo "You must set the JAVA_HOME variable before running CARBON."
  exit 1
fi

if [ -e "$CARBON_HOME/wso2carbon.pid" ]; then
  PID=`cat "$CARBON_HOME"/wso2carbon.pid`
fi

# ----- Process the input command ----------------------------------------------
# Function to export variables from the given .env file
export_env_file() {
  local file_path="$1"

  # Check if the file exists
  if [ ! -f "$file_path" ]; then
    echo "Error: File '$file_path' not found."
    return 1  # Return with an error status
  fi

  # Read the .env file and export each variable to the environment
  while IFS='=' read -r key value; do
      # Ignore lines starting with '#' (comments) or empty lines
      case "$key" in
          \#*|"")
              # Skip comments or empty lines
              continue
              ;;
          *)
              # Trim surrounding whitespace from key and value
              key=$(echo "$key" | xargs)
              value=$(echo "$value" | xargs)
              # Export the key-value pair to the environment
              export "$key=$value"
              ;;
      esac
  done < "$file_path"

  echo "Environment variables loaded from $file_path."
}

args=""
for c in $*
do
    if [ "$c" = "--debug" ] || [ "$c" = "-debug" ] || [ "$c" = "debug" ]; then
          CMD="--debug"
          continue
    elif [ "$CMD" = "--debug" ]; then
          if [ -z "$PORT" ]; then
                PORT=$c
          fi
    elif [ "$c" = "--stop" ] || [ "$c" = "-stop" ] || [ "$c" = "stop" ]; then
          CMD="stop"
    elif [ "$c" = "--start" ] || [ "$c" = "-start" ] || [ "$c" = "start" ]; then
          CMD="start"
    elif [ "$c" = "--version" ] || [ "$c" = "-version" ] || [ "$c" = "version" ]; then
          CMD="version"
    elif [ "$c" = "--restart" ] || [ "$c" = "-restart" ] || [ "$c" = "restart" ]; then
          CMD="restart"
    elif [ "$c" = "--test" ] || [ "$c" = "-test" ] || [ "$c" = "test" ]; then
          CMD="test"
    else
        args="$args $c"
    fi
    # Check if the argument starts with --env-file=
    case "$c" in
      --env-file=*)
        file_path="${c#--env-file=}"
        export_env_file "$file_path"
        ;;
      *)
        continue
        ;;
    esac
done

if [ "$CMD" = "--debug" ]; then
  if [ "$PORT" = "" ]; then
    echo " Please specify the debug port after the --debug option"
    exit 1
  fi
  if [ -n "$JAVA_OPTS" ]; then
    echo "Warning !!!. User specified JAVA_OPTS will be ignored, once you give the --debug option."
  fi
  CMD="RUN"
  JAVA_OPTS="-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=$PORT"
  echo "Please start the remote debugging client to continue..."
elif [ "$CMD" = "start" ]; then
  if [ -e "$CARBON_HOME/wso2carbon.pid" ]; then
    if  ps -p $PID > /dev/null ; then
      echo "Process is already running"
      exit 0
    fi
  fi
  export CARBON_HOME="$CARBON_HOME"
# using nohup sh to avoid erros in solaris OS.TODO
  nohup sh "$CARBON_HOME"/bin/integrator.sh $args > /dev/null 2>&1 &
  exit 0
elif [ "$CMD" = "stop" ]; then
  export CARBON_HOME="$CARBON_HOME"
  kill -term `cat "$CARBON_HOME"/wso2carbon.pid`
  exit 0
elif [ "$CMD" = "restart" ]; then
  export CARBON_HOME="$CARBON_HOME"
  kill -term `cat "$CARBON_HOME"/wso2carbon.pid`
  process_status=0
  pid=`cat "$CARBON_HOME"/wso2carbon.pid`
  while [ "$process_status" -eq "0" ]
  do
        sleep 1;
        ps -p$pid 2>&1 > /dev/null
        process_status=$?
  done

# using nohup sh to avoid erros in solaris OS.TODO
  nohup sh "$CARBON_HOME"/bin/integrator.sh $args > /dev/null 2>&1 &
  exit 0
elif [ "$CMD" = "test" ]; then
    JAVACMD="exec "$JAVACMD""
elif [ "$CMD" = "version" ]; then
  cat "$CARBON_HOME"/bin/version.txt
  cat "$CARBON_HOME"/bin/wso2carbon-version.txt
  exit 0
fi

# ---------- Handle the SSL Issue with proper JDK version --------------------
jdk_version=`$JAVA_HOME/bin/java -version 2>&1 | grep -E "1\.(7|8)|21"`
if [ -z "$jdk_version" ]; then
   echo "Starting WSO2 Carbon (in unsupported JDK)"
   echo "[ERROR] CARBON is supported only on JDK 1.7, 1.8, or 21"
fi

CARBON_XBOOTCLASSPATH=""
for f in "$CARBON_HOME"/wso2/lib/xboot/*.jar
do
    if [ "$f" != "$CARBON_HOME/wso2/lib/xboot/*.jar" ];then
        CARBON_XBOOTCLASSPATH="$CARBON_XBOOTCLASSPATH":$f
    fi
done

JAVA_ENDORSED_DIRS="$CARBON_HOME/wso2/lib/endorsed":"$JAVA_HOME/jre/lib/endorsed":"$JAVA_HOME/lib/endorsed"

CARBON_CLASSPATH=""
if [ -e "$JAVA_HOME/lib/tools.jar" ]; then
    CARBON_CLASSPATH="$JAVA_HOME/lib/tools.jar"
fi
for f in "$CARBON_HOME"/bin/*.jar
do
    if [ "$f" != "$CARBON_HOME/bin/*.jar" ];then
        CARBON_CLASSPATH="$CARBON_CLASSPATH":$f
    fi
done
for t in "$CARBON_HOME"/wso2/lib/commons-lang*.jar
do
    CARBON_CLASSPATH="$CARBON_CLASSPATH":$t
done
# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
  JAVA_HOME=`cygpath --absolute --windows "$JAVA_HOME"`
  CARBON_HOME=`cygpath --absolute --windows "$CARBON_HOME"`
  AXIS2_HOME=`cygpath --absolute --windows "$CARBON_HOME"`
  CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
  JAVA_ENDORSED_DIRS=`cygpath --path --windows "$JAVA_ENDORSED_DIRS"`
  CARBON_CLASSPATH=`cygpath --path --windows "$CARBON_CLASSPATH"`
  CARBON_XBOOTCLASSPATH=`cygpath --path --windows "$CARBON_XBOOTCLASSPATH"`
fi

# ----- Execute The Requested Command -----------------------------------------

echo JAVA_HOME environment variable is set to $JAVA_HOME
echo CARBON_HOME environment variable is set to "$CARBON_HOME"

cd "$CARBON_HOME"

TMP_DIR="$CARBON_HOME"/tmp
if [ -d "$TMP_DIR" ]; then
rm -rf "$TMP_DIR"/*
fi

START_EXIT_STATUS=121
status=$START_EXIT_STATUS

if [ -z "$JVM_MEM_OPTS" ]; then
   java_version=$("$JAVACMD" -version 2>&1 | awk -F '"' '/version/ {print $2}')
   JVM_MEM_OPTS="-Xms256m -Xmx1024m"
   if [ "$java_version" \< "1.8" ]; then
      JVM_MEM_OPTS="$JVM_MEM_OPTS -XX:MaxPermSize=256m"
   fi
fi
echo "Using Java memory options: $JVM_MEM_OPTS"

#To monitor a Carbon server in remote JMX mode on linux host machines, set the below system property.
#   -Djava.rmi.server.hostname="your.IP.goes.here"

while [ "$status" = "$START_EXIT_STATUS" ]
do
    $JAVACMD \
    -Xbootclasspath/a:"$CARBON_XBOOTCLASSPATH" \
    $JVM_MEM_OPTS \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath="$CARBON_HOME/repository/logs/heap-dump.hprof" \
    $JAVA_OPTS \
    -Dcom.sun.management.jmxremote \
    -classpath "$CARBON_CLASSPATH" \
    -Djava.endorsed.dirs="$JAVA_ENDORSED_DIRS" \
    -Djava.io.tmpdir="$CARBON_HOME/wso2/tmp" \
    -Dcatalina.base="$CARBON_HOME/wso2/lib/tomcat" \
    -Dwso2.server.standalone=true \
    -Dcarbon.registry.root=/ \
    -Djava.command="$JAVACMD" \
    -Dcarbon.home="$CARBON_HOME" \
    -Dlogger.server.name="EI-Core" \
    -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager \
    -Dcarbon.config.dir.path="$CARBON_HOME/conf" \
    -Dcarbon.components.dir.path="$CARBON_HOME/wso2/components" \
    -Dcarbon.extensions.dir.path="$CARBON_HOME/extensions" \
    -Dcarbon.dropins.dir.path="$CARBON_HOME/dropins" \
    -Dtomcat.random.port.enable=disable \
    -Dcarbon.external.lib.dir.path="$CARBON_HOME/lib" \
    -Dcarbon.patches.dir.path="$CARBON_HOME/patches" \
    -Dcarbon.servicepacks.dir.path="$CARBON_HOME/servicepacks" \
    -Dcarbon.internal.lib.dir.path="$CARBON_HOME/wso2/lib" \
    -Dei.extendedURIBasedDispatcher=org.wso2.micro.integrator.core.handlers.IntegratorStatefulHandler \
    -Djava.util.logging.config.file="$CARBON_HOME/conf/etc/logging-bridge.properties" \
    -Dcomponents.repo="$CARBON_HOME/wso2/components/plugins" \
    -Dconf.location="$CARBON_HOME/conf"\
    -Dcom.atomikos.icatch.file="$CARBON_HOME/wso2/lib/transactions.properties" \
    -Dcom.atomikos.icatch.hide_init_file_path=true \
    -Dorg.apache.jasper.compiler.Parser.STRICT_QUOTE_ESCAPING=false \
    -Dorg.apache.jasper.runtime.BodyContentImpl.LIMIT_BUFFER=true \
    -Dcom.sun.jndi.ldap.connect.pool.authentication=simple  \
    -Dcom.sun.jndi.ldap.connect.pool.timeout=3000  \
    -Dorg.terracotta.quartz.skipUpdateCheck=true \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF8 \
    -Dskip.logging=true  \
    -Dskip.logging.pattern=\/carbon* \
    -Djava.net.preferIPv4Stack=true \
    -Dcom.ibm.cacheLocalHost=true \
    -DworkerNode=false \
    -Dorg.wso2.ignoreHostnameVerification=true \
    org.wso2.carbon.bootstrap.Bootstrap $*
    status=$?
done
