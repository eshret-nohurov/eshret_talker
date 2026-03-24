# eshret_talker

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](./LICENSE)

`eshret_talker` — это Android-набор для логирования с тремя модулями:
in-memory ядро логгера, Compose-экран просмотра логов и OkHttp-interceptor для понятных HTTP-трейсов.

Репозиторий оформлен как отдельный библиотечный проект, поэтому его удобно развивать, публиковать во внутренний registry и доводить до публичного пакета.

## Содержание

- [Почему eshret_talker](#почему-eshret_talker)
- [Модули](#модули)
- [Требования](#требования)
- [Подключение в проект](#подключение-в-проект)
- [Артефакты](#артефакты)
- [Быстрый старт](#быстрый-старт)
- [Compose UI](#compose-ui)
- [HTTP-логирование](#http-логирование)
- [Конфигурация](#конфигурация)
- [Уровни логов](#уровни-логов)
- [Локальная разработка](#локальная-разработка)
- [Публикация](#публикация)
- [Релизный процесс](#релизный-процесс)
- [Защита репозитория](#защита-репозитория)
- [План развития](#план-развития)
- [Вклад в проект](#вклад-в-проект)
- [Лицензия](#лицензия)

## Почему eshret_talker

- читаемые логи для событий приложения, ошибок и сети
- in-memory история с `StateFlow` для реактивного UI
- удобный Compose-экран с поиском, фильтрами и разворотом деталей
- вывод в Logcat включен из коробки
- безопасное HTTP-логирование с маскированием чувствительных headers
- простой Kotlin API без тяжелой настройки

## Модули

| Модуль | Назначение |
| --- | --- |
| `eshret-talker-core` | Базовый логгер, уровни логов, буфер, конфиг, sink-вывод, интеграция с Logcat |
| `eshret-talker-ui` | Compose-экран и bottom sheet для просмотра логов внутри приложения |
| `eshret-talker-okhttp` | OkHttp-interceptor для логирования запросов и ответов |

## Требования

- Android `minSdk 26`
- `compileSdk 35`
- Java 17
- Kotlin 2.2.20

## Подключение в проект

Сейчас репозиторий устроен как multi-module Android library проект.
Если используете его локально в той же сборке, подключайте только нужные модули:

```kotlin
dependencies {
    implementation(project(":eshret-talker-core"))
    implementation(project(":eshret-talker-ui"))
    implementation(project(":eshret-talker-okhttp"))
}
```

## Артефакты

При публикации модули используют такие координаты:

| Модуль | Maven-координата |
| --- | --- |
| Core | `com.eshret.talker:eshret-talker-core:0.1.0-local` |
| UI | `com.eshret.talker:eshret-talker-ui:0.1.0-local` |
| OkHttp | `com.eshret.talker:eshret-talker-okhttp:0.1.0-local` |

Пример подключения зависимостей из Maven-репозитория или `mavenLocal()`:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
    google()
}

dependencies {
    implementation("com.eshret.talker:eshret-talker-core:0.1.0-local")
    implementation("com.eshret.talker:eshret-talker-ui:0.1.0-local")
    implementation("com.eshret.talker:eshret-talker-okhttp:0.1.0-local")
}
```

## Быстрый старт

Создаем логгер:

```kotlin
val talker = EshretTalker()
```

Пишем логи:

```kotlin
talker.info(
    message = "Открыли экран Home",
    tag = "HOME",
)

talker.success(
    message = "Подборка загружена",
    tag = "HOME",
)

talker.error(
    message = "Не удалось загрузить рекомендации",
    tag = "HOME",
    throwable = exception,
)
```

Обрабатываем исключения одним вызовом:

```kotlin
runCatching {
    repository.refresh()
}.onFailure { throwable ->
    talker.handle(
        throwable = throwable,
        message = "Ошибка обновления",
        tag = "HOME",
    )
}
```

## Compose UI

Используйте полноэкранный просмотрщик, если нужен внутренний debug-console для QA и разработки:

```kotlin
EshretTalkerScreen(
    talker = talker,
)
```

Или покажите его как полноэкранный bottom sheet:

```kotlin
EshretTalkerBottomSheet(
    talker = talker,
    onDismiss = { /* close */ },
)
```

В UI уже есть:

- полноэкранное открытие bottom sheet без промежуточного состояния
- app bar с меню действий
- смена порядка списка через `SwapVert` с прокруткой к началу
- очистка истории из action sheet
- копирование полного списка логов
- системный share `.txt` файла со всеми логами
- цветные карточки по уровню лога
- поиск по сообщению, тегу и деталям
- компактные горизонтальные фильтры видимости по типам логов
- разворот деталей и stack trace
- полноэкранный просмотр `body` с удобным JSON viewer (раскрытие/сворачивание вложенных объектов и массивов)

## HTTP-логирование

Подключите interceptor к `OkHttpClient`:

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(EshretTalkerOkHttpInterceptor(talker))
    .build()
```

Interceptor логирует:

- метод и URL запроса
- код ответа и длительность
- headers с маскированием чувствительных значений
- полный текстовый body для удобного копирования и диагностики
- сетевые ошибки со stack trace

## Конфигурация

`eshret-talker-core` настраивается через `EshretTalkerConfig`:

```kotlin
val talker = EshretTalker(
    config = EshretTalkerConfig(
        enabled = true,
        maxEntries = 600,
        logcatEnabled = true,
        logcatTag = "eshret_talker",
    ),
)
```

Можно отправлять логи в дополнительные sink-обработчики:

```kotlin
val analyticsSink = EshretTalkerSink { entry ->
    println("Forwarded log: ${entry.level} ${entry.message}")
}

val talker = EshretTalker(
    extraSinks = listOf(analyticsSink),
)
```

Глобально отключить логирование можно через `enabled = false` в `EshretTalkerConfig`.

Тонкая настройка HTTP-логирования:

```kotlin
val httpLoggerSettings = EshretTalkerOkHttpLoggerSettings(
    enabled = true,
    logLevel = EshretTalkerLevel.DEBUG,
    printRequestHeaders = true,
    printResponseHeaders = true,
    printResponseTime = true,
    hiddenHeaders = setOf("authorization", "cookie"),
)

val client = OkHttpClient.Builder()
    .addInterceptor(
        EshretTalkerOkHttpInterceptor(
            talker = talker,
            settings = httpLoggerSettings,
        ),
    )
    .build()
```

## Уровни логов

`VERBOSE`, `DEBUG`, `INFO`, `NAVIGATION`, `SUCCESS`, `WARNING`, `ERROR`, `CRITICAL`, `HTTP OUT`, `HTTP IN`

## Локальная разработка

Основные команды для репозитория:

```bash
./gradlew assemble
./gradlew test
```

## Публикация

Проект уже настроен на `maven-publish` для каждого Android library-модуля.
Каждый модуль публикует `release`-артефакт с sources и POM-метаданными.

Локальная публикация:

```bash
./gradlew publishToMavenLocal
```

Проверка генерации POM без публикации артефактов в домашний каталог:

```bash
./gradlew :eshret-talker-core:generatePomFileForReleasePublication
./gradlew :eshret-talker-ui:generatePomFileForReleasePublication
./gradlew :eshret-talker-okhttp:generatePomFileForReleasePublication
```

## Релизный процесс

Базовый сценарий релиза:

1. Обновить `POM_VERSION` в `gradle.properties`.
2. Добавить релизные заметки в `CHANGELOG.md`.
3. Запустить `./gradlew test`.
4. Создать и отправить git tag.
5. Оформить GitHub Release по этому tag.

Подробно это описано в [RELEASING.md](./RELEASING.md).

## Защита репозитория

Чтобы никто, кроме вас, не вносил прямые изменения в `main`, в репозитории уже добавлен `CODEOWNERS`.
Дальше нужно включить branch protection в GitHub (пошагово описано в [REPOSITORY_PROTECTION.md](./REPOSITORY_PROTECTION.md)).

Рекомендуемая модель для библиотек:

- прямые push в `main` запрещены
- изменения только через Pull Request
- обязательный review от code owner
- обязательный статус `build/test`
- запрещены force-push и удаление ветки `main`

## План развития

Библиотека уже закрывает основной сценарий: логи приложения, обработка ошибок, in-app просмотр и HTTP-трейсинг.

Следующие улучшения:

- скриншоты или GIF для Compose UI
- расширенные тесты фильтров и форматирования interceptor
- релизные заметки для каждой версии

## Вклад в проект

Правила и ожидания по PR описаны в [CONTRIBUTING.md](./CONTRIBUTING.md).

## Лицензия

Проект распространяется по лицензии Apache License 2.0.
Текст лицензии: [LICENSE](./LICENSE).
