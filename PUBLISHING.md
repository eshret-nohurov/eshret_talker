# Publishing

This document describes how `eshret_talker` is distributed and how to publish it to
Maven Central for the widest reach.

## Distribution channels

| Channel | Status | Effort |
| --- | --- | --- |
| **JitPack** | Active | Zero setup — builds from a git tag |
| **Maven Central** | Optional next step | One-time account and signing setup |
| **GitHub Packages** | Optional | Consumers need GitHub auth, so less convenient |

## JitPack (already working)

JitPack builds straight from a pushed git tag. No account or signing is needed.

1. Push a release tag, e.g. `git tag 0.1.0 && git push origin 0.1.0` (or create a GitHub Release).
2. Consumers add the JitPack repository and depend on
   `com.github.eshret-nohurov.eshret_talker:<module>:<tag>` (see the README).

`jitpack.yml` pins the build to JDK 17.

## Maven Central (recommended for maximum reach)

Maven Central is the registry Gradle and Maven check by default, so it gives the best
discoverability and trust. Publishing there is a one-time setup.

### 1. Claim a namespace

The simplest verifiable namespace for a GitHub project is `io.github.<username>`:

```
io.github.eshret-nohurov
```

Register it at <https://central.sonatype.com> (Central Portal). GitHub-based namespaces are
verified automatically by creating the verification repository the portal asks for.

### 2. Create a GPG signing key

Maven Central requires signed artifacts:

```bash
gpg --gen-key
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
gpg --export-secret-keys --armor <KEY_ID> > signing-key.asc
```

### 3. Add the publishing plugin

The recommended way is the [`com.vanniktech.maven.publish`](https://github.com/vanniktech/gradle-maven-publish-plugin)
plugin, which handles signing and uploading to the Central Portal. Add it to each library
module and set the coordinates:

```kotlin
plugins {
    id("com.vanniktech.maven.publish") version "0.30.0"
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    coordinates("io.github.eshret-nohurov", project.name, project.version.toString())
}
```

Update `POM_GROUP_ID` in `gradle.properties` to `io.github.eshret-nohurov`.

### 4. Provide credentials

Put your Central Portal token and signing key in `~/.gradle/gradle.properties` (never commit them):

```
mavenCentralUsername=<central-portal-token-user>
mavenCentralPassword=<central-portal-token-password>
signingInMemoryKey=<contents of signing-key.asc>
signingInMemoryKeyPassword=<key passphrase>
```

### 5. Publish

```bash
./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
```

After the release is processed, consumers can depend on
`io.github.eshret-nohurov:eshret-talker-core:<version>` with just `mavenCentral()` in their
repositories.

## Release checklist

1. Update `POM_VERSION` in `gradle.properties`.
2. Add release notes to `CHANGELOG.md`.
3. Run `./gradlew testDebugUnitTest assemble`.
4. Create and push a git tag and a GitHub Release.
5. Publish to Maven Central (if configured).
