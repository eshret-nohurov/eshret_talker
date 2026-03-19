# Релизы eshret_talker

Репозиторий уже настроен на публикацию каждого Android library-модуля через `maven-publish`.

## Модули

- `eshret-talker-core`
- `eshret-talker-ui`
- `eshret-talker-okhttp`

## Координаты артефактов

- `com.eshret.talker:eshret-talker-core:${POM_VERSION}`
- `com.eshret.talker:eshret-talker-ui:${POM_VERSION}`
- `com.eshret.talker:eshret-talker-okhttp:${POM_VERSION}`

## Чеклист релиза

1. Обновите `POM_VERSION` в `gradle.properties`.
2. Обновите `README.md`, если поменялись API, setup или координаты.
3. Добавьте релизные заметки в `CHANGELOG.md`.
4. Проверьте сборку:

```bash
./gradlew test
./gradlew :eshret-talker-core:generatePomFileForReleasePublication
./gradlew :eshret-talker-ui:generatePomFileForReleasePublication
./gradlew :eshret-talker-okhttp:generatePomFileForReleasePublication
```

5. Сделайте коммит с версией и changelog.
6. Создайте git tag, например `v0.1.0`.
7. Отправьте ветку и tag в удаленный репозиторий.
8. Создайте GitHub Release по tag с текстом релизных заметок.

## Локальная публикация

Чтобы опубликовать все модули в локальный Maven-кэш:

```bash
./gradlew publishToMavenLocal
```

## Примечания

- `gradle.properties` содержит общие POM-метаданные (SCM, license, developer).
- `jitpack.yml` фиксирует Java 17 для tagged-сборок на JitPack-подобных окружениях.
- Подпись артефактов и публикация в внешний портал добавляются поверх текущей конфигурации.
