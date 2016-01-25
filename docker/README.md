# TileServer Docker Config #

## Building ##

### Pre-Requisites ###
Both
[`sbt`](http://www.scala-sbt.org/0.13/tutorial/Installing-sbt-on-Linux.html)
and a
[JDK](http://www.webupd8.org/2012/09/install-oracle-java-8-in-ubuntu-via-ppa.html)
(oracle-jdk-8 recommended)
must be installed to build.

To build the image, run:
```
cd .. # Change to project root.
sbt assembly
cp target/scala-2.11/tileserver-assembly-*.jar docker/tileserver-assembly.jar
docker build -t tileserver docker
```

Or, if you want to replace old versions:
```
cd .. # Change to project root.
docker build --rm -t tileserver docker
```

## Running ##
```
docker run -p 2048:2048 -e ZOOKEEPER_ENSEMBLE=<HOST> -e CARTO_HOST=<HOST> -d tileserver
```

For example:
```
docker run -p 2048:2048 -e \
ZOOKEEPER_ENSEMBLE='["10.110.35.228","10.110.38.74","10.110.40.61"]' \
-e CARTO_HOST='carto-renderer.app.marathon.aws-us-west-2-staging.socrata.net' \
-e CARTO_PORT=80 \
-d tileserver
```

## Required Environment Variables ##
* `ZOOKEEPER_ENSEMBLE` - A list of hostnames and ports of zookeeper instances. eg: ["10.0.0.1:2181", "10.0.0.2:2818"]
* `CARTO_HOST` - The carto renderer host.

## Optional Runtime Variables ##
See the [DockerFile](Dockerfile) for defaults.

* `JAVA_XMX`                - Sets the JVM heap size.
* `MIN_THREADS`             - Sets the minimum number of server threads.
* `MAX_THREADS`             - Sets the maximum number of server threads.

Note: `MAX_THREADS` should be set so that each thread has at least 10m
to use.  There are plans to address this in a future release.
