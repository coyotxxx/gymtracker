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

- [ ] 1.1 `HomeCardsResolver` — wyciągnięcie logiki "co pokazać/ukryć/kolejność" z `HomeScreen.kt` do pure function
- [ ] 1.2 Home snapshot test: scenariusz → `HomeUiState` → `HomeCardsResolver` → raport
- [ ] 1.3 Trace: werdykt każdego detektora + uzasadnienie + sekcja pokrycia danych
- [ ] 1.4 Scenariusze A-E dla Home → wygenerowane raporty
- [ ] 1.5 **PUNKT KONTROLNY** — Maciej ocenia raport, ewentualna korekta wzorca

## FAZA 2 — Logika: pełne pokrycie serwisów (warstwa 3)

41 plików logiki bez testu. Repozytoria-przejściówki (samo delegowanie do DAO) pomijamy.

- [ ] 2.1 Deload/load: `DeloadService`, `LoadIncreaseService`, `AutoAdjustmentService`
- [ ] 2.2 Cele/osiągnięcia: `GoalAchievementService`, `GoalRepository` (logika)
- [ ] 2.3 Dieta — analiza: `DietVolatilityAnalyzer`, `AdherenceCalculator`, `CardioKcalEstimator`
- [ ] 2.4 Dieta — generowanie: `MealPrepPlanner`, `ShoppingListGenerator`, `QuickComposeService`, `EmergencyFoodEstimates`
- [ ] 2.5 Bootstrap: `ExerciseDbBootstrap` (seed match, alias, dedup, dead cleanup)
- [ ] 2.6 Statystyki/most: `StatsCacheService`, `TrainingDietBridge`
- [ ] 2.7 Pozostałe serwisy z realną logiką (przegląd reszty 41)

## FAZA 3 — Snapshoty wszystkich ekranów (warstwa 2)

48 ekranów. Każdy: ViewModel → State → raport "co user widzi".

- [ ] 3.1 Trening: Home (z Fazy 1), CoachWorkout, ActiveWorkout, WorkoutDetail, History
- [ ] 3.2 Plany: Plans, PlanEdit, PlanTemplates
- [ ] 3.3 Ćwiczenia: ExerciseLibrary, ExerciseDetail, ExercisePicker
- [ ] 3.4 Dieta core: Diet, DietOnboarding, MealPreferences
- [ ] 3.5 Dieta rozszerzenia: AdherenceReport, AdjustmentHistory, MealPrep, ShoppingList, RecipeBrowser
- [ ] 3.6 Statystyki: Stats, Achievements, MuscleEngagement, StrengthStandards
- [ ] 3.7 Pomiary/ciało: Measurements, MeasurementAdd, BodyMap, ProgressPhotos
- [ ] 3.8 AI: AiTrainer, AiConversations, AiSettings, AiLog, AiWeeklyReport
- [ ] 3.9 Profil/ustawienia: Profile, TrainingSettings, Goals, Backup, Glossary, Notifications
- [ ] 3.10 Narzędzia + reszta: Calculators, OneRm, PlateCalc, PeriodizationPlan, Onboarding, Health*, BarcodeScanner, FoodImageAnalyzer, Debug

## FAZA 4 — Migracje + dane

- [ ] 4.1 Test każdej migracji Room (49→60) — round-trip z danymi
- [ ] 4.2 Backup/restore round-trip — eksport → import → identyczność danych
- [ ] 4.3 Bootstrap ExerciseDB — pełna ścieżka na realnych assetach

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

**Aktualna faza:** FAZA 0 UKOŃCZONA → FAZA 1 (pilot Home)
**Postęp:** 4 / 38 zadań
**Następne zadanie:** 1.1 HomeCardsResolver

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

---

## Jak wznowić w nowej sesji

1. Przeczytaj ten plik — sekcja **Status** mówi gdzie jesteśmy.
2. Pierwsze nieodhaczone `[ ]` zadanie = następne do zrobienia.
3. Po ukończeniu: odhacz `[x]`, dopisz wiersz do Dziennika, zaktualizuj
   Status, zrób commit.
4. Memory `gymtracker_test_system.md` wskazuje na ten plik.
