# Contributing to eshret_talker

Thanks for your interest in `eshret_talker`. The repository is a reusable Android library, so
changes should keep the public API predictable and the module boundaries clean.

## Principles

- keep the responsibilities of `core`, `ui`, and `okhttp` clearly separated
- prefer small, readable APIs over heavy abstractions
- keep logs readable both in Logcat and in the Compose UI
- never leak sensitive data into HTTP logs
- record behavioral changes in `README.md` and `CHANGELOG.md`

## Getting started

1. Fork the repository and create a branch from `main`.
2. Make changes in small, logical steps.
3. Run the local checks before opening a pull request.

## Local checks

Use the Gradle wrapper from the repository root:

```bash
./gradlew assemble
./gradlew testDebugUnitTest
```

If a change affects the UI or text output, add a short before/after description to the pull request.

## Pull request checklist

- the change solves a specific problem
- public API changes are intentional and documented
- documentation is updated when behavior changes
- no sensitive HTTP data leaks into logs
- the build and tests pass locally

## Code style

- keep Kotlin code simple and readable
- comments should explain motivation, not the obvious
- examples should be realistic and close to real Android usage
- prefer focused changes over broad refactors without a clear reason

## Filing issues

When opening an issue, include:

- what you expected
- what actually happened
- steps to reproduce
- the Android version and device/emulator (if relevant)
- a minimal code sample, if available

## Questions and proposals

Suggestions about the API, module architecture, naming, and documentation are welcome. For
larger changes, please open an issue first to align on direction.
