# TileServer #
This service talks to SODA Fountain and tiles the geojson it returns.

Right now it only does coordinate translation and bounding box.

This depends on socrata-http v3.0.0 which is now released., to
compile it check out the master branch of socrata-http and do a sbt publish local.

## Setup ##
Clone and install socrata-http

```
cd ~/Developer/Socrata
git clone git@github.com:socrata/socrata-http.git
cd socrata-http
sbt publishLocal
```

## Usage ##
Start the server with ```sbt run```.

Hit the server with:

```
localhost:2048/tiles/{4x4}/{zoom}/{x}/{y}.json
```


For example:

```
http://localhost:2048/tiles/evgh-t69e/location/14/4207/6101.json
```
