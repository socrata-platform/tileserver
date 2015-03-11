Tile Server Docker
==================

To build the image, run:

    docker build --rm -t tileserver .

Optional environment variables:

Tileserver requires the environment variable ZOOKEEPER_ENSEMBLE to be
set with the syntax:

["10.0.0.1:2181", "10.0.0.2:2818"]

TILESERVER_MAXMEM     (default 2048m)  
