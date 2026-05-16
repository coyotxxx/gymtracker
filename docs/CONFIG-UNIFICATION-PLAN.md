# Plan refaktoru — Jedno źródło prawdy (scalenie konfiguracji)

> Dokument roboczy. Przetrwa zmianę sesji — czytaj go na starcie każdej nowej sesji
> dotyczącej tego refaktoru. Aktualizuj sekcję STATUS po każdym etapie.

## STATUS (aktualizuj po każdym etapie)

| Etap | Opis | Status | Release |
|------|------|--------|---------|
| 0 | Baseline + przygotowanie | ✅ DONE | — |
| 1 | Scalenie tabel (migracja 60→61) | ✅ DONE | v1.28.0 |
| 2 | Jeden cel (migracja 61→62) | ✅ DONE | v1.28.1 |
| 3 | Ekran „Konfiguracja" | ✅ DONE | v1.28.2 |
| 4 | Kreator = ten sam ekran | ✅ DONE | v1.28.3 |
| 5 | Sprzątanie (migracja 62→63) | ▶ CI/RELEASE | v1.28.4 |

Legenda: ☐ TODO · ▶ W TRAKCIE · ✅ DONE

---

## 1. Cel i kontekst

Maciej zgłosił (2026-05-16): konfiguracja rozproszona po wielu ekranach, brak
jednego źródła prawdy, dane się rozjeżdżają. Wybrał **Wariant A** — scalenie
encji `UserProfile` + `UserDietProfile` w jedną. Warunek: aplikacja po KAŻDYM
etapie musi w pełni działać, zero utraty danych usera.

## 2. Stan obecny (audyt 2026-05-16)

### Encje konfiguracyjne
- `UserProfile` (tabela `user_profile`, singleton id=1) — 22 pola, profil treningu.
- `UserDietProfile` (tabela `user_diet_profile`, singleton id=1) — 22 pola, profil diety.
- Baza Room: **wersja 60**. Migracje to prawdziwe `Migration(N,N+1)` w
  `di/AppModule.kt` (MIGRATION_49_50 … MIGRATION_59_60), rejestrowane przez
  `.addMigrations(...)`. `fallbackToDestructiveMigration` USUNIĘTY (tylko
  `fallbackToDestructiveMigrationOnDowngrade`).

### Repozytoria (proste — kluczowe dla nakładki w Etapie 1)
- `UserProfileRepository`: `observe(): Flow<UserProfile>`, `get(): UserProfile`,
  `save(UserProfile)`. DAO: `UserProfileDao` (observe/get/upsert).
- `UserDietProfileRepository`: `observe(): Flow<UserDietProfile?>`, `get(): UserDietProfile?`,
  `upsert(...)`, `save(...)`, `isOnboardingDone(): Boolean`. DAO: `UserDietProfileDao`.

### 4 reprezentacje „celu" (główny problem)
- `UserProfile.weightGoalType` — enum `WeightGoalType` (NONE/CUT/BULK/MAINTAIN).
- `UserDietProfile.goalType` — enum `DietGoalType` (8 wartości) — **najbogatszy, kanoniczny**.
- `UserProfile.goal` — enum `TrainingGoal` (5 wart.) — osobne pojęcie, używane jako
  fallback celu diety w `mapFromTrainingGoal`.
- `Goal` (tabela `goals`) typu LOSE_WEIGHT/GAIN_MASS — cel długoterminowy.

### 2 bugi do naprawienia w trakcie planu
- **Bug A — utrata danych.** `DietOnboardingViewModel.complete()` robi
  `upsert(UserDietProfile(...))` budując NOWY obiekt → gubi `avgStepsPerDay`,
  `customDeficitKcal`, `usualTrainingHour`, `intolerances` (wracają do defaultów).
  Naprawia się w Etapie 4 (znika osobny upsert).
- **Bug B — rozjazd celu.** `DietSettingsDialog`/`saveDietProfileQuick` zapisuje
  `goalType`, NIE syncuje do `weightGoalType`. Sync istnieje tylko jednokierunkowo
  (`ProfileViewModel.save`). Naprawia się w Etapie 2.

### Pliki konsumujące (zakres zmian)
- `weightGoalType` / `WeightGoalType` — ~29 plików (z grep), z czego logika:
  AiContextBuilder, DietAiService, MasterAiContext(+Builder), WorkoutPlanAiService,
  BackupImporter, DietBackupManager, Converters, UserProfile, AchievementDefinitions,
  DeloadService, GoalAchievementService, PhaseManager, StatsRepository, DeloadDetector,
  DietGoals, CalorieAdjustmentEngine + ViewModele (Home/Diet/Goals/Measurements/
  Profile/Onboarding/DietOnboarding) + ekrany.
- `DietGoalType` / `goalType` — ~16 plików.

## 3. Architektura docelowa

- **Jedna encja** `UserProfile` (tabela `user_profile`), ~43 pola — scala wszystkie
  pola obu profili.
- **Jeden cel** — `DietGoalType` (8 wart.). `weightGoalType` usunięty; kierunek
  wagi (deficyt/surplus/utrzymanie) wyliczany helperem `DietGoalType.toWeightDirection()`.
- **Jeden ekran „Konfiguracja"** w Profilu, sekcje. `TrainingSettings` +
  `DietSettingsDialog` wchłonięte. `DietOnboarding` znika — kreator to ten sam
  ekran w trybie krok-po-kroku.
- **Logi codzienne BEZ zmian** — waga/pomiary, woda, kroki dnia, regeneracja
  zostają tam gdzie są (to nie konfiguracja).

---

## ETAP 0 — Baseline + przygotowanie

**Cel:** zabezpieczyć punkt powrotu. Bez release, bez zmian kodu.

Kroki:
1. Backup APK v1.27.9 → `/home/debian/gymtracker-baselines/v1.27.9/`.
2. Zapisać commit hash v1.27.9 (`fd2c88f`) + hasło powrotu „wracamy do v1.27.9".
3. Potwierdzić że `app/schemas/60.json` istnieje (eksport schematu — wymóg
   `MigrationTestHelper`).
4. Ten dokument utworzony i zapisany w pamięci projektu.

Weryfikacja: ✅ APK v1.27.9 → `/home/debian/gymtracker-baselines/v1.27.9/`,
commit `fd2c88f`, `app/schemas/pl.filebit.gymtracker.data.db.AppDatabase/60.json`
potwierdzony. Wykonano 2026-05-16.

---

## ETAP 1 — Scalenie tabel (migracja 60→61) — niewidoczne dla użytkownika

**Cel:** jedna tabela pod spodem. UI i zachowanie aplikacji IDENTYCZNE.

Kroki:
1. **Encja `UserProfile`** — dopisać 21 pól z `UserDietProfile` (bez `id`).
   Zmiana nazw kolidujących: `onboardingCompletedAt`→`dietOnboardingCompletedAt`,
   `updatedAt`→`dietUpdatedAt`. Razem ~43 pola.
2. **`UserDietProfile`** — przestaje być `@Entity`; zostaje jako zwykły data class
   (widok/DTO), żeby ~16 konsumentów kompilowało się bez zmian.
3. **`UserDietProfileRepository`** — staje się NAKŁADKĄ: `get()` czyta scalony
   `UserProfile` i mapuje pola diety na `UserDietProfile`; `upsert/save` czyta
   bieżący `UserProfile`, wstawia pola diety, zapisuje przez `UserProfileDao`.
   `observe()` mapuje flow. **API repozytorium BEZ ZMIAN** → konsumenci nietknięci.
4. **`UserDietProfileDao`** — usunięty (lub pusty); `UserDietProfileRepository`
   wstrzykuje `UserProfileDao`. Zaktualizować `AppModule`/Hilt provides.
5. **`AppDatabase`** — usunąć `UserDietProfile` z listy `entities`, podnieść
   `version = 61`. Tabela `user_diet_profile` zostaje w bazie OSIEROCONA
   (Room toleruje nieznane tabele) — bezpiecznik.
6. **Migracja `MIGRATION_60_61`** w `AppModule.kt`:
   ```sql
   ALTER TABLE user_profile ADD COLUMN ageYears INTEGER NOT NULL DEFAULT 30;
   ALTER TABLE user_profile ADD COLUMN heightCm INTEGER NOT NULL DEFAULT 175;
   ALTER TABLE user_profile ADD COLUMN activityLevel TEXT NOT NULL DEFAULT 'MODERATE';
   ALTER TABLE user_profile ADD COLUMN avgStepsPerDay INTEGER NOT NULL DEFAULT 7000;
   ALTER TABLE user_profile ADD COLUMN goalType TEXT NOT NULL DEFAULT 'MAINTAIN';
   ALTER TABLE user_profile ADD COLUMN paceKgPerWeek REAL NOT NULL DEFAULT 0.0;
   ALTER TABLE user_profile ADD COLUMN customDeficitKcal INTEGER;
   ALTER TABLE user_profile ADD COLUMN dietPreference TEXT NOT NULL DEFAULT 'STANDARD';
   ALTER TABLE user_profile ADD COLUMN allergies TEXT NOT NULL DEFAULT '';
   ALTER TABLE user_profile ADD COLUMN intolerances TEXT NOT NULL DEFAULT '';
   ALTER TABLE user_profile ADD COLUMN dislikedFoods TEXT NOT NULL DEFAULT '';
   ALTER TABLE user_profile ADD COLUMN lovedFoods TEXT NOT NULL DEFAULT '';
   ALTER TABLE user_profile ADD COLUMN cookingTimePerMealMin INTEGER NOT NULL DEFAULT 15;
   ALTER TABLE user_profile ADD COLUMN eatsAtWork INTEGER NOT NULL DEFAULT 0;
   ALTER TABLE user_profile ADD COLUMN hasMicrowaveAtWork INTEGER NOT NULL DEFAULT 1;
   ALTER TABLE user_profile ADD COLUMN mealPrepInterested INTEGER NOT NULL DEFAULT 0;
   ALTER TABLE user_profile ADD COLUMN weeklyBudgetPln INTEGER;
   ALTER TABLE user_profile ADD COLUMN medicalConditions TEXT NOT NULL DEFAULT '';
   ALTER TABLE user_profile ADD COLUMN medicalAwareness INTEGER NOT NULL DEFAULT 0;
   ALTER TABLE user_profile ADD COLUMN usualTrainingHour INTEGER;
   ALTER TABLE user_profile ADD COLUMN dietOnboardingCompletedAt INTEGER;
   ALTER TABLE user_profile ADD COLUMN dietUpdatedAt INTEGER NOT NULL DEFAULT 0;
   -- przekopiowanie wiersza diety (tylko jeśli istnieje):
   UPDATE user_profile SET
     ageYears = (SELECT ageYears FROM user_diet_profile WHERE id=1),
     ... (wszystkie pola) ...
   WHERE id = 1 AND (SELECT COUNT(*) FROM user_diet_profile) > 0;
   ```
   Uwaga: guard `WHERE ... AND (SELECT COUNT(*) ...) > 0` chroni usera który nigdy
   nie otwierał diety (brak wiersza `user_diet_profile`) — bez guardu subquery
   zwróciłaby NULL do kolumn NOT NULL i migracja by się wywaliła.
7. **Cel — uzgodnienie przy migracji:** w Etapie 1 NIE ruszamy `weightGoalType`
   (zostaje). Tylko kopiujemy `goalType` z diety. Unifikacja celu = Etap 2.

Weryfikacja Etapu 1 — ✅ UKOŃCZONA 2026-05-16 (commity 2004b01, 88a6157):
- [x] Robolectric `MigrationTest` — realna migracja 56→61 + walidacja schematu v61
      przez Room (CI). Łańcuch 49→61, 12 migracji.
- [x] androidTest `migrate60To61_mergesDietProfileIntoUserProfile` — kopiowanie
      wiersza diety z pełnymi danymi.
- [x] Pełny zestaw testów (597) zielony, CI zielone.
- [x] Emulator: instalacja v1.28.0 na bazie v60 (po v1.27.9) → migracja 60→61
      bez crasha; onboarding, Plany, Profil, kreator diety (zapis przez nakładkę)
      i ekran Diety (odczyt + TDEE 2633 kcal) działają.
- [x] Release v1.28.0.

---

## ETAP 2 — Jeden cel (migracja 61→62)

**Cel:** zlikwidować dualizm celu. `DietGoalType` jedynym celem.

Kroki:
1. Helper `DietGoalType.toWeightDirection()` (lub `toWeightGoalDirection`) — zwraca
   kierunek wagi dla logiki która dziś czyta `WeightGoalType`.
2. Usunąć `weightGoalType` z encji `UserProfile`. Kolumna w bazie ZOSTAJE
   osierocona (drop kolumny w SQLite = recreate tabeli, ryzykowne — nie robimy).
3. Migracja `MIGRATION_61_62`: brak zmian struktury (kolumna zostaje); ewentualnie
   tylko uzgodnienie wartości jeśli potrzebne. De facto może być no-op
   strukturalny — wersję podnosimy bo encja się zmienia (Room wykryje że pole
   zniknęło z encji — kolumna nadmiarowa w tabeli jest OK dla Room? NIE — Room
   wymaga zgodności. **Decyzja implementacyjna:** albo zostawić pole w encji jako
   `@Ignore`/nieczytane, albo zrobić recreate tabeli. Rozstrzygnąć przy implementacji
   — przetestować `MigrationTestHelper`).
4. ~14 konsumentów `weightGoalType` przepiąć na `goalType` + helper.
5. UI: „Cel masy ciała" (TrainingSettings) i „Cel diety" (DietSettings) → to samo
   pole `goalType`. Bug B znika.
6. `ProfileViewModel.save` — usunąć kod sync celu (linie ~75-94, już niepotrzebny).

**ZREALIZOWANO (v1.28.1, 2026-05-16) — decyzje implementacyjne:**
- `weightGoalType` NIE usunięty z encji (drop kolumny = ryzykowny rebuild tabeli).
  Zostaje jako legacy pole **auto-normalizowane** z `goalType` w
  `UserProfileRepository.save()` — dwa pola nie mogą się rozjechać. Fizyczny
  drop kolumny ewentualnie w Etapie 5.
- Migracja 61→62 = TYLKO dane (UPDATE), bez zmian schematu: jednorazowo promuje
  intencjonalny CUT/BULK do `goalType` gdy `goalType` był domyślnym MAINTAIN.
- `goalType` (8 wart.) edytowalny w Ustawieniach treningu i Konfiguracji diety —
  to samo pole. Helpery `DietGoalType.toWeightGoal()` / `WeightGoalType.toDietGoal()`.
- ~30 konsumentów `weightGoalType` NIE ruszanych — czytają spójny mirror.
- Weryfikacja: GoalUnificationTest + MigrationTest 56→62 + CI + emulator + release.

---

## ETAP 3 — Ekran „Konfiguracja"

**Cel:** jedno miejsce edycji całej konfiguracji.

**ZREALIZOWANO (v1.28.2, 2026-05-16):**
- `TrainingSettingsScreen` przekształcony w centralny ekran „Konfiguracja"
  (nazwa kompozytu/route historyczna — user widzi „Konfiguracja").
- Dodane sekcje (wszystkie edytują pola scalonej encji `UserProfile`):
  Dane podstawowe (płeć/wiek/wzrost), Aktywność poza treningiem, Styl diety,
  Preferencje żywieniowe (alergeny/nietolerancje/unikane/ulubione), Dieta —
  praktyczne (czas gotowania/budżet/warunki), Zdrowie. Tempo w sekcji Cel.
- `ProfileScreen`: pozycja „Konfiguracja" zamiast „Ustawienia treningu".
- Bez zmian w bazie.
- **Odłożone:** `DietConfig` (liczba posiłków / okno IF / powiadomienia diety)
  zostaje w szybkim dostępie z zakładki Dieta (⚙ DietSettingsDialog) — te same
  dane, brak rozjazdu. Pełne wchłonięcie ewentualnie w Etapie 4/5.

Weryfikacja: CI + emulator (sekcje renderują się i zapisują) + release.

---

## ETAP 4 — Kreator = ten sam ekran krok-po-kroku

**Cel:** koniec dublowania pytań w 2 kreatorach.

**ZREALIZOWANO (v1.28.3, 2026-05-16) — decyzje implementacyjne:**
- Zamiast przebudowy 8-stopniowego głównego kreatora (ryzykowne) — usunięty
  REDUNDANTNY `DietOnboarding`. Główny kreator i tak zbiera wszystko
  (wiek/wzrost/cel/aktywność + opcjonalna sekcja diety), więc drugi kreator
  tylko dublował pytania.
- `DietOnboardingScreen` + `DietOnboardingViewModel` USUNIĘTE. `Screen.DietOnboarding`
  usunięty. Ekran Diety nie przekierowuje już do kreatora — zawsze działa.
- Bug A znika (nie ma osobnego `upsert(UserDietProfile(...))`).
- `OnboardingViewModel.complete()` zawsze oznacza `dietOnboardingCompletedAt`.
- Doszczegóławianie diety: `DietSettingsDialog` „⚙ Pełna konfiguracja" →
  ekran „Konfiguracja" (Etap 3). Jeden kreator + jeden ekran konfiguracji.
- `needsOnboarding`/`_onboardingChecked` usunięte z `DietViewModel` (martwe).

Weryfikacja: CI + emulator + release.

---

## ETAP 5 — Sprzątanie (migracja 62→63)

**Cel:** domknąć refaktor.

**ZREALIZOWANO (v1.28.4, 2026-05-16) — finał refaktoru:**
- Migracja `MIGRATION_62_63`: `DROP TABLE IF EXISTS user_diet_profile` — osierocona
  tabela usunięta po 4 release'ach potwierdzenia w boju. AppDatabase v=63.
- Dodany brakujący edytor `injuriesNotes` (sekcja „Zdrowie i kontuzje" ekranu
  Konfiguracja) — pole wieloliniowe, AI je uwzględnia.
- `MigrationTest` zaktualizowany do v63 (łańcuch 49→63, 14 migracji).

**ŚWIADOMIE POMINIĘTE (decyzja: ryzyko > wartość):**
- Usunięcie martwych kolumn `availableEquipmentCsv` / `customDeficitKcal` —
  wymagałoby rebuildu 44-kolumnowej tabeli `user_profile` (najryzykowniejszy typ
  migracji). Kolumny są nieszkodliwe (po prostu nieużywane). Zasada „zero utraty
  danych" > kosmetyka. Zostają.
- Edytory `avgStepsPerDay` / `usualTrainingHour` — wartości auto-wyliczane
  (fallback / wykrywane z historii); ręczny edytor = clutter za marginalną wartość.
- `UserDietProfile` data class ZOSTAJE — to aktywne DTO (`UserDietProfileRepository`
  + `DietBackupManager`), nie martwy kod.

Weryfikacja: CI + emulator + release.

---

## ZASADY BEZPIECZEŃSTWA (warunek Macieja)

1. **Prawdziwa migracja Room** — `ALTER TABLE`, NIGDY `fallbackToDestructiveMigration`.
2. **Test `MigrationTestHelper`** z realnym wierszem danych dla KAŻDEJ migracji.
3. **Stabilne API repozytoriów** w Etapie 1 — podmiana implementacji, nie interfejsu.
4. **Stara tabela osierocona** aż do Etapu 5 — dane fizycznie w bazie przez 4 release'y.
5. **Każdy etap = osobny release**: CI zielone + test na emulatorze + sprawdzenie
   na żywej aplikacji ZANIM ruszę następny etap.
6. **Backup pliku PRZED każdą edycją** (zasada projektu).
7. Aplikacja po KAŻDYM etapie w pełni działająca — żadnego stanu połowicznego.
8. Po każdym release: bump versionName + commit + push + CI + `gh release create`.
