# TileServer #
This service talks to SODA Fountain and converts the geojson it
returns to vector tiles.

## Usage ##
Start the server with ```sbt run```.

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

The following is also supported for debugging purposes,
do not depend on the output format.

* .txt:  text representation of the proto-buffer.
