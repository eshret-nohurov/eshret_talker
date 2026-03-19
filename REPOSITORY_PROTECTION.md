# Защита репозитория и ветки main

Ниже безопасная базовая конфигурация для библиотечного репозитория, чтобы никто не мог вносить изменения в `main` без вашего контроля.

## 1. Настройка прав доступа

В GitHub откройте `Settings -> Collaborators and teams` и проверьте роли:

- вам: `Admin` или `Maintain`
- остальным участникам: `Read` (или `Triage`, если нужен только доступ к issues)

Если человек не должен менять код, не давайте `Write` и выше.

## 2. Защита ветки main (Branch Protection Rule)

Откройте `Settings -> Branches -> Add rule` и задайте:

- Branch name pattern: `main`
- `Require a pull request before merging`
- `Require approvals`: минимум `1`
- `Require review from Code Owners`
- `Require status checks to pass before merging`
- включите `Require branches to be up to date before merging`
- включите `Restrict who can push to matching branches`
- в списке push-разрешений оставьте только себя
- включите `Do not allow bypassing the above settings`
- включите `Block force pushes`
- включите `Block deletions`

## 3. Что уже сделано в репозитории

- добавлен `.github/CODEOWNERS` с владельцем `@eshret-nohurov`
- добавлены шаблоны issues и pull request для единообразного процесса
- добавлены `CONTRIBUTING.md` и `RELEASING.md` для прозрачных правил

## 4. Рекомендуемая модель для библиотек

- все изменения идут через Pull Request
- в `main` не пушим напрямую
- релизы формируются через tag + GitHub Release
- обязательны CI-проверки перед merge
