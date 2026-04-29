# CLAUDE.md — Project Guidelines

Repo: GymTracker — hobby Android app w Kotlin / Jetpack Compose / Hilt / Room.

## Naczelne zasady

1. **Aplikacja całkowicie darmowa.** Żadnych subskrypcji, płatności, reklam, in-app purchases.
2. **Tylko Android** (smartfon / tablet). Żadnych smartwatch'y (Wear OS / Apple Watch), żadnego iOS.
3. **Offline-first.** Wszystko lokalnie (Room). Backup przez systemowy picker (Drive / USB / chmura usera).
4. **Bez backendu.** Żadnych serwerów aplikacji, żadnego trackingu, żadnej telemetrii.
5. **BYOK dla AI.** Jeśli kiedykolwiek dodamy AI features (V3), user wkleja własny klucz API.
6. **Open code.** Kod czysty, utrzymywalny, komentarze tylko gdy WHY jest niejasne.

## Stack

- Kotlin 2.1.0
- Jetpack Compose (Material 3)
- Hilt 2.56.2 (DI)
- Room 2.7.2 (DB)
- KSP 2.1.0-1.0.29
- AGP 8.7.3
- Coil 3 (image loading — przyszłość)
- kotlinx-datetime, kotlinx-serialization

## Schema migracji

DB version: aktualna **v=5**. Używamy `fallbackToDestructiveMigration(true)` (MVP — strata danych przy migracji akceptowalna). Plan: gdy app dojrzeje, dodać prawdziwe `Migration` specs.

## Konwencje kodu

- Compose `@Composable` — file per screen + opcjonalnie file per ViewModel
- ViewModels w pakiecie `ui/<feature>/`
- DAO w `data/db/dao/`, entity w `data/entity/`, repo w `data/repository/`
- Stringi PL w `res/values/strings.xml` — bez hardcodów w UI
- Material3 ExperimentalApi opt-in globalny (compiler flag)
- Foreground service rest timer: type **specialUse** + `FOREGROUND_SERVICE_SPECIAL_USE` permission

## Build

CI: GitHub Actions `.github/workflows/build.yml` — debug APK przy każdym pushu na main.

Lokalnie:
```
./gradlew assembleDebug
```

APK trafia jako artefakt do release'u (gh release create v0.X.0 ...).

## Roadmap

Patrz [ROADMAP.md](ROADMAP.md) — fazy V1 / V2 / V3 z checkboxami. Każda zakończona faza = odhaczenie + bump versionName + nowy release.
