# UNIFIED COACH ARCHITECTURE — scalenie całego systemu w jeden organizm

> **Dyrektywa Macieja (2026-06-13):** apka ma być JEDNĄ spójną całością, nie zbiorem
> pojedynczych reakcji. Cel użytkownika (schudnąć / utrzymać / przytyć) jest korzeniem;
> AI trener + AI dietetyk dzielą jeden kontekst i NADZORUJĄ algorytmy; wszystko reaguje
> razem (dieta, treningi, kroki, nawodnienie, regeneracja, kontuzje, systematyczność,
> jakość ćwiczeń). **Nie duplikujemy funkcji, nie doklejamy fixów** — inaczej zrobi się kolos.
>
> Ten dokument = MAPA tego co jest + PROJEKT scalenia + ROADMAPA. Kod dopiero po akceptacji.

---

## 0. Zasada naczelna
**Algorytm liczy → AI nadzoruje → Orchestrator decyduje → jedna spójna reakcja.**
Każda istniejąca i przyszła reakcja przechodzi przez jeden punkt. Żadna nie decyduje sama.

---

## 1. Co JUŻ istnieje (fundament — nie zaczynamy od zera)

### ✅ Wspólny kontekst — `MasterAiContextBuilder` / `MasterAiContext`
Już dziś w jednym przejściu zbiera CAŁOŚĆ: cel + typ celu, trend wagi, adherence
(kcal/białko/węgle/tłuszcz), recovery (sen/HRV/stres/tętno/SpO2/energia/soreness), NEAT
(kroki 7/30d), nawodnienie, faza diety, trening dziś, PRy, muscle recovery, training readiness,
ulubione/unikane (ćwiczenia, produkty, posiłki). Zasila: `DietAiService`, `WorkoutPlanAiService`,
`WeeklyReportService`, `PlanAuditService`. **To jest szkielet „jednego kontekstu", o który prosisz.**

### ✅ Silniki/analizatory (liczą sygnały) — istnieją, działają
Trening: `DeloadDetector`/`DeloadService`, `PeriodizationOrchestrator`+`TrainingPhaseAnalyzer`,
`StagnationAnalyzer`, `TrainingLoadAnalyzer` (ACWR), `MuscleRecoveryAnalyzer`,
`TrainingReadinessAnalyzer`, `EventDetector` (PR/gap/deload/injury), `LoadIncreaseService`.
Zdrowie/regeneracja: `RecoveryAnalyzer`, `RecoveryScoreCalculator`, `HealthInsightAnalyzer`
(sen+HRV→adjustment), `NeatAnalyzer` (kroki).
Dieta: `CalorieAdjustmentEngine`+`AutoAdjustmentService`, `PhaseManager`, `DietGoals`,
`HydrationCalculator`, `DietVolatilityAnalyzer`, `AdherenceCalculator`, `SafetyGuard`.
Cel/efekty: `GoalAchievementService`, `TrendAnalyzer`.

### ✅ AI — kontekst + narzędzia
`MasterAiContextBuilder` (kontekst), `AiToolHandler` (narzędzia), `AiQuickAskViewModel`
(unified trener+dietetyk), `AiTrainerViewModel`, `DietAiService`, `WorkoutPlanAiService`,
`WeeklyReportService`, `PlanAuditService`, `AiDecisionExplainer`.
**Narzędzia AI (co AI realnie potrafi):**
- READ: `get_workouts/events/rollups/exercise_history/body_history`, `find_exercises_*`, `get_pending_decisions`
- WRITE (dane usera): `log_weight`, `add_meal`, `set_calorie_target`, `set_diet_goal`
- PROPOSE (periodyzacja): `propose_deload`, `propose_periodization_action`, `transition_phase`, `schedule_next_cycle`

---

## 2. Pełna inwentaryzacja

### A. SYGNAŁY (co mierzymy)
| Sygnał | Źródło | W MasterContext? |
|--------|--------|------------------|
| Trend wagi (slope kg/tydz) | TrendAnalyzer | ✅ |
| Adherence kcal/makro 14d | AdherenceCalculator | ✅ |
| Recovery (sen/HRV/stres/HR/SpO2/energia) | RecoveryAnalyzer | ✅ |
| Soreness / muscle recovery per partia | MuscleRecoveryAnalyzer | ✅ |
| Gotowość do treningu (kompozyt) | TrainingReadinessAnalyzer | ✅ |
| ACWR / obciążenie | TrainingLoadAnalyzer | częściowo |
| Stagnacja e1RM | StagnationAnalyzer | przez PRy |
| NEAT / kroki 7/30d | NeatAnalyzer / ActivityRepo | ✅ |
| Nawodnienie adherence | HydrationCalculator | ✅ |
| Faza treningu / diety | TrainingPhaseAnalyzer / PhaseManager | ✅ |
| Kontuzja / ból | EventDetector / painArea | przez injuriesNotes |
| Wahania kcal | DietVolatilityAnalyzer | ✗ |
| Brak akcji (posiłek/trening) | DailyReviewWorker (v2.31) | ✗ |

### B. DECYDENCI (kto z sygnałów robi decyzję) — **TU JEST ROZPROSZENIE**
| Decyzja | Kto decyduje (dziś, osobno) |
|---------|------------------------------|
| Deload | `DeloadService` + `TrainingPhaseAnalyzer(NEEDS_DELOAD)` + `EventDetector(DELOAD_DETECTED)` + `NotificationCenter(phase_needs_deload)` |
| Zwiększenie obciążenia | `LoadIncreaseService` (z ACWR DETRAINING) |
| Korekta kcal | `CalorieAdjustmentEngine`/`AutoAdjustmentService` + `SafetyGuard` |
| Przejście fazy cyklu | `PeriodizationOrchestrator` |
| Faza diety / refeed | `PhaseManager` |
| Reakcja na sen/HRV | `HealthInsightAnalyzer` (REST/DELOAD/LIGHT) |
| Pominięty posiłek | `DietViewModel.skippedMealAlert` (v2.30) |
| Braki dnia | `DailyReviewWorker` (v2.31) |
| Cel osiągnięty | `GoalAchievementService` |

### C. POWIERZCHNIE REAKCJI (gdzie to wychodzi) — **DWA RÓWNOLEGŁE KANAŁY**
| Kanał | Co | Kiedy |
|-------|-----|-------|
| `NotificationCenter` | dzwonek IN-APP (recovery/ACWR/faza/brak treningu/brak wagi) | liczony co render Home |
| `HomeCardsResolver` | karty na Home (AI proposal/deload/injury/readiness/phase...) | render Home |
| `DeloadService.cardState` | stan kafla deload | render Home |
| Karty w Diecie | SkippedMealCard, DietVolatilityCard, AutoAdjustment preview | render Diety |
| Notyfikacje SYSTEMOWE | HomeAlertNotifier, ProactiveAiCheckWorker, DailyReviewWorker, DietAutoAdjustmentWorker, MealReminderWorker, WeeklyReportWorker, UnfinishedWorkoutWorker | workery w tle |

---

## 3. DUPLIKACJE I KONFLIKTY (sedno problemu „kolosa")

1. **Dwa równoległe CELE.** `WeightGoalType` (NONE/CUT/BULK/MAINTAIN na UserProfile) ORAZ
   `DietGoalType` (FAT_LOSS/MUSCLE_GAIN/RECOMP/MAINTAIN na UserDietProfile). Logika rozsiana
   po ~15 plikach gałęzi na cel. **Ryzyko: dieta i trening mogą czytać inny cel.**
2. **Deload wykrywany w 4+ miejscach** niezależnie (patrz B). Każde może niezależnie krzyknąć
   „deload" → ryzyko sprzecznych/podwójnych sygnałów.
3. **„Gotowość/regeneracja" liczona 4×**: RecoveryAnalyzer, RecoveryScoreCalculator,
   TrainingReadinessAnalyzer, MuscleRecoveryAnalyzer + HealthInsightAnalyzer — częściowo to samo.
4. **Dwa kanały powiadomień** (in-app `NotificationCenter` vs systemowe workery) z osobną,
   częściowo pokrywającą się logiką decyzji „co pokazać".
5. **kcal dotykany w 3 miejscach**: `DietGoals` (cel bazowy), `CalorieAdjustmentEngine` (korekta),
   `PhaseManager` (refeed/faza) — bez jednego arbitra kolejności.
6. **AI NIE nadzoruje.** Algorytmy decydują same; AI tylko czyta kontekst i odpowiada/proponuje.
   Narzędzia `propose_*` są opt-in (user musi zapytać), nie wpięte w automatyczny obieg reakcji.
   → To jest kluczowa luka względem wizji „AI kontroluje całość".

---

## 4. DOCELOWA ARCHITEKTURA — `CoachOrchestrator`

```
        ┌──────────────────────────────────────────────┐
        │  CEL (jeden, zunifikowany: CUT/MAINTAIN/...)  │  ← korzeń
        └───────────────────────┬──────────────────────┘
                                ↓
        ┌──────────────────────────────────────────────┐
        │  CoachSignals  (rozszerzony MasterAiContext)  │  ← JEDNO źródło sygnałów
        │  dieta+trening+kroki+woda+regeneracja+kontuzje│
        └───────────────────────┬──────────────────────┘
                                ↓
        ┌──────────────────────────────────────────────┐
        │           CoachOrchestrator (NOWY)            │  ← JEDYNY punkt decyzji
        │  • zbiera sygnały od silników (= „narzędzia") │
        │  • dedup + priorytet + anti-konflikt          │
        │  • waży reakcje WG CELU                        │
        │  • AI NADZORUJE: walidacja/priorytet/wyjaśnienie│
        └───────────────────────┬──────────────────────┘
                                ↓
        ┌──────────────────────────────────────────────┐
        │  CoachReaction[]  (jeden model: priorytet,    │
        │  kanał, treść, akcje) → jeden renderer kart   │
        │  + jeden notifier systemowy                    │
        └──────────────────────────────────────────────┘
```

**Role:**
- **Silniki/analizatory** = czujniki. Liczą sygnał, NIE decydują o reakcji. (Zostają, minimalnie zmienione.)
- **CoachOrchestrator** = jedyne miejsce, które z sygnałów + celu + nadzoru AI tworzy
  JEDNĄ skoordynowaną, priorytetyzowaną listę reakcji. Tu mieszka dedup deloadu, kolejność kcal,
  anty-konflikt (np. „nie tnij kcal gdy sen słaby i kroki spadły — najpierw NEAT").
- **AI (nadzór)** = przed wydaniem ważnych/sprzecznych reakcji orchestrator konsultuje AI
  (z `CoachSignals`); AI może przeważyć, połączyć, wyjaśnić. Może zmieniać DANE (nie kod).
- **Jeden kanał reakcji** = `CoachReaction` renderowany jednolicie (karta Home/Dieta) + jeden
  notifier systemowy. Koniec równoległości NotificationCenter vs HomeAlertNotifier.

**Per cel (przykład sterowania):**
- `CUT` (schudnąć): priorytet = deficyt + białko + NEAT/kroki + sen (głód); trening podtrzymujący;
  AI pilnuje by nie ciąć za ostro przy słabej regeneracji.
- `MAINTAIN`: priorytet = stabilność wagi ± korytarz; reaguje na dryf w obie strony.
- `BULK`/`MUSCLE_GAIN`: priorytet = nadwyżka + progresja siły + objętość; deload gdy stagnacja.
- `RECOMP`: waga stała, sygnał = skład/siła; ostrożne korekty.

---

## 5. ROADMAPA SCALANIA (bez utraty funkcji, bez przepisywania od zera)

| Etap | Cel | Co robimy | Ryzyko |
|------|-----|-----------|--------|
| **U1** ✅ v2.32.0 | Jeden CEL | **Unifikacja danych BYŁA ZROBIONA już w v1.28.1** (`goalType` kanoniczny, `weightGoalType` auto-normalizowany w `UserProfileRepository.save`, mapy `toDietGoal/toWeightGoal`, `GoalUnificationTest`). U1 = **usunięcie martwego kodu + duplikacji**: martwe `weightGoalType = ...` w Onboarding/HomeViewModel (nadpisywane normalizacją) → zamienione na kanoniczny `goalType`; zduplikowany inline `when(weightGoalType){...}` → istniejąca `toDietGoal()`; usunięty martwy import. Zachowanie identyczne, testy zielone. **FOLLOW-UP (osobny krok):** backup TRENINGOWY trzyma tylko 4-wart. `weightGoalType` → przy restore cel 8-wart. (RECOMP/STRENGTH) degraduje do MAINTAIN; backup DIETETYCZNY trzyma pełny `goalType`. Do naprawy w kroku backupu (zmiana formatu + wersja). | wykonane |
| **U2** ✅ | Jedno źródło sygnałów | **Zweryfikowane:** doradcy już istnieją i same arbitrażują w domenie — `DeloadService.cardState()` (trening), `AutoAdjustmentService.analyzeNow()` (dieta, z regeneracją/NEAT/nawodnieniem). „Jedno źródło" = wywołanie istniejących doradców; wielki `CoachSignals` NIEpotrzebny. Modułowość gratis (None/NEEDS_MORE_DATA = brak reakcji). Zero nowego kodu. | wykonane |
| **U3** 🔨 rdzeń gotowy | Orchestrator | `data/coach/CoachReaction.kt` (jeden model: domena/priorytet/akcje + `CoachVerdict`) + `CoachOrchestrator.evaluate()` (wywołuje doradców) + czysty arbiter `arbitrateCoach()` (drabina priorytetów + anti-konflikt kontuzja>deload + dedup deloadu). **6 testów zielonych.** DODATKOWY, nie podłączony do UI (świadomie — patrz U4). NIE ruszono istniejących powierzchni. | rdzeń done, czeka na U4 |
| **U4a** ✅ v2.33.0 | Orchestrator ŻYWY | `CoachOrchestrator.evaluate()` wpięty w `DailyReviewWorker` — wieczorny Bilans pokazuje zunifikowany werdykt coacha (trening+dieta) NA GÓRZE + braki. Koniec „dormant". ZERO usuwania powierzchni. | wykonane |
| **U4b** ⏳ | Jeden kanał reakcji (replace) | Karta coacha na Home/Diecie z werdyktu; **zastąpienie** rozproszonych kart (deload/dieta/in-app bell). Akcje `CoachActionType` → istniejące serwisy. To krok „replace" — wymaga potwierdzenia Macieja co znika. | średnie/wyższe |
| **U5** ✅ v2.41.0 | AI-nadzór | `DailyReviewWorker.reviewWithAi` — AI ocenia werdykt raz dziennie (wybór Macieja: tanio) z pełnym kontekstem; komentarz w Bilansie + log `coach_ai_review`. Łapie błędy algorytmu (slope-bug). Bez klucza pomijane. | wykonane |
| **U6** ✅ v2.42.0 | Per-goal + E2E | Per-goal już w silnikach (CalorieAdjustmentEngine/deload goal-aware). `CoachOrchestratorE2ETest` — pusta baza = zero fałszywych reakcji (strażnik przeciw bugom typu slope) + dedup dismissed. | wykonane |

**ROADMAPA U1-U6 KOMPLETNA (v2.32.0–v2.42.0).** System scalony: cel→sygnały→orchestrator(arbiter)→AI-nadzór→jeden kanał (Karta coacha Home+Dieta). Algorytm działa bez AI, AI poleruje gdy klucz jest.

Każdy etap = osobny release, build+testy zielone, zero utraty danych, zero duplikacji.
**Nowe reakcje od teraz przechodzą przez Orchestrator** — nie dokładamy obok.

---

## 6. Żelazne zasady (po scaleniu)
1. Jedno źródło CELU. Jedno źródło SYGNAŁÓW (`CoachSignals`). Jeden punkt DECYZJI (`CoachOrchestrator`). Jeden model REAKCJI (`CoachReaction`).
2. Algorytm liczy, AI nadzoruje, Orchestrator decyduje — nigdy odwrotnie, nigdy w trzech miejscach.
3. Żadna funkcja nie reaguje „obok" orchestratora.
4. Dodajesz sygnał → dokładasz czujnik, NIE nowy decydent.
5. Każda reakcja widoczna w `diagnostic_events` (kampania logowania = warstwa obserwowalności tego systemu).

---

## 7. Status
**FAZA: projekt zaakceptowany do realizacji?** — czeka na decyzję Macieja.
Po akceptacji startujemy od **U1 (jeden cel)** — najbezpieczniejszy, odblokowuje resztę.
