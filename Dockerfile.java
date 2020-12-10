FROM openjdk:14

COPY . /home
WORKDIR /home/java
RUN curl -L -o dd-java-agent.jar 'https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=com.datadoghq&a=dd-java-agent&v=0.69.0'
RUN ./gradlew --no-daemon installDist
CMD JAVA_OPTS="-Ddd.agent.host=$DD_AGENT_HOST -Ddd.profiling.enabled=true -javaagent:dd-java-agent.jar" ./build/install/movies-api-java/bin/movies-api-java
