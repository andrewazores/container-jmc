#!/bin/sh

set -x
set -e

function cleanup() {
    podman pod kill container-jfr
    podman pod rm container-jfr
}
trap cleanup EXIT

if [ -z "$CONTAINER_JFR_IMAGE" ]; then
    CONTAINER_JFR_IMAGE="quay.io/rh-jmc-team/container-jfr:latest"
fi

echo -e "\n\nRunning $CONTAINER_JFR_IMAGE ...\n\n"

if [ -z "$CONTAINER_JFR_RJMX_PORT" ]; then
    CONTAINER_JFR_RJMX_PORT=9091
fi

if [ -z "$CONTAINER_JFR_LOG_LEVEL" ]; then
    CONTAINER_JFR_LOG_LEVEL=ALL
fi

if [ -z "$CONTAINER_JFR_WEB_HOST" ]; then
    CONTAINER_JFR_WEB_HOST="0.0.0.0" # listens on all interfaces and hostnames for testing purposes
fi

if [ -z "$CONTAINER_JFR_WEB_PORT" ]; then
    CONTAINER_JFR_WEB_PORT=8181
fi

if [ -z "$CONTAINER_JFR_EXT_WEB_PORT" ]; then
    CONTAINER_JFR_EXT_WEB_PORT="$CONTAINER_JFR_WEB_PORT"
fi

if [ -z "$CONTAINER_JFR_LISTEN_HOST" ]; then
    CONTAINER_JFR_LISTEN_HOST="$CONTAINER_JFR_WEB_HOST"
fi

if [ -z "$CONTAINER_JFR_LISTEN_PORT" ]; then
    CONTAINER_JFR_LISTEN_PORT=9090;
fi

if [ -z "$CONTAINER_JFR_EXT_LISTEN_PORT" ]; then
    CONTAINER_JFR_EXT_LISTEN_PORT="$CONTAINER_JFR_LISTEN_PORT"
fi

if [ -z "$CONTAINER_JFR_AUTH_MANAGER" ]; then
    CONTAINER_JFR_AUTH_MANAGER="com.redhat.rhjmc.containerjfr.net.NoopAuthManager"
fi

if [ -z "$CONTAINER_JFR_REPORT_GENERATION_MAX_HEAP" ]; then
    CONTAINER_JFR_REPORT_GENERATION_MAX_HEAP="200"
fi

if [ ! -d "$PWD/truststore" ]; then
    mkdir "$PWD/truststore"
fi

if ! podman pod exists container-jfr; then
    podman pod create \
        --hostname container-jfr \
        --name container-jfr \
        --publish $CONTAINER_JFR_RJMX_PORT:$CONTAINER_JFR_RJMX_PORT \
        --publish $CONTAINER_JFR_EXT_LISTEN_PORT:$CONTAINER_JFR_LISTEN_PORT \
        --publish $CONTAINER_JFR_EXT_WEB_PORT:$CONTAINER_JFR_WEB_PORT
fi

podman run \
    --pod container-jfr \
    --mount type=tmpfs,target=/flightrecordings \
    --mount type=tmpfs,target=/templates \
    --mount type=bind,source="$PWD/truststore",destination=/truststore,relabel=shared,bind-propagation=shared \
    -e CONTAINER_JFR_DISABLE_SSL=$CONTAINER_JFR_DISABLE_SSL \
    -e CONTAINER_JFR_DISABLE_JMX_AUTH=$CONTAINER_JFR_DISABLE_JMX_AUTH \
    -e CONTAINER_JFR_LOG_LEVEL=$CONTAINER_JFR_LOG_LEVEL \
    -e CONTAINER_JFR_RJMX_USER=$CONTAINER_JFR_RJMX_USER \
    -e CONTAINER_JFR_RJMX_PASS=$CONTAINER_JFR_RJMX_PASS \
    -e CONTAINER_JFR_RJMX_PORT=$CONTAINER_JFR_RJMX_PORT \
    -e CONTAINER_JFR_CORS_ORIGIN=$CONTAINER_JFR_CORS_ORIGIN \
    -e CONTAINER_JFR_WEB_HOST=$CONTAINER_JFR_WEB_HOST \
    -e CONTAINER_JFR_WEB_PORT=$CONTAINER_JFR_WEB_PORT \
    -e CONTAINER_JFR_EXT_WEB_PORT=$CONTAINER_JFR_EXT_WEB_PORT \
    -e CONTAINER_JFR_LISTEN_HOST=$CONTAINER_JFR_LISTEN_HOST \
    -e CONTAINER_JFR_LISTEN_PORT=$CONTAINER_JFR_LISTEN_PORT \
    -e CONTAINER_JFR_EXT_LISTEN_PORT=$CONTAINER_JFR_EXT_LISTEN_PORT \
    -e CONTAINER_JFR_AUTH_MANAGER=$CONTAINER_JFR_AUTH_MANAGER \
    -e CONTAINER_JFR_ARCHIVE_PATH="/flightrecordings" \
    -e CONTAINER_JFR_TEMPLATE_PATH="/templates" \
    -e CONTAINER_JFR_REPORT_GENERATION_MAX_HEAP="$CONTAINER_JFR_REPORT_GENERATION_MAX_HEAP" \
    -e GRAFANA_DATASOURCE_URL=$GRAFANA_DATASOURCE_URL \
    -e GRAFANA_DASHBOARD_URL=$GRAFANA_DASHBOARD_URL \
    -e USE_LOW_MEM_PRESSURE_STREAMING=$USE_LOW_MEM_PRESSURE_STREAMING \
    -e KEYSTORE_PATH=$KEYSTORE_PATH \
    -e KEYSTORE_PASS=$KEYSTORE_PASS \
    -e KEY_PATH=$KEY_PATH \
    -e CERT_PATH=$CERT_PATH \
    --rm -it "$CONTAINER_JFR_IMAGE" "$@"
