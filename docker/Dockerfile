FROM socrata/java

WORKDIR /srv/tileserver

# Some paths/locations we care about
env VERSION lastSuccessfulBuild
ENV TILESERVER_ROOT /srv/tileserver
ENV TILESERVER_ARTIFACT tileserver-assembly-LATEST.jar
ENV TILESERVER_CONFIG /etc/tileserver.conf
ENV TILESERVER_LOGBACK_CONFIG /etc/tileserver-logback.xml

RUN mkdir -p $TILESERVER_ROOT && \
    cd $TILESERVER_ROOT && \
    curl -O http://jenkins/job/tileserver-release/$VERSION/artifact/target/scala-2.10/$TILESERVER_ARTIFACT

# Tileserver environment settings
ENV TILESERVER_MAXMEM 2048m
ENV LOG_LEVEL info
ENV LOG_PATTERN %d{YYYY-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} host:"%X{X-Socrata-Host}" reqId:\(%X{X-Socrata-RequestId}\) 4x4:[%X{X-Socrata-Resource}] - %msg%n

# Add other required resources to the container
Add tileserver.conf.j2 /etc/
Add tileserver-logback.xml.j2 /etc/

ADD ship.d /etc/ship.d

EXPOSE 2048
