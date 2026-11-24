# Лабораторная работа № 4 · REST, WebSocket и Kafka

На macOS (Apple Silicon или Intel) и Windows команды одинаковые. Установите и запустите [Docker Desktop](https://docs.docker.com/get-started/get-docker/). На Windows используйте Linux-контейнеры. Откройте терминал в папке проекта после клонирования репозитория или распаковки **Code → Download ZIP**.

```sh
docker compose up --build -d
docker compose ps
```

Первый запуск загрузит образы Maven 3.9.11 с JDK 21, Tomcat 9.0.111 и Kafka 4.1.2, а также зависимости Jersey 2.35. Docker соберёт оба веб-компонента из исходников. Java, Maven, Kafka и NetBeans отдельно устанавливать не нужно.

| Проверка | Адрес |
|---|---|
| Время в JSON | [Сервис времени](http://localhost:8079/TimeService/api/time) |
| Вычисление `2+4*7 = 30` | [Калькулятор](http://localhost:8079/TimeService/api/calculate?expression=2%2B4%2A7) |
| Обмен сообщениями | [WebSocket-чат](http://localhost:8079/examples/websocket/chat.xhtml) |

Откройте чат в двух вкладках. Отправьте текст, `time`, `dol`, `eu` или `2+4*7`. Ответ появится в обеих вкладках. Если порт 8079 занят, создайте файл `.env` рядом с `compose.yaml` со строкой `WEB_PORT=18079`, повторите запуск и откройте страницы на порту 18079.

## Kafka

После запуска трёх брокеров создайте топик с тремя партициями и тремя репликами:

```sh
docker compose exec broker3 /opt/kafka/bin/kafka-topics.sh --bootstrap-server broker1:9092,broker2:9092,broker3:9092 --create --if-not-exists --topic lab4 --partitions 3 --replication-factor 3
docker compose exec broker3 /opt/kafka/bin/kafka-topics.sh --bootstrap-server broker1:9092,broker2:9092,broker3:9092 --describe --topic lab4
```

В трёх терминалах запустите по одному читателю из одной группы. В четвёртом запустите продюсер и введите несколько строк.

```sh
docker compose exec broker3 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server broker1:9092,broker2:9092,broker3:9092 --topic lab4 --group lab4-group --from-beginning --property print.partition=true
```

```sh
docker compose exec broker3 /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server broker1:9092,broker2:9092,broker3:9092 --topic lab4 --producer-property acks=all --producer-property partitioner.class=org.apache.kafka.clients.producer.RoundRobinPartitioner
```

Для демонстрации отказа остановите `broker1` и повторите команду `--describe`. Дождитесь, пока в каждой партиции останутся две синхронные реплики в колонке `Isr`. Отправьте новую серию сообщений, затем верните брокер и проверьте, что во всех партициях снова по три синхронные реплики.

```sh
docker compose stop broker1
docker compose start broker1
```

Чтобы остановить лабораторную и сохранить данные Kafka, выполните `docker compose down`. Для запуска только REST и чата используйте `docker compose up --build -d web`.
