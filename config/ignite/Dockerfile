FROM maven:3-amazoncorretto-11 AS prometheus
WORKDIR ./prometheus
COPY ./config/ignite/prometheus .
RUN mvn package

FROM apacheignite/ignite:2.13.0-jdk11
COPY ./config/ignite/ignite.xml $IGNITE_HOME/ignite.xml
COPY --from=prometheus ./prometheus/target/ignite-prometheus-1.0-SNAPSHOT-jar-with-dependencies.jar $IGNITE_HOME/user-libs/ignite-prometheus-1.0.jar
RUN mv $IGNITE_HOME/libs/optional/ignite-opencensus $IGNITE_HOME/libs
# Needed for Ignite OpenCensus
ENV USER_LIBS=$IGNITE_HOME/user-libs/ignite-prometheus-1.0.jar
ENV CONFIG_URI=$IGNITE_HOME/ignite.xml