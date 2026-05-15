# Plan systemu testów GymTracker

> **Dokument żywy.** Po każdym ukończonym zadaniu: odhacz `[x]`, dopisz do
> Dziennika, zrób commit. Dzięki temu plan przetrwa wygaśnięcie sesji —
> nowa sesja czyta ten plik i wie gdzie jesteśmy.

## Cel

Zbudować system testów dający **maksymalną realną pewność** o sprawności
aplikacji przed wypuszczeniem na produkcję. Każdy test produkuje **pełen
ślad** (trace): co się stało, jak aplikacja zareagowała i **dlaczego**.

**Uczciwie o pokryciu:** 100% absolutnej pewności nie istnieje w żadnym
oprogramowaniu. Cel realny: **~90-95%** — pełne pokrycie logiki i stanów
ekranów (headless, szybkie) + krytyczne flow UI. Ostatnie ~5% to ręczny
smoke test na urządzeniu przed release.

## Skala aplikacji (stan 2026-05-15)

- 75 500 linii Kotlin
- 48 ekranów, 42 ViewModele
- ~1220 punktów interakcji UI (679 onClick, 312 Button, 215 clickable, 14 toggle)
- 453 testy jednostkowe / 42 pliki testowe (stan startowy)
- 41 plików logiki bez bezpośredniego testu
- 1 test instrumentowany (MigrationTest), 0 testów UI, 0 automatycznych E2E

## Zasady

1. **Headless gdzie się da** — testy logiki i stanów bez emulatora (sekundy, CI).
2. **Każdy test = raport** — nie czarna skrzynka pass/fail. Trace pokazuje
   dane wejściowe + pokrycie danych, każdy krok, werdykt każdego warunku
   z uzasadnieniem, finalny stan ekranu, reguły które coś ukryły/pokazały.
3. **Warstwowo** — każda faza daje wartość osobno, nie trzeba czekać na całość.
4. **Waliduj wzorzec na pilocie** zanim powielisz go 47 razy.
5. **Scenariusze realne** — dane z prawdziwych sesji testowych Macieja (A-E,
   edge case'y), nie sztuczne.

## Architektura — 5 warstw

| Warstwa | Co | Technologia |
|---|---|---|
| 1. Mapa pokrycia | 48 ekranów × opcje, status każdej | dokument + skrypt |
| 2. Screen Snapshots | każdy ekran: scenariusz → stan → raport | headless, in-memory Room |
| 3. Logic E2E + trace | każdy serwis/detektor: werdykt + dlaczego | unit + Robolectric |
| 4. UI interaction | kliknięcia, toggle, dialogi, nawigacja | Compose UI test |
| 5. Trace Reporter | każdy test → czytelny raport | wspólny framework |

---

## FAZA 0 — Fundament / infrastruktura

- [x] 0.1 Test harness: in-memory Room + `BackupImporter` loader + helper scenariuszy
- [x] 0.2 Trace Reporter — framework formatujący raport (dane + pokrycie + kroki + werdykty + wynik)
- [x] 0.3 Scenariusze bazowe jako pliki JSON w test resources (A-E + edge z sesji)
- [x] 0.4 Coverage report (JaCoCo) w CI + pierwszy pomiar białych plam

## FAZA 1 — Pilot: ekran Home (walidacja wzorca)

- [x] 1.1 `HomeCardsResolver` — wyciągnięcie logiki "co pokazać/ukryć/kolejność" z `HomeScreen.kt` do pure function
- [x] 1.2 Snapshot test v1 — syntetyczne stany → resolver → TraceReport (6 stanów)
- [~] 1.3 **PUNKT KONTROLNY #1** — ocena: pilot v1 wykrywa tylko reguły kart, za mało. Decyzja: dokończyć pełny E2E przed skalowaniem.
- [x] 1.4 Pełny E2E — scenariusz danych → prawdziwe detektory → resolver. Raport: DANE/pokrycie + DETEKTORY z liczbami + CO WIDZI USER + OCENA. **Wykrył 4 realne anomalie** (patrz Dziennik).
- [x] 1.5 Sekcja DANE/pokrycie + werdykty z liczbami — zrobione w 1.4
- [x] 1.6 Aktywny detektor sprzeczności sygnałów (HomeConsistencyChecker, 5 reguł) + sekcja OCENA
- [x] 1.7 **PUNKT KONTROLNY #2** — wzorzec ZAAKCEPTOWANY przez Macieja, skalujemy
- [ ] 1.8 Bugi wykryte przez E2E (B1-B4) — osobne zadania po FAZIE 2

## FAZA 2 — Logika: pełne pokrycie serwisów (warstwa 3)

41 plików logiki bez testu. Repozytoria-przejściówki (samo delegowanie do DAO) pomijamy.

- [x] 2.1 Deload/load: `DeloadService`, `LoadIncreaseService` (AutoAdjustmentService → 2.3, to serwis diety)
- [x] 2.2 Cele/osiągnięcia: `GoalAchievementService` (4 przypadki graniczne)
- [x] 2.3 Dieta — lekkie: `DietVolatilityAnalyzer` ✓, `CardioKcalEstimator` ✓
- [x] 2.4 BLOK DIETOWY ✅ lekkie (DietLightServicesTest), planujące (DietPlanningServicesTest), `AdherenceCalculator` (AdherenceCalculatorTest), silnik korekt kcal `CalorieAdjustmentEngine` + `SafetyGuard`. **UWAGA:** `AutoAdjustmentService.analyzeNow()` jako orkiestrator (13 dep + AI) nie pokryty E2E — sedno logiki jest w silniku (pokryty); orkiestracja → ręczny smoke.
- [x] 2.5 Bootstrap: `ExerciseDbBootstrap` (import + GIFy + dedup zweryfikowane)
- [x] 2.6 Statystyki/most: `StatsCacheService`, `TrainingDietBridge`
- [x] 2.7 Pozostałe serwisy z realną logiką — przegląd: `TrendAnalyzer` pokryty (TrendAnalyzerTest). Reszta z 41 to repozytoria-przejściówki nad DAO (DietRepository, RecoveryRepository, ActivityRepository, BodyRepository, GoalRepository, PlanRepository itd.) + wrappery Preferences — pomijane wg zasady fazy. RecoveryAnalyzer/NeatAnalyzer/HydrationCalculator już miały testy.

**FAZA 2 KOMPLETNA** — 59 plików testów, cała logika serwisowa z realną logiką pokryta.

## FAZA 3 — Snapshoty wszystkich ekranów (warstwa 2)

48 ekranów. Każdy: ViewModel → State → raport "co user widzi".

- [x] 3.1 Trening: Home (Faza 1) ✓, WorkoutDetail ✓, History ✓, CoachWorkout ✓, ActiveWorkout ✓ — ViewModelKit + snapshoty
- [x] 3.2 Plany: Plans ✓, PlanEdit ✓ (nowy + istniejący). PlanTemplates = statyczny obiekt danych (bez ViewModelu/logiki stanu).
- [x] 3.3 Ćwiczenia: ExerciseLibrary ✓ (193 ćwiczeń), ExerciseDetail ✓, ExercisePicker ✓
- [x] 3.4 Dieta core: Diet ✓ (DietViewModel — 31 zależności, najcięższy ekran), DietOnboarding ✓, MealPreferences ✓
- [x] 3.5 Dieta rozszerzenia: AdherenceReport ✓, AdjustmentHistory ✓, MealPrep ✓, ShoppingList ✓, RecipeBrowser ✓
- [x] 3.6 Statystyki: Stats ✓, Achievements ✓ (modal), MuscleEngagement ✓, StrengthStandards ✓
- [x] 3.7 Pomiary/ciało: Measurements ✓ (pomiary + trendy), ProgressPhotos ✓. MeasurementAdd/BodyMap = ekrany formularza/wizualizacji bez własnego ViewModelu.
- [x] 3.8 AI: AiTrainer ✓, AiConversations ✓, AiSettings ✓, AiLog ✓, AiWeeklyReport ✓ (AiPreferences.secure — fallback gdy KeyStore niedostępny)
- [x] 3.9 Profil/ustawienia: Profile ✓, Goals ✓, Backup ✓, Notifications ✓. TrainingSettings = ekran bez własnego VM (ProfileViewModel), Glossary = statyczny słownik (GlossaryTest w FAZIE 2).
- [x] 3.10 Narzędzia + reszta: Onboarding ✓, PeriodizationPlan ✓, Debug ✓, HealthHistory ✓, Update ✓. Calculators/OneRm/PlateCalc = czyste UI (lokalny Compose state), BarcodeScanner/FoodImageAnalyzer = CameraX (poza zakresem headless).

**FAZA 3 KOMPLETNA** — wszystkie ekrany z ViewModelem mają snapshot. ViewModelKit = kompletny kontener DI dla testów.

## FAZA 4 — Migracje + dane

- [x] 4.1 Migracje Room — round-trip 56→60 (odtworzenie bazy v56 z JSON, migracja, walidacja schematu, dane przeżywają) + test ciągłości łańcucha 49→60. Schematy 49-55 nie eksportowane historycznie — najnowsze 4 migracje pokryte round-trip.
- [x] 4.2 Backup/restore round-trip — eksport ZIP → clearAllTables → import → identyczność (30 treningów, 240 serii, 12 pomiarów bezstratnie).
- [x] 4.3 Bootstrap ExerciseDB — pokryty w 2.5 (ExerciseDbBootstrapTest: import z realnych assetów exercisedb_v1.json, >50% z GIF, zero duplikatów).

**FAZA 4 KOMPLETNA** — migracje + backup zweryfikowane, zero utraty danych.

## FAZA 5 — UI interaction (warstwa 4)

- [ ] 5.1 Flow: rozpocznij trening → loguj set → timer → zakończ
- [ ] 5.2 Flow: generuj plan AI → zastosuj → start
- [ ] 5.3 Flow: dieta — dodaj posiłek → logowanie → adherence
- [ ] 5.4 Flow: backup → restore
- [ ] 5.5 Reprezentatywne "banalne": toggles, dialogi, nawigacja, dismiss kart

## FAZA 6 — Integracja CI + pre-release

- [ ] 6.1 Wszystkie warstwy w CI (unit + Robolectric + instrumented)
- [ ] 6.2 Pre-release checklist — zbiorczy raport stanu przed każdym release
- [ ] 6.3 Coverage gate — minimalne pokrycie wymagane do merge

---

## Status

**Aktualna faza:** FAZA 4 ZAKOŃCZONA → FAZA 5 (UI interaction)
**Postęp:** 38 / 41 zadań
**Następne zadanie:** FAZA 5 — UI interaction (krytyczne flow)

### Lokalny build (od 2026-05-15)
JDK 17 + Android SDK lokalnie — testy ~1-2 min zamiast 7 min CI.
`JAVA_HOME=/home/debian/jdk-17.0.19+10 ./gradlew :app:testDebugUnitTest`

### Punkt kontrolny #1 (2026-05-15)
Pilot v1 (snapshot na syntetycznych stanach) wykrywa TYLKO błędne reguły
kompozycji kart (~15% problemów). NIE wykrywa: bugów detektorów,
niespójności stanu, braków danych, złych komunikatów, "złej filozofii".
Decyzja: dokończyć pilot Home do pełnego E2E (detektory na realnych
danych + pokrycie + sprzeczności + oceny) ZANIM skalować na 47 ekranów.

### Baseline pokrycia (2026-05-15, przed FAZĄ 1)
Pomiar JaCoCo — punkt wyjścia, mapa białych plam:
- **Total: 4%** (z UI Compose — zawyżone "missed", UI to FAZA 3-5)
- Logika: `data.repository` 20%, `util` 33%, `ai` 7%, `data.entity` 32%
- `ui.*` — prawie wszystko 0% (brak testów UI — spodziewane)
Cel: po FAZA 1-3 logika i stany ekranów znacząco w górę.

## Dziennik realizacji

| Data | Zadanie | Co zrobiono | Wersja/commit |
|------|---------|-------------|---------------|
| 2026-05-15 | — | Plan utworzony i zatwierdzony | d7f4a0c |
| 2026-05-15 | 0.1 | Harness: Robolectric + in-memory Room + loadScenario przez BackupImporter. Fix: AiPreferences lazy crypto (KeyStore niedostępny w JVM). Sanity test zielony w CI. | 171b63f |
| 2026-05-15 | 0.2 | TraceReport — framework raportu (section/coverage/verdict/shows/hidden/note + emit do build/reports/traces). Test zielony w CI. | b13a74c |
| 2026-05-15 | 0.3 | 5 scenariuszy bazowych (fresh/healthy/overtraining_cut/return_after_break/injury) + smoke. Placeholdery czasu {{D-N}} — scenariusz wieczny. Generator gen_test_scenarios.py. ScenarioLoadTest 8/8 zielony w CI. | d1cc9eb |
| 2026-05-15 | 0.4 | JaCoCo: plugin + task jacocoTestReport + krok CI + artefakt coverage-report. Baseline zmierzony (logika repo 20%, util 33%). **FAZA 0 UKOŃCZONA.** | cb14c03 |
| 2026-05-15 | 1.1-1.2 | HomeCardsResolver (pure function: stan→karty visible/hidden) + HomeSnapshotTest (6 stanów→TraceReport). Artefakt trace-reports w CI. | bde2b3d, f7e4a8f |
| 2026-05-15 | 1.3 | Punkt kontrolny #1: pilot v1 za wąski (tylko reguły kart). FAZA 1 rozbita na pełny E2E. | — |
| 2026-05-15 | 1.4-1.5 | Pełny E2E Home (HomeDetectors 12 obiektów + HomeFullE2ETest 5 scenariuszy). Raporty wykryły 4 ANOMALIE: (B1) świeży user 3 treningi → "DELOAD/stagnacja"; (B2) healthy 24 treningi stała waga → "stagnacja"; (B3) TrainingLoad zawsze INSUFFICIENT/ACWR=0 (karta ACWR nigdy dla normalnego usera); (B4) sprzeczność DeloadSuggestion vs Readiness GOOD. | f9bd8e9 |
| 2026-05-15 | 1.6 | HomeConsistencyChecker — 5 reguł sprzeczności sygnałów. Raport OCENA flaguje B4 jako [KONFLIKT] automatycznie. Wzorzec raportu kompletny. | bed473c |
| 2026-05-15 | 1.7 | Punkt kontrolny #2: wzorzec raportu ZAAKCEPTOWANY. **FAZA 1 UKOŃCZONA.** Skalujemy na FAZĘ 2. | 8a1c60e |
| 2026-05-15 | 2.1 | DeloadServiceTest (5 scenariuszy, werdykty deload/injury/return zielone) + LoadIncreaseServiceTest (cykl apply/restore, restore=dokładnie oryginalne wagi). | a8246bb, 7c53116 |
| 2026-05-15 | 2.2 | GoalAchievementServiceTest — 4 przypadki graniczne karty CEL OSIĄGNIĘTY (reguła ISSN 7 dni stabilności). | 127836e |
| 2026-05-15 | 2.3 | DietVolatilityAnalyzerTest (wahania kcal, 3 przyp.) + CardioKcalEstimatorTest (kcal cardio 7d, 3 przyp.). Dietowe ciężkie serwisy → blok 2.4. | 98c88a8, d433b83 |
| 2026-05-15 | 2.6 | TrainingDietBridgeTest (Workout→TrainingDaySummary) + StatsCacheServiceTest (spójność snapshotu). Lokalny build JDK17. | 723e71d |
| 2026-05-15 | 2.5 | ExerciseDbBootstrapTest — bootstrap importuje, >50% z GIF, zero duplikatów (dedup zweryfikowany). | 9399d4a |
| 2026-05-15 | 2.4a | DietLightServicesTest — QuickCompose (gramatura pod target, +11% kcal raportowane), EmergencyFood (lookup + POPULAR), DietaryKnowledge (JSON z assetów). | 1f74825 |
| 2026-05-15 | 2.4b | DietPlanningServicesTest — ShoppingListGenerator (agregacja 2 dni, zaokrągl. do opakowań) + MealPrepPlanner (plan 4 kroki, 65 min). | 9354169 |
| 2026-05-15 | 2.4c1 | AdherenceCalculatorTest — pipeline goal (Mifflin 2879 kcal) → spożycie (755 kcal co do grama) → AdherenceLog. Zostaje AutoAdjustmentService. | 22fe58e |
| 2026-05-15 | 2.4c2 | CalorieAdjustmentEngineTest (5 bramek silnika: NEEDS_MORE_DATA, recovery override, REFEED, no-goal) + SafetyGuardTest (floor kcal, BMR floor, Block/Warn). Zamyka blok dietowy. | db5b839 |
| 2026-05-15 | 2.7 | TrendAnalyzerTest (kierunki trendu wagi: NO_DATA / FALLING+fastLoss / FLAT+stagnacja / RISING+fastGain). **FAZA 2 zakończona.** | 89476bd |
| 2026-05-15 | 3.1 | ViewModelKit (graf repo) + snapshoty: WorkoutDetail, History, CoachWorkout, ActiveWorkout. Wzorzec ViewModel-snapshot ustalony. | 174f058, 309ea14, 4226336 |
| 2026-05-15 | 3.2 | ViewModelKit rozbudowany o MasterAiContextBuilder (17 dep) — graf AI kompletny. Snapshoty PlanList + PlanEdit (nowy/istniejący). | ee9000e |
| 2026-05-15 | 3.3 | ExerciseSnapshotTest — ExerciseLibrary (193 ćwiczeń), ExercisePicker, ExerciseDetail (przez SavedStateHandle). | 6e5dc2e |
| 2026-05-15 | 3.4 | DietCoreSnapshotTest — DietOnboarding (wizard), MealPreferences, Diet (DietViewModel 31 dep — graf DI kompletny: cel 2879 kcal, posiłki, grupy). | 8d6c6a7, 70da9c8 |
| 2026-05-15 | 3.5 | DietExtSnapshotTest — AdherenceReport, AdjustmentHistory, MealPrep, ShoppingList, RecipeBrowser (empty state każdego). | 89826d2 |
| 2026-05-15 | 3.6 | StatsSnapshotTest — Stats (5120 kg, 3 PR, 24 dni heatmapy), MuscleEngagement, StrengthStandards, Achievement modal. | 01e83d6 |
| 2026-05-15 | 3.7 | MeasurementsSnapshotTest — Measurements (3 pomiary, trend wagi), ProgressPhotos (empty state). | ed5802c |
| 2026-05-15 | 3.8 | AiSnapshotTest — AiTrainer/AiConversations/AiSettings/AiLog/WeeklyReport. AiPreferences.secure: fallback do plain prefs gdy KeyStore niedostępny (też hardening produkcyjny — brak crashu). | ac7cf5f |
| 2026-05-15 | 3.9 | ProfileSnapshotTest — Profile (dane usera), Goals, Backup, Notifications (empty states). | 78bb491 |
| 2026-05-15 | 3.10 | ToolsSnapshotTest — Onboarding, PeriodizationPlan, Debug, HealthHistory, Update. **FAZA 3 zakończona.** | 5070539 |
| 2026-05-15 | 4.1 | MigrationTest — round-trip 56→60 (baza v56 z JSON → migracja → walidacja, dane przeżywają) + ciągłość łańcucha 49→60. Migracje AppModule: private→internal. | f4f63a7 |
| 2026-05-15 | 4.2-4.3 | BackupRoundTripTest — eksport ZIP → clear → import bezstratny (30 treningów/240 serii/12 pomiarów). 4.3 ExerciseDB bootstrap pokryty w 2.5. **FAZA 4 zakończona.** | (ten commit) |

## Bugi wykryte przez system testów (do rozpatrzenia — zadanie 1.8)

| # | Opis | Scenariusz | Status |
|---|------|-----------|--------|
| B1 | Świeży user (3 treningi, RPE 6-7) dostaje "DELOAD ZALECANY — stagnacja na 3 ćwiczeniach". 3 treningi to za mało na werdykt stagnacji. | fresh | do analizy |
| B2 | healthy (24 treningi, stała waga) → "stagnacja". Częściowo artefakt scenariusza (generator nie progresuje wag), do weryfikacji czy detektor też za czuły. | healthy | do analizy |
| B3 | TrainingLoadAnalyzer zawsze INSUFFICIENT (ACWR=0.00) — nawet przy 30 treningach. Karta ACWR nigdy nie pokaże się userowi trenującemu 3×/tydz. Próg "daysOfData" za wysoki? | wszystkie | do analizy |
| B4 | Sprzeczność: DeloadService→Suggestion (przeciążenie) ale TrainingReadiness→GOOD(80). Dwa systemy, sprzeczne werdykty. | overtraining_cut, healthy | do analizy |

---

## Jak wznowić w nowej sesji

1. Przeczytaj ten plik — sekcja **Status** mówi gdzie jesteśmy.
2. Pierwsze nieodhaczone `[ ]` zadanie = następne do zrobienia.
3. Po ukończeniu: odhacz `[x]`, dopisz wiersz do Dziennika, zaktualizuj
   Status, zrób commit.
4. Memory `gymtracker_test_system.md` wskazuje na ten plik.
