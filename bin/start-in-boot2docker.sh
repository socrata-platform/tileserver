#!/bin/bash

PROJ_NAME='tileserver'
PORT=2048
ENVFILE="/tmp/${PROJ_NAME}-env"     # This is the boot2docker vm path.

ping -c 2 jenkins.sea1.socrata.com >/dev/null 2>/dev/null \
    || { echo "VPN connection required to download artifact(s)." ; exit ; }

boot2docker init
boot2docker down

VBoxManage sharedfolder add 'boot2docker-vm' \
    --name "${PROJ_NAME}-docker" \
    --hostpath "$PWD/docker" \
    --automount 2>/dev/null

echo "Starting VM..."
$(boot2docker up 2>&1 | tail -n 4)

ADDRS=$(ifconfig | grep 'inet ' | awk '{print $2}' | grep -v 127.0.0.1)

if ! [ "${ENSEMBLE}" ]; then
    ENSEMBLE=$(
        echo "${ADDRS}" | while read addr; do
            echo "${addr}:2181"
        done
    )

    ENSEMBLE=$(echo ${ENSEMBLE} | sed 's/ /, /g')
fi

echo "About to ssh into docker container!"

boot2docker ssh <<EOF
    mkdir ${PROJ_NAME}-docker
    sudo mount -t vboxsf -o uid=1000,gid=50 ${PROJ_NAME}-docker ${PROJ_NAME}-docker
    cd ${PROJ_NAME}-docker
    docker build --no-cache --rm -t ${PROJ_NAME} .
    echo ZOOKEEPER_ENSEMBLE=[${ENSEMBLE}] > ${ENVFILE}
    sed -i 's/\[/["/' ${ENVFILE}
    sed -i 's/, /", "/g' ${ENVFILE}
    sed -i 's/]/"]/' ${ENVFILE}
    echo starting ${PROJ_NAME}
    docker run --env-file=${ENVFILE} -p ${PORT}:${PORT} -d ${PROJ_NAME}
EOF

echo "Forwarding localhost:${PORT} to boot2docker (Ctrl+C to exit)"
echo "boot2docker ssh -L ${PORT}:localhost:${PORT} -N"
boot2docker ssh -L ${PORT}:localhost:${PORT} -N
