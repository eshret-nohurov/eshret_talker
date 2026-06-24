import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.Project

// Этот файл описывает корневую Gradle-конфигурацию библиотеки eshret_talker.
// Здесь мы задаём общие group/version и публикацию модулей в Maven Central и mavenLocal
// (через плагин com.vanniktech.maven.publish), а также POM-метаданные для каждого артефакта.

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.maven.publish) apply false
}

fun Project.requiredProperty(name: String): String =
    providers.gradleProperty(name).orNull ?: error("Отсутствует Gradle-свойство: $name")

group = requiredProperty("POM_GROUP_ID")
version = requiredProperty("POM_VERSION")

allprojects {
    group = rootProject.group
    version = rootProject.version
}

val modulePomDescriptions = mapOf(
    "eshret-talker-core" to "Logger core with an in-memory buffer, log levels, sink output, on-disk sessions, and Logcat integration.",
    "eshret-talker-ui" to "Jetpack Compose screen for browsing, filtering, and analyzing eshret_talker logs inside the app.",
    "eshret-talker-okhttp" to "OkHttp interceptor that sends readable request and response traces to eshret_talker.",
)

subprojects {
    plugins.withId("com.android.library") {
        pluginManager.apply("com.vanniktech.maven.publish")

        // Подпись включаем ТОЛЬКО когда задан ключ (то есть при релизе в Maven Central).
        // Иначе publishToMavenLocal (им пользуется JitPack) падал бы из-за отсутствия signatory.
        val signingConfigured = providers.gradleProperty("signingInMemoryKey").isPresent ||
            providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent ||
            providers.gradleProperty("signing.keyId").isPresent

        extensions.configure<MavenPublishBaseExtension> {
            // Публикация именно в новый Central Portal (central.sonatype.com), а не в старый OSSRH.
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
            // Подпись артефактов GPG нужна для Maven Central; локально она не требуется.
            if (signingConfigured) {
                signAllPublications()
            }

            coordinates(
                groupId = rootProject.group.toString(),
                artifactId = project.name,
                version = rootProject.version.toString(),
            )

            pom {
                name.set(project.name)
                description.set(
                    modulePomDescriptions[project.name]
                        ?: rootProject.requiredProperty("POM_DESCRIPTION"),
                )
                inceptionYear.set(rootProject.requiredProperty("POM_INCEPTION_YEAR"))
                url.set(rootProject.requiredProperty("POM_URL"))

                licenses {
                    license {
                        name.set(rootProject.requiredProperty("POM_LICENSE_NAME"))
                        url.set(rootProject.requiredProperty("POM_LICENSE_URL"))
                        distribution.set(rootProject.requiredProperty("POM_LICENSE_DIST"))
                    }
                }

                developers {
                    developer {
                        id.set(rootProject.requiredProperty("POM_DEVELOPER_ID"))
                        name.set(rootProject.requiredProperty("POM_DEVELOPER_NAME"))
                        url.set(rootProject.requiredProperty("POM_DEVELOPER_URL"))
                    }
                }

                scm {
                    url.set(rootProject.requiredProperty("POM_SCM_URL"))
                    connection.set(rootProject.requiredProperty("POM_SCM_CONNECTION"))
                    developerConnection.set(rootProject.requiredProperty("POM_SCM_DEV_CONNECTION"))
                }
            }
        }
    }
}
