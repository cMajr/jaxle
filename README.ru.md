# Jaxle

[English](README.md)

**Компактный Java-фреймворк без зависимостей со своим HTTP-сервером.**

В Jaxle хэндлер представляет собой функцию, которая принимает `Request`
и возвращает `Response`. Фреймворки, например Javalin, вместо этого
помещают заголовки, статус и тело в изменяемый контекст, где второй вызов,
задающий ответ, затирает первый. Компилятор не допустит, чтобы хэндлер
вернул что-либо кроме объекта `Response`, проверяя все его ветки. Чтобы
протестировать хэндлер, достаточно вызвать его с `Request` и проверить
возвращённый `Response`, не запуская сервер.

## Быстрый старт

```java
import jaxle.Server;

public class Main {
    public static void main(String[] args) {
        // Укажите любой порт или вызовите new Server(), чтобы слушать 8080
        Server server = new Server(8081);
        Users users = new Users();
        server.get("/users/{id}", users::findById);
        server.start();
    }
}
```

```java
import java.util.Map;

import jaxle.Request;
import jaxle.Response;

import static jaxle.Response.notFound;
import static jaxle.Response.ok;

public class Users {
    private final Map<Integer, String> users = Map.of(1, "alice", 2, "bob");

    Response findById(Request request) {
        int id = request.param("id", Integer::parseInt);
        String name = users.get(id);

        if (name == null) {
            return notFound("No user with id " + id);
        }

        return ok(name);
    }
}
```

Проверьте через curl.

```
$ curl localhost:8081/users/1        # 200
alice
$ curl localhost:8081/users/3        # 404
No user with id 3
$ curl localhost:8081/users/abc      # 400
bad path parameter id
```

Пример с регистрацией пользователей лежит в [`examples/example/`](examples/example).

```
javac -d out src/jaxle/*.java examples/example/*.java && java -cp out example.Main
```

## Возможности

- Маршруты для GET, POST, PUT, PATCH и DELETE, а также для любого `Method` через `addRoute`
- Типизированные параметры пути `request.param("id", UUID::fromString)`
- Декодирование процентной кодировки в путях и query-параметрах, `request.query("page")`
- Тело запроса в виде байтов или текста в UTF-8, `body()` и `text()`
- Фабрики для частых ответов, `ok`, `created`, `notFound`, `json` и другие
- `HttpException`, чтобы завершить запрос со статусом ошибки из любого места
- HEAD, OPTIONS и 405 обрабатываются автоматически
- `new Server(0)` для свободного порта, узнать его можно через `port()`
- `close()`, чтобы остановить сервер и выйти из `start()`

## Использование

Jaxle требует Java 21 или новее. До первого релиза соберите jar в клоне
этого репозитория и компилируйте свой код с ним.

```
javac -d out src/jaxle/*.java && jar cf jaxle.jar -C out .
javac -cp jaxle.jar -d app Main.java Users.java
java -cp jaxle.jar:app Main
```

## Область применения

Jaxle работает по HTTP/1.1 и рассчитан на запуск за обратным прокси вроде
nginx, который берёт на себя TLS и HTTP/2.

- Chunked-запросы не поддерживаются.
- Запрос должен уложиться в 30 секунд, строка в 8 КиБ,
  заголовков не больше 100 и не больше 32 КиБ в сумме, тело не больше
  500 КиБ, открытых соединений не больше 500.
- Хэндлеры выполняются параллельно в виртуальных потоках и должны быть
  потокобезопасными.

## Пока нет

- Keep-alive.
- Настраиваемые лимиты.
- Таймаут на запись.
- Middleware.
- Маппинг JSON.

## Лицензия

Apache License 2.0, см. [LICENSE](LICENSE).
