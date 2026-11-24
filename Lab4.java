import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

class Lab4 {
    private static final Path WORK = Path.of(".lab4").toAbsolutePath();
    private static final Path JAVA = Path.of(System.getProperty("java.home"), "bin",
            File.separatorChar == '\\' ? "java.exe" : "java");
    private static final String CENTRAL = "https://repo.maven.apache.org/maven2/";
    private static final String CLUSTER_ID = "cdG4bV24RLqBe43MdpZnmg";

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || (args.length == 2 && args[0].equals("web"))) {
            web(args.length == 0 ? 8079 : Integer.parseInt(args[1]));
        } else if (args.length == 2 && args[0].equals("broker")) {
            broker(Integer.parseInt(args[1]));
        } else if (args.length >= 2 && args[0].equals("kafka")) {
            kafkaTool(args[1], Arrays.copyOfRange(args, 2, args.length));
        } else {
            throw new IllegalArgumentException("java Lab4.java [web PORT | broker 1..3 | kafka TOOL ARGS...]");
        }
    }

    private static void web(int port) throws Exception {
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Port must be in 1024..65535");
        Path tomcat = install(CENTRAL + "org/apache/tomcat/tomcat/9.0.111/tomcat-9.0.111.zip",
                "apache-tomcat-9.0.111");
        maven("TimeService", "package");

        Path server = WORK.resolve("web");
        for (String dir : List.of("conf", "logs", "temp", "work", "webapps")) {
            Files.createDirectories(server.resolve(dir));
        }
        try (var files = Files.list(tomcat.resolve("conf"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Files.copy(file, server.resolve("conf").resolve(file.getFileName()),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path config = server.resolve("conf/server.xml");
        Files.writeString(config, Files.readString(config)
                .replace("<Server port=\"8005\"", "<Server port=\"-1\"")
                .replace("<Connector port=\"8080\"", "<Connector address=\"127.0.0.1\" port=\"" + port + "\""));
        Files.copy(Path.of("TimeService/target/TimeService.war"), server.resolve("webapps/TimeService.war"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Path chat = server.resolve("webapps/examples");
        Path classes = chat.resolve("WEB-INF/classes");
        Files.createDirectories(classes);
        Files.createDirectories(chat.resolve("websocket"));
        Files.copy(Path.of("ChatWebSocket/web/websocket/chat.xhtml"), chat.resolve("websocket/chat.xhtml"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String juli = tomcat.resolve("bin/tomcat-juli.jar").toString();
        int compiled = javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-encoding", "UTF-8", "-cp",
                tomcat.resolve("lib/websocket-api.jar") + File.pathSeparator + juli,
                "-d", classes.toString(), "ChatWebSocket/src/java/websocket/chat/ChatAnnotation.java");
        if (compiled != 0) throw new IllegalStateException("Chat compilation failed");
        System.out.println("Chat: http://127.0.0.1:" + port + "/examples/websocket/chat.xhtml");
        run(JAVA.toString(), "-Dcatalina.home=" + tomcat, "-Dcatalina.base=" + server,
                "-Djava.io.tmpdir=" + server.resolve("temp"), "-Dlab4.http.port=" + port,
                "-cp", tomcat.resolve("bin/bootstrap.jar") + File.pathSeparator + juli,
                "org.apache.catalina.startup.Bootstrap", "start");
    }

    private static void maven(String project, String... goals) throws Exception {
        Path maven = install(CENTRAL + "org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip",
                "apache-maven-3.9.11");
        List<String> command = new ArrayList<>(List.of(
                JAVA.toString(), "-Dmaven.home=" + maven, "-Dclassworlds.conf=" + maven.resolve("bin/m2.conf"),
                "-Dmaven.multiModuleProjectDirectory=" + Path.of(project).toAbsolutePath(), "-cp",
                maven.resolve("boot/*").toString(), "org.codehaus.plexus.classworlds.launcher.Launcher",
                "-f", project + "/pom.xml", "--batch-mode", "--no-transfer-progress"));
        command.addAll(List.of(goals));
        run(command.toArray(String[]::new));
    }

    private static Path kafkaHome() throws Exception {
        Files.createDirectories(WORK);
        Path kafka = WORK.resolve("kafka-4.1.2");
        try (var channel = FileChannel.open(WORK.resolve("kafka-4.1.2.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.lock()) {
            Path ready = kafka.resolve(".lab4-installed");
            if (!Files.exists(ready)) {
                maven("Kafka", "--strict-checksums",
                        "org.apache.maven.plugins:maven-dependency-plugin:3.9.0:copy-dependencies",
                        "-DincludeScope=runtime", "-DoutputDirectory=" + kafka.resolve("libs"));
                Files.createFile(ready);
            }
        }
        return kafka;
    }

    private static void broker(int node) throws Exception {
        if (node < 1 || node > 3) throw new IllegalArgumentException("Broker number must be 1..3");
        Path kafka = kafkaHome();
        Path data = WORK.resolve("node-" + node);
        int port = 9092 + (node - 1) * 100;
        Path config = WORK.resolve("broker-" + node + ".properties");
        java.util.Properties settings = new java.util.Properties();
        settings.putAll(Map.ofEntries(
                Map.entry("process.roles", "broker,controller"), Map.entry("node.id", Integer.toString(node)),
                Map.entry("controller.quorum.voters", "1@127.0.0.1:9093,2@127.0.0.1:9193,3@127.0.0.1:9293"),
                Map.entry("listeners", "PLAINTEXT://127.0.0.1:" + port + ",CONTROLLER://127.0.0.1:" + (port + 1)),
                Map.entry("advertised.listeners", "PLAINTEXT://127.0.0.1:" + port),
                Map.entry("controller.listener.names", "CONTROLLER"),
                Map.entry("inter.broker.listener.name", "PLAINTEXT"),
                Map.entry("listener.security.protocol.map", "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT"),
                Map.entry("log.dirs", data.toString()), Map.entry("num.partitions", "3"),
                Map.entry("default.replication.factor", "3"), Map.entry("min.insync.replicas", "2"),
                Map.entry("offsets.topic.replication.factor", "3"), Map.entry("offsets.topic.num.partitions", "3"),
                Map.entry("transaction.state.log.replication.factor", "3"),
                Map.entry("transaction.state.log.min.isr", "2"), Map.entry("auto.create.topics.enable", "false")));
        try (var out = Files.newOutputStream(config)) { settings.store(out, null); }

        if (!Files.exists(data.resolve("meta.properties"))) {
            kafkaJava(kafka, false, "kafka.tools.StorageTool", "format", "-t", CLUSTER_ID, "-c", config.toString());
        }
        kafkaJava(kafka, true, "kafka.Kafka", config.toString());
    }

    private static void kafkaTool(String name, String[] args) throws Exception {
        String mainClass = switch (name) {
            case "topics" -> "org.apache.kafka.tools.TopicCommand";
            case "producer" -> "org.apache.kafka.tools.ConsoleProducer";
            case "consumer" -> "org.apache.kafka.tools.consumer.ConsoleConsumer";
            case "groups" -> "org.apache.kafka.tools.consumer.group.ConsumerGroupCommand";
            default -> throw new IllegalArgumentException("Expected topics, producer, consumer or groups");
        };
        kafkaJava(kafkaHome(), false, mainClass, args);
    }

    private static void kafkaJava(Path kafka, boolean server, String mainClass, String... args) throws Exception {
        Path logging = Path.of("Kafka/log4j2.xml").toAbsolutePath();
        List<String> command = new ArrayList<>(List.of(JAVA.toString(), "-Xmx384m",
                "-Dlog4j2.configurationFile=" + logging.toUri(), "-Dlab4.kafka.log.level=" + (server ? "info" : "warn"),
                "-cp", kafka.resolve("libs/*").toString(), mainClass));
        command.addAll(List.of(args));
        run(command.toArray(String[]::new));
    }

    private static Path install(String url, String folder) throws Exception {
        Files.createDirectories(WORK);
        Path installed = WORK.resolve(folder);
        try (var channel = FileChannel.open(WORK.resolve(folder + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.lock()) {
            Path ready = installed.resolve(".lab4-installed");
            if (Files.exists(ready)) return installed;
            var client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            String checksum = client.send(request(url + ".sha512"), HttpResponse.BodyHandlers.ofString()).body()
                    .replaceFirst("(?s)^[^\\n]*?:", "").replaceAll("\\s+", "");
            if (!checksum.matches("(?i)[0-9a-f]{128}")) throw new IllegalStateException("Invalid checksum for " + url);
            Path archive = WORK.resolve(url.substring(url.lastIndexOf('/') + 1));
            System.out.println("Downloading " + url);
            var response = client.send(request(url), HttpResponse.BodyHandlers.ofFile(archive));
            if (response.statusCode() != 200) throw new IllegalStateException("Download failed: HTTP " + response.statusCode());
            var hash = MessageDigest.getInstance("SHA-512");
            try (var input = new java.security.DigestInputStream(Files.newInputStream(archive), hash)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            if (!HexFormat.of().formatHex(hash.digest()).equalsIgnoreCase(checksum)) {
                throw new IllegalStateException("SHA-512 mismatch for " + archive);
            }
            run("tar", "-xf", archive.toString(), "-C", WORK.toString());
            Files.createFile(ready);
            Files.delete(archive);
            return installed;
        }
    }

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofMinutes(5)).build();
    }

    private static void run(String... command) throws Exception {
        Process child = new ProcessBuilder(command).inheritIO().start();
        Thread stop = new Thread(child::destroy);
        Runtime.getRuntime().addShutdownHook(stop);
        int code = child.waitFor();
        Runtime.getRuntime().removeShutdownHook(stop);
        if (code != 0) throw new IllegalStateException("Command exited with " + code + ": " + command[0]);
    }
}
