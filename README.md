# 🏋️ GymTracker

Hobby aplikacja Android do logowania treningów na siłowni.

**Stack:** Kotlin · Jetpack Compose · Material 3 · Room · Hilt · Navigation Compose

---

## 📦 Co jest w MVP (sesja 1)

- ✅ **Logowanie treningu** — start/wznów, wybór ćwiczenia z biblioteki, dodawanie serii (kg × reps), oznaczanie jako wykonane
- ✅ **Auto-fill** — pierwsza seria każdego ćwiczenia podpowiada wagę i powtórzenia z ostatniej sesji
- ✅ **Timer odpoczynku** — foreground service, działa przy zgaszonym ekranie, wibracja na koniec, +/-15s, skip
- ✅ **Historia** — lista zakończonych treningów z podsumowaniem (czas, serie, volume)
- ✅ **Szczegóły treningu** — wszystkie ćwiczenia i serie z możliwością usunięcia całego treningu
- ✅ **Biblioteka 55 ćwiczeń** (sztanga, hantle, maszyny, wyciąg, ciężar ciała) z polskimi nazwami
- ✅ **Profil** — cel treningowy, doświadczenie, jednostka, domyślny czas odpoczynku
- ✅ **Backup JSON** — eksport/import wszystkich danych przez Storage Access Framework

W sesji 2 dodamy: edycja planów treningowych, wykresy progresu, automatyczna detekcja PR.
W sesji 3: AI generator planów (Claude API).

---

## 🚀 Jak zbudować APK przez GitHub Actions

### 1. Utwórz repo na GitHubie
- Wejdź na https://github.com/new
- Nazwa: `gymtracker` (albo dowolna)
- **Private** (hobby projekt, nie potrzebujesz publicznego)
- Bez README, bez .gitignore (mamy własne)

### 2. Wgraj kod
```bash
cd gymtracker
git init
git add .
git commit -m "Initial MVP"
git branch -M main
git remote add origin https://github.com/TWOJ_USER/gymtracker.git
git push -u origin main
```

### 3. Zaczekaj 3-5 minut
GitHub automatycznie uruchomi workflow `Build APK`. Postęp obserwujesz w zakładce **Actions** w repo.

### 4. Pobierz APK
- Wejdź w **Actions** → kliknij ostatni workflow run
- Na dole sekcja **Artifacts** → `gymtracker-debug-apk`
- Pobierz ZIP, rozpakuj — w środku `app-debug.apk`

### 5. Zainstaluj na telefonie
- Skopiuj APK na telefon (np. przez USB, Drive, Telegram do siebie)
- Otwórz plik na telefonie
- Android zapyta: **„Pozwolić aplikacji [Files / Chrome] na instalację z nieznanych źródeł?"** → Tak
- Tap **Zainstaluj**
- Gotowe ✅

---

## 🔄 Jak wgrywać zmiany

Każdy `git push` na branch `main` automatycznie zbuduje nowy APK.

Można też ręcznie odpalić build z GitHuba: **Actions → Build APK → Run workflow**.

---

## 📁 Struktura projektu

```
app/src/main/
├── AndroidManifest.xml
├── assets/
│   └── exercises.json          # seed bazy ćwiczeń (55 ruchów)
├── res/                        # tematy, strings.xml (polski), ikony
└── java/pl/filebit/gymtracker/
    ├── GymTrackerApp.kt        # Application class (Hilt)
    ├── MainActivity.kt
    ├── di/AppModule.kt         # Hilt providery
    ├── data/
    │   ├── entity/             # encje Room (Exercise, Workout, WorkoutSet, UserProfile)
    │   ├── db/                 # AppDatabase, DAO, Converters
    │   ├── repository/         # warstwa abstrakcji nad DAO
    │   └── seed/               # ExerciseSeeder
    ├── service/
    │   └── RestTimerService.kt # foreground service - timer odpoczynku
    ├── ui/
    │   ├── theme/              # Compose Material 3 dark theme
    │   ├── navigation/         # bottom nav + NavHost
    │   ├── components/         # RestTimerBar
    │   ├── home/               # Home screen
    │   ├── workout/            # ActiveWorkout + ExercisePicker
    │   ├── history/            # History list + WorkoutDetail
    │   ├── exercises/          # ExerciseLibrary
    │   ├── profile/            # Profile editor
    │   └── backup/             # JSON export/import (SAF)
    └── util/
        └── Formatters.kt       # daty, czas, ciężar
```

---

## 🛠️ Wersje (luty 2025)

- AGP 8.7.3
- Kotlin 2.1.0
- KSP 2.1.0-1.0.29
- Compose BOM 2024.12.01
- Material 3 (najnowsza w BOM)
- Room 2.6.1
- Hilt 2.52
- Navigation Compose 2.8.5
- min SDK 29 (Android 10), target SDK 35 (Android 15)

---

## 🐛 Jeśli build w Actions się wywali

Najczęstsze powody:
1. **Network timeout** podczas pobierania zależności → po prostu uruchom Run again
2. **Out of memory** → zwiększ `org.gradle.jvmargs` w `gradle.properties`
3. **Niezgodność wersji** → zaktualizuj `gradle/libs.versions.toml`

Logi błędów: Actions → kliknij failed run → rozwiń step `Build debug APK`.

---

## 📝 Następne kroki

Aktualnie aplikacja jest **w pełni funkcjonalna do logowania treningów**. Możesz jej użyć od jutra na siłowni.

Jak będziesz chciał dodatkowych funkcji (plany, wykresy, AI), wracaj do Claude w nowej sesji — wszystkie zmiany robimy na bieżąco i pushujemy.

---

Built with Claude (Anthropic) jako hobby projekt 💪
