# Changelog

All notable changes to this project are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Sessions: on-disk persistence of log history per app launch (`EshretTalkerSessionStore` /
  `FileEshretTalkerSessionStore`). A session is created when `EshretTalker` is initialized,
  stores every entry on disk (regardless of `maxEntries`), is written in the background, is
  named by its start time, and is kept for 7 days.
- Sessions screen in the Compose UI: a "Sessions" button in the viewer, a list grouped by day
  (session time, how long ago it ended, entry count), per-session log viewing with search and
  filters, copy and share of all session logs, and deletion of a single session or all sessions.
- Unit tests for the logger core, the OkHttp interceptor, and the file-based session store:
  safety invariants, buffer limits, success/error branching, header masking, body truncation,
  charset handling, retention, and persistence beyond the in-memory limit.
- A `clock` parameter on `EshretTalker` for deterministic timestamps in tests.

### Changed

- `kotlinx-coroutines-core` is now an `api` dependency of the core module, because `StateFlow`
  appears in the public signature of `EshretTalker.logs`.
- The in-memory log buffer is updated with a single allocation per entry instead of two.
- Network log gating was simplified and documented: dead code was removed and the verbosity
  semantics of `logLevel` were clarified.
- HTTP request and response bodies are decoded using the charset from `Content-Type` instead of
  always assuming UTF-8.

### Fixed

- `EshretTalkerOkHttpLoggerSettings.hiddenHeaders` now masks sensitive headers by default
  (`authorization`, `cookie`, `set-cookie`, `x-api-key`, `x-access-token`). Previously the
  safe default lived only in the interceptor constructor, so any custom settings object
  silently disabled masking and could leak secrets into the log.

## [0.1.0]

### Added

- In-memory logger core with log levels, bounded buffer, configuration, and Logcat sink.
- Jetpack Compose log viewer with search, per-level filters, detail expansion, JSON body
  viewer, copying, and `.txt` export.
- OkHttp interceptor with configurable request/response/error logging and header masking.
- `maven-publish` configuration for every Android library module.
