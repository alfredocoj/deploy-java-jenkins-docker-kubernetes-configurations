ARG ARG_JAVA_APP_DIR=/usr/local/ithappens

FROM fabric8/java-alpine-openjdk8-jdk
RUN apk update && apk upgrade && apk add netcat-openbsd
RUN apk add tzdata
RUN cp /usr/share/zoneinfo/Brazil/East /etc/localtime
RUN echo "Brazil/East" >  /etc/timezone
RUN unlink /etc/localtime && ln -s /usr/share/zoneinfo/Etc/GMT+3 /etc/localtime

COPY m2 /deployments/m2
COPY app.jar /deployments/app.jar

ENV JAVA_APP_DIR=$ARG_JAVA_APP_DIR