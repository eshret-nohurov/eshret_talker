import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

// Этот файл описывает корневую Gradle-конфигурацию библиотеки eshret_talker.
// Здесь мы задаём общие group/version и публикацию release-артефактов для всех Android-модулей.

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
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
    "eshret-talker-core" to "Ядро логгера с хранением в памяти, уровнями логов, sink-выводом и интеграцией с Logcat.",
    "eshret-talker-ui" to "Jetpack Compose-экран для просмотра, фильтрации и анализа логов eshret_talker внутри приложения.",
    "eshret-talker-okhttp" to "OkHttp-interceptor, который отправляет читаемые трейсы запросов и ответов в eshret_talker.",
)

subprojects {
    plugins.withId("com.android.library") {
        pluginManager.apply("maven-publish")

        extensions.configure<LibraryExtension> {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                }
            }
        }

        extensions.configure<PublishingExtension> {
            publications {
                register<MavenPublication>("release") {
                    groupId = rootProject.group.toString()
                    artifactId = project.name
                    version = rootProject.version.toString()

                    pom {
                        name.set(project.name)
                        description.set(
                            modulePomDescriptions[project.name]
                                ?: rootProject.requiredProperty("POM_DESCRIPTION"),
                        )
                        url.set(rootProject.requiredProperty("POM_URL"))
                        inceptionYear.set(rootProject.requiredProperty("POM_INCEPTION_YEAR"))

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

                    project.afterEvaluate {
                        from(project.components["release"])
                    }
                }
            }
        }
    }
}
