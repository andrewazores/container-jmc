#!/bin/sh

# FIXME this should be an integration test

# run smoketest.sh first in a separate terminal, then this once the ContainerJFR instance has finished startup

# FIXME once RuleProcessor can apply new rules to existing targets, remove podman commands

podman kill vertx-fib-demo

demoAppServiceUrl="service:jmx:rmi:///jndi/rmi://container-jfr:9093/jmxrmi"

curl -vLk \
    -X POST \
    -F username=admin \
    -F password=adminpass123 \
    -F persist=true \
    "https://0.0.0.0:8181/api/v2/credentials/$(echo -n $demoAppServiceUrl | jq -sRr @uri)"

curl -vLk \
    -X POST \
    -F name="Default Rule" \
    -F targetAlias="es.andrewazor.demo.Main" \
    -F description="This is a test rule" \
    -F eventSpecifier="template=Continuous,type=TARGET" \
    -F archivalPeriodSeconds="60" \
    -F preservedArchives="3" \
    https://0.0.0.0:8181/api/v2/rules

podman run \
    --name vertx-fib-demo \
    --pod container-jfr \
    --rm -d quay.io/andrewazores/vertx-fib-demo:0.4.0