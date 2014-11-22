# TileServer #
This service talks to SODA Fountain and converts the geojson it
returns to vector tiles.

## Usage ##
Start the server with ```sbt run```.

Hit the server with:

```
localhost:2048/tiles/{4x4}/{zoom}/{x}/{y}.{extension}
```

For example:

```
http://localhost:2048/tiles/evgh-t69e/location/14/4207/6101.json
```

The following extensions are supported:

* .pbf:  proto-buffer / binary vector tile.
* .bpbf: base 64 encoded proto-buffer.
* .txt:  text representation of the proto-buffer.
* .json: the raw json returned from soda-fountain.
