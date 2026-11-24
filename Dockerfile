FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS rest-build
WORKDIR /build
COPY TimeService/ .
RUN mvn --batch-mode --no-transfer-progress package

FROM tomcat:9.0.111-jdk21-temurin@sha256:fd25b8ac4ca3aee286aa2fa73e308240b6c9eb596b4da70ebeeb7afd67a9ac22 AS chat-build
WORKDIR /build
COPY ChatWebSocket/src/java/ .
RUN mkdir -p /chat-classes && javac --release 17 -encoding UTF-8 \
    -cp "/usr/local/tomcat/lib/*:/usr/local/tomcat/bin/tomcat-juli.jar" \
    -d /chat-classes websocket/chat/ChatAnnotation.java

FROM tomcat:9.0.111-jdk21-temurin@sha256:fd25b8ac4ca3aee286aa2fa73e308240b6c9eb596b4da70ebeeb7afd67a9ac22
ENV JAVA_OPTS="-Dlab4.http.port=8080"
COPY --from=rest-build /build/target/TimeService.war /usr/local/tomcat/webapps/
COPY --from=chat-build /chat-classes/ /usr/local/tomcat/webapps/examples/WEB-INF/classes/
COPY ChatWebSocket/web/ /usr/local/tomcat/webapps/examples/
