#!/bin/bash

DEV_ROOT="$HOME/Developer/Socrata/"
ENVFILE="/tmp/tileserver-env"     # This is the boot2docker vm path.

ping -c 2 jenkins.sea1.socrata.com >/dev/null 2>/dev/null \
    || { echo "VPN connection required to download artifact(s)." ; exit ; }

boot2docker init
boot2docker down

if [ ! -e "$DEV_ROOT/tileserver-docker/.git" ]; then
    cd "$DEV_ROOT"
    git clone --recursive git@git.socrata.com:tileserver-docker.git
fi

VBoxManage sharedfolder add 'boot2docker-vm' \
    --name 'tileserver-docker' \
    --hostpath "$DEV_ROOT/tileserver-docker" \
    --automount 2>/dev/null

echo "Starting VM..."
$(boot2docker up 2>&1 | tail -n 4)

ADDRS=$(ifconfig | grep 'inet ' | awk '{print $2}' | grep -v 127.0.0.1)

if ! [ "$ENSEMBLE" ]; then
    ENSEMBLE=$(
        echo "$ADDRS" | while read addr; do
            echo "$addr:2181"
        done
    )

    ENSEMBLE=$(echo $ENSEMBLE | sed 's/ /, /g')
fi

boot2docker ssh <<EOF
    mkdir tileserver-docker
    sudo mount -t vboxsf -o uid=1000,gid=50 tileserver-docker tileserver-docker
    cd tileserver-docker
    docker build --no-cache --rm -t tileserver .
    echo ZOOKEEPER_ENSEMBLE=[$ENSEMBLE] > $ENVFILE
    sed -i 's/\[/["/' $ENVFILE
    sed -i 's/, /", "/g' $ENVFILE
    sed -i 's/]/"]/' $ENVFILE
    echo starting tileserver
    docker run --env-file=$ENVFILE -p 2048:2048 -d tileserver
EOF

echo 'Forwarding localhost:2048 to boot2docker (Ctrl+C to exit)'
echo "boot2docker ssh -L 2048:localhost:2048 -N"
boot2docker ssh -L 2048:localhost:2048 -N
