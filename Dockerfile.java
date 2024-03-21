FROM sapmachine:21-jdk-ubuntu-22.04

COPY . /home
WORKDIR /home/java
RUN curl -L -o dd-java-agent.jar 'https://dtdg.co/latest-java-tracer'
RUN ./gradlew --no-daemon installDist
CMD JAVA_OPTS="-Ddd.agent.host=$DD_AGENT_HOST -Ddd.profiling.enabled=true -javaagent:dd-java-agent.jar" ./build/install/movies-api-java/bin/movies-api-java
