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

- [ ] 0.1 Test harness: in-memory Room + `BackupImporter` loader + helper scenariuszy
- [ ] 0.2 Trace Reporter — framework formatujący raport (dane + pokrycie + kroki + werdykty + wynik)
- [ ] 0.3 Scenariusze bazowe jako pliki JSON w test resources (A-E + edge z sesji)
- [ ] 0.4 Coverage report (JaCoCo) w CI + pierwszy pomiar białych plam

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

**Aktualna faza:** FAZA 0 — nierozpoczęta
**Postęp:** 0 / 38 zadań

## Dziennik realizacji

| Data | Zadanie | Co zrobiono | Wersja/commit |
|------|---------|-------------|---------------|
| 2026-05-15 | — | Plan utworzony i zatwierdzony | — |

---

## Jak wznowić w nowej sesji

1. Przeczytaj ten plik — sekcja **Status** mówi gdzie jesteśmy.
2. Pierwsze nieodhaczone `[ ]` zadanie = następne do zrobienia.
3. Po ukończeniu: odhacz `[x]`, dopisz wiersz do Dziennika, zaktualizuj
   Status, zrób commit.
4. Memory `gymtracker_test_system.md` wskazuje na ten plik.
