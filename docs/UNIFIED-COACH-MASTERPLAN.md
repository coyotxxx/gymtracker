# Unified Coach — Master Plan (audyt 2026-06-16)

> Cel Macieja (powtórzony wielokrotnie): **dieta + treningi + reszta = JEDEN organizm,
> jeden mózg, jeden głos.** AI ma się zachowywać jak prawdziwy trener + dietetyk, którzy
> prowadzą go RAZEM: diagnozują przyczynę, rozmawiają gdy brak danych, adaptują plan i
> doprowadzają do celu. Nie dwa nieme silniki obok siebie. Bez nowych „funkcji" — ma
> *poprawnie działać* to, co zbudowaliśmy.

## 1. Diagnoza: „zorkiestrowana niezależność", nie „jeden mózg"

Audyt całej aplikacji (detektory, reakcje, AI, workery, ekrany, encje) daje jeden wniosek:

- **Dane SĄ wspólne.** `MasterAiContextBuilder` składa 46 pól ze WSZYSTKICH domen (dieta,
  trening, regeneracja, waga, cele, preferencje, gotowość). Fundament „jednego systemu" jest.
- **Decyzje powstają OSOBNO.** Każdy silnik liczy niezależnie; `CoachOrchestrator` tylko
  **zbiera + rankuje + dedupuje** — jest „policjantem ruchu", nie trenerem.
- **AI tylko KOMENTUJE** (DailyReview/DecisionExplainer) albo **prowadzi tylko gdy USER
  kliknie** „Zapytaj AI". **Nie ma** mechanizmu, w którym AI *samo* zaczyna rozmowę, gdy
  brakuje danych.

Skutek dla Macieja (diet-user bez czasu na trening): Coach wystawia komendę „zaloguj
trening" (zakłada przyczynę), dietetyk dalej liczy jakby trenował, oba milczą o tym, że
przy braku treningu w redukcji traci mięśnie. To **dwa głosy**, nie jeden.

## 2. Co JUŻ działa (nie ruszać)

- `MasterAiContext` — wspólny kontekst (46 pól, wszystkie domeny). `ai/MasterAiContextBuilder.kt`.
- Detektor opuszczonego treningu — JEDNA pura funkcja `detectMissedWorkouts` (`util/DeloadDetector.kt`),
  używana i przez push (ProactiveAiCheckWorker) i in-app (DeloadService→CoachCard). Bez duplikatu.
- `CoachOrchestrator.evaluate()` — jeden punkt, używany przez DailyReview (push) i Home (in-app).
- Narzędzia AI (`ai/AiTools.kt`): 11 read, 4 propozycje periodyzacji (PENDING), 6 write
  (dieta add/delete/get meal, set kcal, set goal, log weight).
- Recovery→dieta CZĘŚCIOWO: zły sen/stres blokuje cięcie kcal (`CalorieAdjustmentEngine`).

## 3. WSZYSTKIE szwy (gdzie to „dwa systemy") — pełna lista

| # | Szew | Dowód (file) | Skutek |
|---|------|------|--------|
| S1 | **Dieta nie widzi FAKTYCZNEGO treningu** — tylko PLANOWANE dni (carb cycling) i workoutsDone% jako bramka | `AutoAdjustmentService.analyzeNow`; `CalorieAdjustmentEngine.analyze` (brak param „realny trening"); `TrainingDietBridge.isPlannedTrainingDay`; `DietGoals.kt` carb cycling = isPlannedTrainingDay | Nie trenujesz → dieta NIE zmienia strategii (dalej „cut", tylko HOLD). Carb cycling wg planu, nie wg faktu. |
| S2 | **Trening nie widzi stanu DIETY** — DeloadService zna tylko goalType, nie deficyt/fazę/adherence | `DeloadService.buildDetectionContext` (tylko weightGoalType) | Deload nie koordynuje się z dietą (np. diet break / głęboki deficyt) |
| S3 | **Silniki nie widzą swojego OUTPUTU przed orkiestracją** — dieta liczy nie wiedząc, że deload aktywny/sugerowany | `CoachOrchestrator.evaluate` (deload→potem dieta, brak przepływu) | Możliwe sprzeczne sygnały (deload + cut). Arbiter łata na poziomie REAKCJI, nie wejścia silnika. |
| S4 | **4 niezależne detektory deloadu** (DeloadService RPE; NotificationCenter faza; NotificationCenter recovery RED; CalorieEngine soreness) | jw. | Liczone 4×, dedup dopiero na reakcji |
| S5 | **Karta Coacha identyczna na Home i Diecie** — ten sam werdykt, nie kontekstowy | `HomeScreen.kt` + `DietScreen.kt` (oba `coachVerdict`) | Na Diecie wyskakuje trening-nudge (nie w kontekście) |
| S6 | **Stare karty dublują CoachCard** (TrainingReadiness/Phase/Load) — U4b niedokończony | `HomeScreen.kt` (legacy cards „ADDITYWNIE"); `HomeCardsResolver.kt` (reguły są, UI ich nie używa) | 2–3 powierzchnie na ten sam sygnał |
| S7 | **DietAutoAdjustmentWorker** wysyła osobny push (nie scalony w „Bilans dnia") | `DietAutoAdjustmentWorker` | Osobny kanał obok zunifikowanego |
| S8 | **Brak proaktywnej rozmowy** — AI nigdy samo nie pyta „dlaczego nie trenujesz"; „Zapytaj AI" zawsze user-initiated; brak „missing-data mode" | `AiQuickAskViewModel` (user-initiated); DailyReview/Proactive wysyłają FAKTY, nie pytania | Komenda zamiast diagnozy; AI nie zdobywa przyczyny |
| S9 | **AI = komentator, nie mózg** — werdykt liczą reguły, AI tylko ocenia 2 zdaniami | `DailyReviewWorker.reviewWithAi` (U5) | AI ma pełny obraz, ale nie KIERUJE skoordynowaną reakcją |
| S10 | **Brak pamięci przyczyny/ograniczeń usera** — system nie wie „Maciej wybrał dietę bez treningu", więc nagabuje w kółko | brak encji/flagi | Powtarza niemożliwe („idź ćwiczyć") |

## 4. WSZYSTKIE pominięte powierzchnie (coach ich nie bierze pod uwagę)

| Obszar | Encja | Czemu ważne | Stan |
|---|---|---|---|
| Choroby/medyczne | `UserProfile.medicalConditions` | plan posiłków powinien je respektować | przechowywane, AI nie filtruje |
| Timing posiłków vs trening | `MealEntry.type` + `usualTrainingHour` | okno okołotreningowe | infra jest, brak reakcji |
| Trend HRV (spadek) | `RecoveryLog.hrvMs` | spadek HRV = przeciążenie układu autonomicznego | zapisywane, brak alertu trendu |
| Budżet | `UserProfile.weeklyBudgetPln` | tańsze źródła białka | przechowywane, AI nie używa |
| Trudność meal-prep / feedback | `MealFeedback`, `MealPrepPlan` | upraszczać gdy „za trudne" | minimalnie używane |
| Pomiary ciała (talia/skład) | `BodyMeasurement` (obwody, %bf) | dowód rekompozycji ≠ sama waga | używane tylko do goal-achieved |
| Okna postu (IF) | `FastingWindow` | kolizja z treningiem/posiłkami | śledzone, nie zintegrowane z AI |
| Nawodnienie/NEAT | `HydrationLog`, `DailyActivityLog` | NEAT blokuje cięcia (działa); nawodnienie wpływa na siłę | NEAT ✓, nawodnienie częściowo |
| Rollupy 4-tyg | `WeeklyRollup`/`Monthly` | trend zamiast szumu dziennego | liczone, częściowo w periodyzacji |

## 5. Zasady docelowego zachowania (jak prawdziwi trenerzy)

1. **Diagnoza, nie komenda.** Gdy przyczyna nieznana — pytaj: „Nie trenujesz od X — co się
   dzieje?" [Brak czasu] [Kontuzja] [Inne], nie „zaloguj trening".
2. **AI proaktywnie rozmawia przy braku danych** — samo zaczyna, by poznać przyczynę.
3. **Po poznaniu przyczyny — ADAPTUJ i ZAPAMIĘTAJ.** „Brak czasu" → trener: minimalny trening
   domowy/kroki; dietetyk: chroń mięśnie (białko, mniejszy deficyt). I **przestań** nagabywać o trening.
4. **Jeden głos.** Sygnał z jednej domeny wchodzi do drugiej; wyjście to jedna skoordynowana
   wypowiedź trener+dietetyk, nie dwie karty.
5. **AI prowadzi, reguły pilnują** (floor). Działa bez AI (reguły) i z AI (precyzja, rozmowa).

## 6. Plan wdrożenia (fazy U7–U12, każda = osobny release)

### U7 — Dieta widzi FAKTYCZNY trening (szew S1) ✅ ZROBIONE (v2.57.0)
- `CalorieAdjustmentEngine.analyze()` dostał PEWNE źródło: `realWorkouts14d` (liczba ukończonych
  `Workout` w 14 dni z `WorkoutDao`) + `hasTrainingPlan`. `AutoAdjustmentService` je liczy i podaje.
  **Świadomie NIE** opieramy się na `adherence.workoutsDone` (pochodzi z leniwie tworzonego
  `TrainingDaySummary` — bywa pusty u diet-usera). Adherence = tylko fallback dla testów.
- Reguła w `analyzeCut`: gdy `hasTrainingPlan` i `<2` realnych treningów w 14 dni:
  - chudniesz ≥0.4 kg/tydz → `INCREASE_KCAL +150` (`cut_no_training_protect_muscle`, chroń mięśnie),
  - wolniej → `HOLD` (`cut_no_training_hold`, nie tnij dalej). Oba z poleceniem „białko ≥2 g/kg".
- `CoachOrchestrator.dietReaction` surfacuje OBA wyniki jako kartę „Dieta bez treningu"
  (priorytet CONSISTENCY), zamiast nagabywać „idź ćwiczyć".
- Testy: `CalorieAdjustmentEngineTest` — INCREASE/HOLD bez treningu, pewne źródło nadpisuje
  adherence, brak planu = brak wnioskowania, backward-compat z treningiem.
- Carb cycling (FAKT vs plan) — przeniesione do U8 (koordynacja silników).

### U8 — Trening widzi stan DIETY + koordynacja silników (S2, S3, S4)
- `DeloadService` dostaje fazę/deficyt/adherence → koordynacja timingu (deload vs diet break).
- Orkiestrator: przepływ OUTPUT→INPUT (deload aktywny → dieta HOLD/refeed, nie cut). Arbitraż
  wewnątrz pętli, nie tylko na reakcji.
- Skonsolidować 4 detektory deloadu do jednego źródła.

### U9 — Diagnoza zamiast komend + proaktywna rozmowa AI (S8)
- Nudge'y bez znanej przyczyny → pytania z opcjami (lub otwarcie czatu z konkretnym pytaniem).
- Mechanizm „AI pyta o brakujące dane": gdy luka (np. brak treningu/regeneracji/niepotwierdzone
  posiłki) — AI proaktywnie inicjuje krótką rozmowę, by ustalić przyczynę (na istniejącym
  `AiQuickAsk`/`chatWithTools`, dodać proaktywny trigger).

### U10 — Pamięć przyczyny / trybu usera (S10)
- Zapisywać ustaloną przyczynę/ograniczenie (np. „brak czasu na trening → tryb diet-led”),
  żeby system **zapamiętał** i adaptował trwale + **przestał** nagabywać o niemożliwe.
- Coach respektuje ten tryb we wszystkich domenach (trener łagodzi oczekiwania, dietetyk prowadzi).

### U11 — Jeden głos / jeden kanał (S5, S6, S7) — dokończenie U4b
- Wdrożyć `HomeCardsResolver` w UI: ukryć stare karty (Readiness/Phase/Load) gdy CoachCard je pokrywa.
- Karta kontekstowa per ekran (Dieta = język dietetyka; Home = dominujący sygnał) — nie identyczny duplikat.
- Scalić push DietAutoAdjustment w „Bilans dnia".

### U12 — AI jako mózg koordynujący (S9) + pominięte powierzchnie (sekcja 4)
- DailyReview/AI-nadzór: nie tylko komentarz — produkować skoordynowaną wypowiedź trener+dietetyk
  z wspólnego kontekstu (reguły jako floor).
- Domknąć pominięte sygnały: medyczne (filtr posiłków), timing okołotreningowy, trend HRV,
  budżet, meal-feedback, pomiary obwodów. Każdy do wspólnego kontekstu/coacha.

## 7. Zasada nadrzędna wdrożenia
Każda faza: backup (baseline = poprzedni release), zmiana zachowania na ISTNIEJĄCYM silniku
(bez nowych „funkcji”), pełne testy, jeden GitHub release, weryfikacja na realnej bazie Macieja.
