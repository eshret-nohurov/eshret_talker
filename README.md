# eshret_talker

Отдельная Android-библиотека для красивого и удобного логирования в стиле `talker_flutter`.

## Что внутри

- `eshret-talker-core`
  - уровни логов
  - in-memory буфер
  - вывод в Logcat
  - API наподобие `info`, `warning`, `error`, `handle`
- `eshret-talker-ui`
  - Compose-экран журнала
  - Compose bottom sheet
  - цветные карточки логов
  - фильтр, поиск, очистка
- `eshret-talker-okhttp`
  - interceptor для красивых HTTP-логов
  - разделение request/response
  - маскирование чувствительных headers

## Идея дизайна

- `INFO` — синий
- `SUCCESS` — зелёный
- `WARNING` — оранжевый
- `ERROR` — красный
- `CRITICAL` — тёмно-красный
- `HTTP OUT` — бирюзовый
- `HTTP IN` — зелёно-бирюзовый

Каждый лог показывает:

- смайлик уровня
- цветной акцент
- время
- тег
- основное сообщение
- детали и stack trace по раскрытию

## Использование

```kotlin
val talker = EshretTalker()

talker.info(
    message = "Открыли экран Home",
    tag = "HOME",
)

talker.error(
    message = "Ошибка загрузки подборок",
    tag = "HOME",
    throwable = exception,
)
```

Для HTTP:

```kotlin
OkHttpClient.Builder()
    .addInterceptor(EshretTalkerOkHttpInterceptor(talker))
    .build()
```

Для UI:

```kotlin
EshretTalkerBottomSheet(
    talker = talker,
    onDismiss = { /* close */ },
)
```

