/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
// Modified from the Apache Tomcat example to add commands and REST calculator calls.
package websocket.chat;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@ServerEndpoint(value = "/websocket/chat")
public class ChatAnnotation {

    private static final Log log = LogFactory.getLog(ChatAnnotation.class);

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private static final String GUEST_PREFIX = "Guest";
    private static final AtomicInteger connectionIds = new AtomicInteger(0);
    private static final Set<ChatAnnotation> connections = new CopyOnWriteArraySet<>();

    private final String nickname;
    private Session session;
    /*
     * The queue of messages that may build up while another message is being sent. The thread that sends a message is
     * responsible for clearing any queue that builds up while that message is being sent.
     */
    private Queue<String> messageBacklog = new ArrayDeque<>();
    private boolean messageInProgress = false;

    public ChatAnnotation() {
        nickname = GUEST_PREFIX + connectionIds.getAndIncrement();
    }


    @OnOpen
    public void start(Session session) {
        this.session = session;
        connections.add(this);
        String message = String.format("* %s %s", nickname, "has joined.");
        broadcast(message);
    }


    @OnClose
    public void end() {
        connections.remove(this);
        String message = String.format("* %s %s", nickname, "has disconnected.");
        broadcast(message);
    }


    @OnMessage
    public void incoming(String message) {
        String command = message.trim();
        if (command.equalsIgnoreCase("time")) {
            broadcast("Current time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        } else if (command.equalsIgnoreCase("dol")) {
            broadcast("2.86");
        } else if (command.equalsIgnoreCase("eu")) {
            broadcast("3.26");
        } else if (command.startsWith("=") || command.matches("[0-9.+*/\\s-]+")) {
            calculate(command.startsWith("=") ? command.substring(1).trim() : command);
        } else {
            broadcast(nickname + ": " + message);
        }
    }

    private void calculate(String expression) {
        if (expression.length() > 512) {
            broadcast("Ошибка: выражение слишком длинное.");
            return;
        }

        String port = System.getProperty("lab4.http.port", "8079");
        String encoded = URLEncoder.encode(expression, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/TimeService/api/calculate?expression=" + encoded))
                .timeout(Duration.ofSeconds(3)).GET().build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String json = response.body();
                String result = json.substring("{\"result\":".length(), json.length() - 1);
                broadcast(nickname + ": " + expression + " = " + result);
            } else if (response.statusCode() == 400) {
                broadcast("Ошибка: неверное выражение или деление на ноль.");
            } else {
                broadcast("Ошибка REST-сервиса: HTTP " + response.statusCode());
            }
        } catch (IOException ex) {
            log.warn("Calculator request failed", ex);
            broadcast("Ошибка: REST-сервис недоступен.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Calculator request interrupted", ex);
        }
    }

    @OnError
    public void onError(Throwable t) throws Throwable {
        log.error("Chat Error: " + t.toString(), t);
    }


    /*
     * synchronized blocks are limited to operations that are expected to be quick. More specifically, messages are not
     * sent from within a synchronized block.
     */
    private void sendMessage(String msg) throws IOException {

        synchronized (this) {
            if (messageInProgress) {
                messageBacklog.add(msg);
                return;
            } else {
                messageInProgress = true;
            }
        }

        boolean queueHasMessagesToBeSent = true;

        String messageToSend = msg;
        do {
            session.getBasicRemote().sendText(messageToSend);
            synchronized (this) {
                messageToSend = messageBacklog.poll();
                if (messageToSend == null) {
                    messageInProgress = false;
                    queueHasMessagesToBeSent = false;
                }
            }

        } while (queueHasMessagesToBeSent);
     }


    private static void broadcast(String msg) {
        for (ChatAnnotation client : connections) {
            try {
                client.sendMessage(msg);
            } catch (IOException ioe) {
                if (log.isDebugEnabled()) {
                    log.debug("Chat Error: Failed to send message to client", ioe);
                }
                if (connections.remove(client)) {
                    try {
                        client.session.close();
                    } catch (IOException ignore) {
                        // Ignore
                    }
                    String message = String.format("* %s %s", client.nickname, "has been disconnected.");
                    broadcast(message);
                }
            }
        }
    }
}
