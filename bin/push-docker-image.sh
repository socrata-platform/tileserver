#!/bin/bash

# Your boot2docker instance should already be running
# from ./start-in-boot2docker.sh

PROJ_NAME='tileserver'
PROJ_ROOT=$(git rev-parse --show-toplevel 2>/dev/null)
PROJ_TAG=$(git tag -l 2>/dev/null | tail -n 1)
PROJ_VER=$(echo $PROJ_TAG | sed 's/^v//')
REG_HOST=registry.docker.aws-us-west-2-infrastructure.socrata.net
REGISTRY=$REG_HOST:5000

cd $PROJ_ROOT

ping -c 2 $REG_HOST >/dev/null 2>/dev/null \
    || { echo "AWS VPN connection required to push image." ; exit ; }

boot2docker ssh <<EOF
  # Point docker at the registry.
  sudo /etc/init.d/docker stop >/dev/null

  # Give docker time to spin down.
  while docker ps >/dev/null 2>/dev/null; do
    sleep 0.5
  done

  sudo sed -i '1s/$/\n\nEXTRA_ARGS="--insecure-registry registry.docker.aws-us-west-2-infrastructure.socrata.net:5000"\n/' /etc/init.d/docker
  sudo /etc/init.d/docker start >/dev/null

  # Give docker time to spin up.
  until docker ps >/dev/null 2>/dev/null; do
    sleep 0.5
  done
EOF

BUILD="-$1"
TAG_EXISTS=$(boot2docker ssh "docker images| egrep '$REGISTRY/internal/$PROJ_NAME[[:space:]]+$PROJ_VER$BUILD[[:space:]]'")

if [ "$TAG_EXISTS" ]; then
    echo "Tag already exists."
    exit 1
fi

boot2docker ssh <<EOF
  # Tag the image.
  docker tag $PROJ_NAME $REGISTRY/internal/$PROJ_NAME:$PROJ_VER$BUILD
  docker push $REGISTRY/internal/$PROJ_NAME:$PROJ_VER$BUILD
EOF
