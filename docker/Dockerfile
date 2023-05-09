FROM socrata/java8-focal

# Service specifif settings.
ENV SERVER_NAME tileserver

# Some paths/locations we care about
ENV SERVER_ROOT      /srv/${SERVER_NAME}/
ENV SERVER_CONFIG    ${SERVER_NAME}.conf
ENV LOGBACK_CONFIG   ${SERVER_NAME}-logback.xml
ENV SERVER_ARTIFACT  ${SERVER_NAME}-assembly.jar

# Environment Settings.
EXPOSE 2048
ENV CARTO_PORT 4096
ENV JAVA_XMX 512m

# Java 7 PermGen default.
ENV COMPRESSED_CLASS_SIZE 64m
# Compressed_class_size + Java 7 Permgen default.
ENV MAX_META_SIZE 128m

# Threads currently use about 100m each.
ENV MIN_THREADS 3
ENV MAX_THREADS 5
ENV QUERY_TIMEOUT 1m
ENV JMX_PORT 2049
EXPOSE 2049

# Log Settings.
ENV LOG_LEVEL info
ENV LOG_PATTERN %d %-5p [%t]  [%X{X-Socrata-RequestId}] [%X{X-Socrata-Resource}] %c{36} "%X{X-Socrata-Host}" %m%n %ex{full}

# Add other required resources to the container
ADD ship.d /etc/ship.d
Add ${SERVER_CONFIG}.j2 /etc/
Add ${LOGBACK_CONFIG}.j2 /etc/
ADD ${SERVER_ARTIFACT} ${SERVER_ROOT}

