# GymTracker — Raport badania błędów (2026-06-12)

**Stan:** v2.22.0 (versionCode 596, DB v73), branch redesign-v2
**Metoda:** pełne testy jednostkowe + lint + kompilacja androidTest + 5 równoległych agentów-recenzentów na obszarach zmian sesji 2026-06-10/11 (12 release'ów v2.11→v2.22) + ręczna weryfikacja kluczowych twierdzeń.
**Decyzja właściciela:** tylko raport — naprawy w następnej sesji (po testach na telefonie).

## ✅ Zdrowe
- Testy jednostkowe: wszystkie zielone. androidTest kompiluje się.
- **Migracje 67→73: zweryfikowane SQL vs schematy JSON — czyste.** Zero ryzyka crashu po update, zero utraty danych. Hilt+nullable-default DAO poprawne. Defaulty Kotlin↔DB spójne (brak defaultValue w schematach → Room nie porównuje).
- Lint: 1 błąd pre-existing (`BarcodeScannerScreen.kt:277` UnsafeOptInUsageError, sprzed sesji).

## 🔴 KRYTYCZNE (do v2.23.0)

### K1. MissedWorkout: false-positive dla świeżego planu
`DeloadService.kt:165-190` (`checkMissedWorkouts`) — okno 7 dni nie jest przycięte do `TrainingPlan.createdAt` (pole istnieje, nieużywane) ani do daty aktywacji. Świeży plan Pn/Śr/Pt utworzony w sobotę → natychmiast FIRM „Opuściłeś 3 z 3" + notyfikacja. Dodatkowo ignoruje `WeeklyPlanOverride` (przesunięcia i świadome „Pomiń" liczą się jako missed — apka karze za użycie własnej funkcji; Home używa `getEffectiveScheduleForCurrentWeek()`).
**Fix:** pomijać dni `< dayStartOf(activePlan.createdAt)`; rozwiązywać efektywne dni per tydzień z overrides (DB trzyma 6 tyg.). Idealnie: timestamp aktywacji w `setActivePlan`.

### K2. CTA „Zacznij trening" = no-op gdy dziś jest dzień planu (ZWERYFIKOWANE)
`HomeViewModel.kt:832-833` — `startNextPlannedToday` robi `state.value.nextPlannedDay ?: return`, a `nextPlannedDay` liczone TYLKO gdy `todaysPlan == null` (`:293`). Typowy scenariusz missed (opuścił Pn+Śr, dziś Pt=dzień planu) → klik w „Zacznij trening" (MissedWorkoutCard v2.12, ReturnAfterBreakCard v2.19) nic nie robi, bez feedbacku.
**Fix:** fallback — gdy `nextPlannedDay==null && todaysPlan!=null` → `startWorkoutFromPlanForDay(todaysPlan.id, isoDay, onCoach)`. + guard na `activeWorkout != null` (LOW: CTA dokleja duplikaty setów do aktywnego treningu).

### K3. Carry-over reintroduktuje fałszywe 100% adherence
`DietViewModel.kt:970-984` (`carryOverPlanIfEmpty`) — `m.copy(id=0, dateMs, createdAt)` zachowuje `isPlanned` źródła. Wczorajsze RĘCZNE wpisy (isPlanned=false) skopiowane na dziś = „zjedzone" o poranku (AdherenceCalculator `else -> !m.isPlanned`). Dokładnie klasa buga naprawianego w v2.11.
**Fix:** `copy(..., isPlanned = true)` (komentarz funkcji mówi „jako zaplanowany").

### K4. MealStatusReceiver gubi kliki (brak goAsync)
`MealStatusReceiver.kt:28,47-51` — zapis w `scope.launch` po powrocie z `onReceive`; system może ubić proces → status+recompute niezapisane, a notyfikacja skasowana (cancel synchroniczny). Utrata danych bez śladu.
**Fix:** `goAsync()` + `finish()` w finally (lub OneTimeWorkRequest). Przy okazji [LOW]: klik po północy zapisuje na zły dzień — przekazywać dateMs w extras.

### K5. add_meal (AI v2.22) nie przelicza adherence
`AiToolHandler.kt:execAddMeal` — brak `adherenceCalc.computeForDate(dateMs)` (każda ścieżka UI to robi). Dziennik się zmienia, adherence nie.
**Fix:** wstrzyknąć AdherenceCalculator, recompute po addMeal. (Brak cyklu zależności.)

### K6. OpenAI: prompt obiecuje zapis, tools nie idą
`AiClient.kt:175` — `OPENAI -> callOpenAi(...)` bez tools; `AiQuickAskViewModel` prompt: „Możesz ZMIENIAĆ dane… użyj narzędzi". GPT odpowie „zapisałem" — w bazie nic. Fałszywe potwierdzenie.
**Fix (minimum):** zdanie o zapisie tylko gdy `provider == ANTHROPIC`; dla OpenAI jawne „nie masz zapisu". Docelowo function-calling dla OpenAI.

### K7. WeeklyReportScheduler: UPDATE + dynamiczny initialDelay łamie „poniedziałek 9:00"
`WeeklyReportScheduler.kt:24-33` + `WorkerRescheduler` przy KAŻDYM starcie apki. Semantyka UPDATE zachowuje lastEnqueueTime, bierze nowy delay → next run dryfuje; po pierwszym biegu cykl kotwiczy się na złym dniu na stałe. KDoc WorkerRescheduler („schedulers używają KEEP") nieprawdziwy dla weekly/dietAdjustment.
**Fix:** `ExistingPeriodicWorkPolicy.KEEP` w schedulePeriodic (UPDATE tylko przy zmianie toggle w ProfileViewModel — tam cancel→schedule). + weryfikacja dnia w workerze. [MED powiązane: brak constraint NetworkType.CONNECTED i brak Result.retry → tydzień bez raportu przepada.]

## 🟠 ŚREDNIE (po K1-K7)

- **M1. Cel oceniany ≠ wyświetlany (kcal):** `AdherenceCalculator.kt:41-48` używa `config.manualKcal` bez `kcalForDate(start)` (weeklyKcalOverrides — dni refeed!) i bez cardio bonus, które DietViewModel pokazuje (`:1077-1089`). Refeed dzień: user je 2750 wg celu, oceniany vs 2200 → „125%".
- **M2. DietAiService.generateDayPlan bez isTrainingDay/kcalForDate/cardio** (`DietAiService.kt:198-204`) — AI generuje plan pod płaskie makro, niespójny z cyklowanym celem v2.18.
- **M3. Worker omija per-type dismiss:** `ProactiveAiCheckWorker.kt:100,127-132` — po X-nięciu alertu `cardState()`=None → fallback woła surowe `missedWorkoutSignal()` bez dismissedAtMs i bez hasha → codzienny spam. Także: przy None/Active worker nie woła `maybeNotify` → stale notyfikacje niekasowane + hash niezresetowany; early-return zagłusza regułę periodyzacji (PendingDecision nie powstaje tygodniami).
- **M4. `UserProfileRepository.save()` zamyka MAINTENANCE-podczas-CUT** przy każdym niezwiązanym zapisie profilu (porównuje fazę z celem zamiast wykrywać ZMIANĘ celu). Save wołany m.in. z zapisu wagi (StrengthStandardsViewModel:43), GoalsViewModel:75. **Fix:** porównać stary vs nowy goal przed zamykaniem.
- **M5. log_weight (AI) duplikuje wiersze dnia** — UI (`saveQuickWeight`) deduplikuje przez copy() istniejącego pomiaru dnia; AI zawsze INSERT. Trend/historia przekłamane.
- **M6. add_meal contains-lookup** może trafić zły produkt (alfabetycznie pierwszy zawierający frazę: „kurczak"→„Bulion z kurczaka"). **Fix:** przy >1 trafieniu zwrócić toolErr z listą kandydatów (pętla tool_use dopyta).
- **M7. Rozjazd HomeCardsResolver ↔ HomeScreen:** reguła `misleadingAfterBreak` (ukryj FAZĘ przy ReturnAfterBreak, v1.26.9) jest TYLKO w resolverze (`HomeCardsResolver.kt:110-127`); HomeScreen (`:354-360`) jej nie ma → realny UI pokazuje mylącą kartę DELOAD obok POWRÓT PO PRZERWIE, a testy przechodzą.
- **M8. generateAiDayPlan:** nie resetuje starych statusów CONSUMED (świeży plan od razu „zjedzony" jeśli rano oznaczono slot) + czyści tylko widoczne sloty z `state.groups` (wpisy spoza typesForSlots zostają i liczą się do adherence).

## 🟡 NISKIE / PRE-EXISTING

- Import backupu: duplikuje posiłki przy re-imporcie (upsert id=0 = INSERT); gubi `weeklyKcalOverrides` (DTO nie ma pola, import konstruuje nowy DietConfig); gubi `workoutContext` w MealEntryDto; brak recompute adherence po imporcie.
- Sloty SNACK przy mealsPerDay=5/6: te same wpisy w 2-3 slotach → podwójne liczenie w totals; wspólny status konsumpcji.
- Cap deficytu v2.15: warning odpala się też gdy działa manualKcalOverride (deficyt nieużywany), a manualKcal NIE jest capowany tempem (obejście jednym polem). Liczyć od `finalKcal - tdee`.
- DST: `lastCompletedWeekStartMillis = current - 7*24h` pęka 2×/rok (klucz dedup + granica tygodnia ±1h). Fix: `minus(daysFromMonday+7, DAY)`.
- Rest-day carb cycling przy niskim manualKcal: suma makro może przekroczyć kcal (carbs=0, fat podbity).
- AiToolHandler: `SimpleDateFormat` nie thread-safe + niesynchronizowany rate-limit deque (singleton, 2 równoległe czaty); SimpleDateFormat lenient (przyjmie 2026-02-31).
- chatWithTools: przy timeout/limicie 6 rund po wykonanym zapisie user widzi tylko błąd (dane już zmienione) — dopisać ostrzeżenie do komunikatu błędu.
- recompute historycznego dnia używa DZISIEJSZEGO aktywnego planu (isPlannedTrainingDay nie jest historyczne).
- Status z notyfikacji nie odświeża otwartego ekranu diety (użyć observeForDate w combine).
- WeeklyReportWorker: brak NetworkType.CONNECTED + brak retry (tydzień przepada przy braku sieci w poniedziałek).
- Lint pre-existing: BarcodeScannerScreen.kt:277 UnsafeOptInUsageError.

## Plan sugerowany
- **v2.23.0** = K1-K7 (zbiorczy bugfix + testy: false-positive missed, CTA fallback, carry-over isPlanned=true, goAsync, add_meal recompute, OpenAI gating, scheduler KEEP).
- **v2.24.0** = M1-M8 (spójność celów + dismiss w workerze + save-goal-change + AI write polish + resolver↔screen).
- Niskie: przy okazji kolejnych prac w danym obszarze.
