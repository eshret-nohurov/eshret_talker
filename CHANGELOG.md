# Changelog

All notable changes to this project are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-06-24

First public release.

### Added

- In-memory logger core with log levels, a bounded buffer, configuration, sink output, and a
  Logcat sink. Logging is crash-safe: a failure inside logging never takes down the caller.
- Jetpack Compose log viewer with search, per-level filters, detail and stack-trace expansion,
  a JSON body viewer, list ordering, clipboard copy, and `.txt` export.
- OkHttp interceptor with configurable request/response/error logging, body previews with size
  limits, and `Content-Type` charset handling.
- Sessions: on-disk persistence of log history per app launch (`EshretTalkerSessionStore` /
  `FileEshretTalkerSessionStore`). A session stores every entry on disk regardless of the
  in-memory `maxEntries` limit, is written in the background, is named by its start time, is
  grouped by day, and is kept for 7 days.
- Sessions screen in the Compose UI: a "Sessions" button, a per-day session list, per-session
  log viewing with search and filters, copy and share of all session logs, and deletion of a
  single session or all sessions.
- Unit tests for the logger core, the OkHttp interceptor, and the file-based session store.
- `maven-publish` configuration for every Android library module.

### Security

- Sensitive headers (`authorization`, `cookie`, `set-cookie`, `x-api-key`, `x-access-token`)
  are masked by default, including in user-provided settings and in stored sessions.
