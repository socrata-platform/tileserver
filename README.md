# TileServer #
This service talks to SODA Fountain and converts the geojson it
returns to vector tiles.

## Usage ##
Start the server with `sbt run`.

Hit the server with:

```
http://localhost:2048/tiles/{4x4}/{point_column}/{zoom}/{x}/{y}.{extension}
```

For example:

```
http://localhost:2048/tiles/evgh-t69e/location/14/4207/6101.json
```

The following extensions are supported:

* .pbf:  proto-buffer / binary vector tile.
* .bpbf: base 64 encoded proto-buffer.
* .json: the raw json returned from soda-fountain.
* .png: renderered vector tile.

The following is also supported for debugging purposes,
do not depend on the output format.

* .txt:  text representation of the proto-buffer.

## X-Socrata-Host ##
Calling the tileserver requires setting X-Socrata-Host properly.

A valid staging value is: dataspace-demo.test-socrata.com

A valid RC value is: dataspace-demo.rc-socrata.com

A valid production value is: dataspace.demo.socrata.com

## Dependencies ##
TileServer uses ZooKeeper for service discovery, so it must have
access to a running instance (there are IPs for the staging ensemble
in `reference.conf`).

.png rendering requires access to a running instance of 
[carto-renderer](http://github.com/socrata-platform/carto-renderer)

Both of these are configured in `/src/main/resources/reference.conf`
and default to `localhost`.
