# TileServer #
This service talks to SODA Fountain and tilees the geojson it returns.

Right now it only does coordinate translation and bounding box.

This depends on socrata-http v3.0.0 which hasn't been released yet, to
compile it check out the "3.0.0" branch of socrata-http and do a
```sbt publishLocal```.

## Setup ##
Clone socrata-http

```
cd ~/Developer/Socrata
git clone git@github.com:socrata/socrata-http.git
```

Install socrata-http 3.0.0

```
cd ~/Developer/Socrata/socrata-http
git checkout -b 3.0.0 origin/3.0.0
sbt publishLocal
```

## Usage ##
Start the server with ```sbt run```.

Hit the server with:

```localhost:2048/tiles/{4x4}/{zoom}/{x}/{y}.json```


For example:

```http://localhost:2048/tiles/evgh-t69e/location/14/4207/6101.json```
