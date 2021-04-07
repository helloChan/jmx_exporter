FROM ${base.image}
RUN mkdir -p /app
COPY jmx_prometheus_javaagent-${project.version}.jar /app/
COPY echo_server.jar /app/
COPY config.yml /app/
WORKDIR /app/
CMD java -javaagent:jmx_prometheus_javaagent-${project.version}.jar=${metrics.port}:config.yml -jar echo_server.jar ${server.port}