#!/bin/bash

echo `docker port `hostname` $AMBRY_PORT`

AMBRY_SERVER_PID=0
AMBRY_FRONTEND_PID=0
root_dir=`ls /opt/ | grep ambry`
AMBRY_HOME=/opt/${root_dir}

# see https://medium.com/@gchudnov/trapping-signals-in-docker-containers-7a57fdda7d86#.bh35ir4u5
term_handler() {
  echo 'Stopping Ambry Server....'
  if [ $AMRBY_SERVER_PID -ne 0 ]; then
    kill -s TERM "$AMBRY_SERVER_PID"
    wait "$AMBRY_SERVER_PID"
  fi
  echo 'Stopping Ambry Frontend....'
  if [ $AMRBY_FRONTEND_PID -ne 0 ]; then
    kill -s TERM "$AMBRY_FRONTEND_PID"
    wait "$AMBRY_FRONTEND_PID"
  fi
  echo 'Ambry stopped.'
  exit
}


# Capture kill requests to stop properly
trap "term_handler" SIGHUP SIGINT SIGTERM
echo $AMBRY_HOME

cd $AMBRY_HOME && ./gradlew allJar
cd $AMBRY_HOME/target && mkdir logs

java -Dlog4j.configuration=file:$AMBRY_HOME/config/log4j.properties -jar ambry.jar --serverPropsFilePath $AMBRY_HOME/config/server.properties --hardwareLayoutFilePath $AMBRY_HOME/config/HardwareLayout.json --partitionLayoutFilePath $AMBRY_HOME/config/PartitionLayout.json > logs/server.log &
AMBRY_SERVER_PID=$!

java -Dlog4j.configuration=file:$AMBRY_HOME/config/log4j.properties -cp "*" com.github.ambry.frontend.AmbryFrontendMain --serverPropsFilePath $AMBRY_HOME/config/frontend.properties --hardwareLayoutFilePath $AMBRY_HOME/config/HardwareLayout.json --partitionLayoutFilePath $AMBRY_HOME/config/PartitionLayout.json > logs/frontend.log &
AMBRY_FRONTEND_PID=$!

sleep 2

curl http://localhost:1174/healthCheck

wait
