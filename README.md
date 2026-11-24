# Лабораторная работа № 4 · REST, WebSocket и Kafka

На macOS и Windows используются одинаковые команды. Установите [JDK 21](https://adoptium.net/temurin/releases/?version=21), проверьте командой `java -version`, что терминал видит эту версию. Скачайте проект через **Code → Download ZIP** и распакуйте его или клонируйте репозиторий:

```sh
git clone https://github.com/Deloperr/lab4-web-services-kafka.git
cd lab4-web-services-kafka
```

## Запуск веб-части

Из папки проекта выполните:

```sh
java Lab4.java
```

При первом запуске программа скачает Maven 3.9.11 и Tomcat 9.0.111, сверит SHA-512 архивов и сохранит их в `.lab4`. Maven скачает зависимости Jersey 2.35. Затем программа соберёт оба веб-компонента и запустит Tomcat. Для распаковки используется системная команда `tar`, доступная на macOS и в Windows 10/11. Проект TimeService можно также открыть в NetBeans как Maven-проект.

| Проверка | Адрес |
|---|---|
| Время в JSON | [Сервис времени](http://127.0.0.1:8079/TimeService/api/time) |
| Вычисление `2+4*7 = 30` | [Калькулятор](http://127.0.0.1:8079/TimeService/api/calculate?expression=2%2B4%2A7) |
| Обмен сообщениями | [WebSocket-чат](http://127.0.0.1:8079/examples/websocket/chat.xhtml) |

Откройте чат в двух вкладках. Отправьте текст, `time`, `dol`, `eu` или `2+4*7`. Ответ появится в обеих вкладках. Если порт занят, запустите `java Lab4.java web 18079` и используйте порт 18079. Для остановки нажмите Ctrl+C.

## Дополнительное задание Kafka

В трёх отдельных терминалах из папки проекта запустите по одному брокеру:

```sh
java Lab4.java broker 1
java Lab4.java broker 2
java Lab4.java broker 3
```

При первом запуске Maven скачает Kafka 4.1.2 и её библиотеки из Maven Central с проверкой контрольных сумм. Настройки и данные узлов хранятся в `.lab4`. Брокеры слушают локальные порты 9092, 9192 и 9292.

Когда брокеры запустятся, создайте топик и проверьте распределение партиций:

```sh
java Lab4.java kafka topics --bootstrap-server "127.0.0.1:9092,127.0.0.1:9192,127.0.0.1:9292" --create --if-not-exists --topic lab4 --partitions 3 --replication-factor 3
java Lab4.java kafka topics --bootstrap-server "127.0.0.1:9092,127.0.0.1:9192,127.0.0.1:9292" --describe --topic lab4
```

В трёх дополнительных терминалах запустите по одному читателю из группы `lab4-group`:

```sh
java Lab4.java kafka consumer --bootstrap-server "127.0.0.1:9092,127.0.0.1:9192,127.0.0.1:9292" --topic lab4 --group lab4-group --from-beginning --property print.partition=true
```

В ещё одном терминале запустите продюсер. Вводите по одному сообщению на строку:

```sh
java Lab4.java kafka producer --bootstrap-server "127.0.0.1:9092,127.0.0.1:9192,127.0.0.1:9292" --topic lab4 --producer-property acks=all --producer-property partitioner.class=org.apache.kafka.clients.producer.RoundRobinPartitioner
```

Для проверки отказа нажмите Ctrl+C в терминале одного из брокеров, который указан лидером партиции. Повторите команду `--describe` и дождитесь двух номеров в колонке `Isr` каждой партиции. Отправьте новую серию сообщений и проверьте, что читатели их получили. Запустите брокер с тем же номером и дождитесь возвращения третьей реплики.
