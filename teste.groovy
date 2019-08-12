env.MAX_CORES=1
env.REQUEST_CORES="500m"
env.MAX_MEMORY="256Mi"
env.REQUEST_MEMORY="100Mi"
env.REPLICAS=2
env.JAVA_OPTIONS="-Djava.security.egd=file:/dev/./urandom -XX:+UseG1GC -Dserver.port=8080 -Dspring.profiles.active=${PROFILE}"
env.PORT_KUBERNETES=30031
env.URL_TEST="/api/teste/"
env.NODE_KUBERNETES="k8s-node-01"
