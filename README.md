# eshret_talker

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![JitPack](https://jitpack.io/v/eshret-nohurov/eshret_talker.svg)](https://jitpack.io/#eshret-nohurov/eshret_talker)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](./LICENSE)

`eshret_talker` is a lightweight Android logging toolkit built from three small modules:
an in-memory logger core, a Jetpack Compose log viewer, and an OkHttp interceptor for
readable HTTP traces. It is designed to be a drop-in in-app debug console for development
and QA.

## Table of contents

- [Why eshret_talker](#why-eshret_talker)
- [Modules](#modules)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Compose UI](#compose-ui)
- [Sessions](#sessions)
- [HTTP logging](#http-logging)
- [Configuration](#configuration)
- [Log levels](#log-levels)
- [Building from source](#building-from-source)
- [Publishing](#publishing)
- [Contributing](#contributing)
- [License](#license)

## Why eshret_talker

- readable logs for app events, errors, and network traffic
- in-memory history exposed as a `StateFlow` for reactive UI
- polished Compose viewer with search, filters, and expandable details
- Logcat output enabled out of the box
- safe HTTP logging with masking of sensitive headers by default
- on-disk sessions: the full history of each app launch, grouped by day and kept for a week
- a simple Kotlin API with no heavy setup
- crash-safe by design: a failure inside logging never takes down the caller

## Modules

| Module | Purpose |
| --- | --- |
| `eshret-talker-core` | Logger core: log levels, in-memory buffer, configuration, sink output, Logcat integration, and optional on-disk sessions |
| `eshret-talker-ui` | Compose screen and bottom sheet for browsing logs and sessions inside the app |
| `eshret-talker-okhttp` | OkHttp interceptor that turns requests and responses into readable traces |

You can depend on only the modules you need. `ui` and `okhttp` depend on `core`.

## Requirements

- Android `minSdk 24`
- `compileSdk 35`
- Java 17
- Kotlin 2.2.20

## Installation

Releases are published via [JitPack](https://jitpack.io/#eshret-nohurov/eshret_talker).

Add the JitPack repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then add the modules you need (replace `0.1.0` with the latest tag):

```kotlin
dependencies {
    implementation("com.github.eshret-nohurov.eshret_talker:eshret-talker-core:0.1.0")
    implementation("com.github.eshret-nohurov.eshret_talker:eshret-talker-ui:0.1.0")
    implementation("com.github.eshret-nohurov.eshret_talker:eshret-talker-okhttp:0.1.0")
}
```

> The `com.github.eshret-nohurov.eshret_talker` group id is JitPack's coordinate for a
> multi-module project. The version is any pushed git tag (for example `0.1.0`) or commit hash.

## Quick start

Create a logger:

```kotlin
val talker = EshretTalker()
```

Write logs:

```kotlin
talker.info(
    message = "Opened Home screen",
    tag = "HOME",
)

talker.success(
    message = "Feed loaded",
    tag = "HOME",
)

talker.error(
    message = "Failed to load recommendations",
    tag = "HOME",
    throwable = exception,
)
```

Handle exceptions in one call:

```kotlin
runCatching {
    repository.refresh()
}.onFailure { throwable ->
    talker.handle(
        throwable = throwable,
        message = "Refresh failed",
        tag = "HOME",
    )
}
```

## Compose UI

Use the full-screen viewer as an in-app debug console for development and QA:

```kotlin
EshretTalkerScreen(
    talker = talker,
)
```

Or present it as a full-screen bottom sheet:

```kotlin
EshretTalkerBottomSheet(
    talker = talker,
    onDismiss = { /* close */ },
)
```

The viewer includes:

- an app bar with an actions menu
- list ordering toggle (newest or oldest first) with scroll-to-top
- history clearing from the actions sheet
- copy the full log list to the clipboard
- system share of all logs as a `.txt` file
- color-coded cards per log level
- search across message, tag, and details
- compact horizontal visibility filters per log level
- expandable details and stack traces
- a full-screen `body` viewer with a JSON tree (expand and collapse nested objects and arrays)

## Sessions

The logger can persist history as sessions on disk. A session is created when `EshretTalker`
is initialized (that is, on every app launch) and keeps **all** of its entries — even tens of
thousands (the in-memory `maxEntries` limit does not apply to storage).

Enable it by passing a session store:

```kotlin
val talker = EshretTalker(
    sessionStore = FileEshretTalkerSessionStore(context.filesDir),
)
```

Without a `sessionStore` the logger behaves exactly as before, and the "Sessions" button does
not appear in the UI.

Behavior:

- sessions are named by their start time and grouped by day
- sessions are kept for 7 days; anything older is removed on the next launch
- writes happen in the background and never block or crash the caller
- secrets in HTTP logs stay masked in stored sessions as well

In the Compose UI a "Sessions" button appears next to the actions menu:

- a list of sessions grouped by day, showing the session time, how long ago it ended, and the
  number of entries
- tapping a session opens its logs in the same viewer (search, filters, details)
- you can copy all of a session's logs or share them as a `.txt` file
- delete a single session or all sessions at once

The retention period is configurable:

```kotlin
val sessionStore = FileEshretTalkerSessionStore(
    rootDirectory = context.filesDir,
    retentionDays = 7,
)
```

## HTTP logging

Attach the interceptor to your `OkHttpClient`:

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(EshretTalkerOkHttpInterceptor(talker))
    .build()
```

The interceptor logs:

- request method and URL
- response code and duration
- headers with sensitive values masked
- the full text body for easy copying and diagnostics
- network failures with a stack trace

Sensitive headers (`authorization`, `cookie`, `set-cookie`, `x-api-key`, `x-access-token`)
are masked by default, even when you provide your own settings.

## Configuration

`eshret-talker-core` is configured through `EshretTalkerConfig`:

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

You can forward logs to additional sinks:

```kotlin
val analyticsSink = EshretTalkerSink { entry ->
    println("Forwarded log: ${entry.level} ${entry.message}")
}

val talker = EshretTalker(
    extraSinks = listOf(analyticsSink),
)
```

Logging can be disabled globally with `enabled = false` in `EshretTalkerConfig`.

Fine-tune HTTP logging:

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

`logLevel` acts as a verbosity switch: `VERBOSE`/`DEBUG` log all traffic, while `INFO` and
above suppress normal traffic and keep only errors (`4xx`/`5xx` and network failures).

## Log levels

`VERBOSE`, `DEBUG`, `INFO`, `NAVIGATION`, `SUCCESS`, `WARNING`, `ERROR`, `CRITICAL`,
`HTTP OUT`, `HTTP IN`

## Building from source

```bash
./gradlew assemble
./gradlew testDebugUnitTest
```

## Publishing

Each Android library module is configured for `maven-publish` and publishes a `release`
artifact with sources and POM metadata.

Publish to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

See [RELEASING.md](./RELEASING.md) for the release process.

## Contributing

Contribution guidelines are described in [CONTRIBUTING.md](./CONTRIBUTING.md).

## License

Distributed under the Apache License 2.0. See [LICENSE](./LICENSE) for details.
