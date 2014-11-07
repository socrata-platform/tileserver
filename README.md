# TileServer #

This service talks to SODA Fountain and tilees the geojson it returns.

Right now it only does coordinate translation and bounding box.

This depends on socrata-http v3.0.0 which hasn't been released yet, to
compile it check out the "3.0.0" branch of socrata-http and do a ```sbt publishLocal```.
