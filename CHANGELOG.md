# История изменений

В этом файле фиксируются заметные изменения проекта.

## [Unreleased]

### Добавлено

- структурированный и расширенный `README.md`
- руководство по вкладу в проект в `CONTRIBUTING.md`
- лицензия Apache 2.0
- шаблоны Issues и Pull Request для GitHub
- публикационная конфигурация `maven-publish` для модулей
- инструкция по релизному процессу в `RELEASING.md`
- инструкция по защите ветки и прав доступа в `REPOSITORY_PROTECTION.md`
- `CODEOWNERS` для модели с обязательным review владельца
- флаг `enabled` в `EshretTalkerConfig` для глобального включения/отключения логгера
- `EshretTalkerOkHttpLoggerSettings` для тонкой настройки HTTP-логов, фильтров и маскирования headers
- полноэкранный `EshretTalkerBottomSheet`
- action sheet с очисткой истории, копированием логов и системным шарингом `.txt` файла
- уровень логов `NAVIGATION` и API `talker.navigation(...)`
- фильтрация видимости логов по типам на экране журнала
- переключение порядка логов через `SwapVert`
- полноэкранный viewer для `body` с раскрывающимися JSON-узлами

### Изменено

- удалены упоминания внешних пакетов из исходников и документации
- документация переведена на русский язык
- добавлены POM-метаданные для артефактов (`group`, `version`, `scm`, `license`, `developer`)
- `kotlinOptions.jvmTarget` переведен на современный `compilerOptions` DSL
- HTTP-interceptor теперь сохраняет полный текстовый body (без preview-обрезки)
