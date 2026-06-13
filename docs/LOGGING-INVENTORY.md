# LOGGING-INVENTORY — pełna mapa instrumentacji DiagnosticLogger

> Cel (dyrektywa Macieja 2026-06-13): **każda funkcja, reakcja, decyzja, powiadomienie i komunikat
> musi mieć log**. Bez realnych danych o tym jak aplikacja reaguje nigdy nie zdiagnozujemy czy
> działa poprawnie. Ten dokument to checklista „nic nie pominięte" — odhaczamy obszar po obszarze.

Logger: `DiagnosticLogger` (fire-and-forget, lokalny, eksport tylko przez Debug na żądanie).
Kategorie: `AI · DETECTOR · WORKER · DIET · NOTIFICATION · REPORT · ADHERENCE · USER_ACTION · ERROR`.
Poziomy: `INFO · WARN · ERROR`.

Legenda: ✅ zrobione · 🔶 częściowe · ⬜ do zrobienia

---

## OBSZAR A — DIETA (batch v2.24.0) 🔶
| Punkt | Plik | Status |
|-------|------|--------|
| Zmiana statusu posiłku (CONSUMED/SKIPPED/PLANNED) | `MealConsumptionRepository.cycleStatus/setStatus` | ✅ v2.24.0 |
| Wyliczenie adherence dnia (kcal%, posiłki, SKIPPED) | `AdherenceCalculator.computeForDate` | ✅ v2.24.0 |
| Dodanie/usunięcie posiłku ręcznego | `DietViewModel.addMeal/deleteMeal` | ⬜ |
| Carry-over planu na nowy dzień | `DietViewModel` carryOver | ⬜ |
| Generowanie planu dnia AI | `DietViewModel.generateAiDayPlan` | ⬜ |
| Cel dzienny (deficit cap, carb cycling) | `util/DietGoals.computeDailyGoal` | ⬜ |
| Korekty kalorii (silnik regułowy ~25 gałęzi) | `CalorieAdjustmentEngine` | ⬜ |
| SafetyGuard (limity bezpieczeństwa) | `SafetyGuard` | ⬜ |
| Faza diety / PhaseManager | `PhaseManager` | ⬜ |
| Hydration log | `HydrationLog*` | ⬜ |
| Reakcja dietetyka AI na pominięte posiłki | (do zaprojektowania) | ⬜ |

## OBSZAR B — TRENING / DETEKTORY / PERIODYZACJA ⬜
| Punkt | Plik | Status |
|-------|------|--------|
| Wykrycie deload | `DeloadDetector` | ⬜ |
| Apply / restore deload | `DeloadService.apply/restore` | ⬜ |
| Missed workouts | `DeloadService.checkMissedWorkouts` | ⬜ |
| Orchestrator periodyzacji | `PeriodizationOrchestrator` | ⬜ |
| Stagnation / ReturnAfterBreak / ActiveInjury | analyzery | 🔶 (część przez ProactiveAiCheckWorker) |
| Widoczność kart Home | `HomeCardsResolver` | ⬜ |
| EventDetectorService (używa Log.d, nie DiagnosticLogger) | `EventDetectorService` | ⬜ |

## OBSZAR C — WORKERY / NOTYFIKACJE / SCHEDULERY 🔶
| Punkt | Plik | Status |
|-------|------|--------|
| ProactiveAiCheckWorker wynik | `ProactiveAiCheckWorker` | 🔶 (signal/alert_fired) |
| WeeklyReportWorker wynik + skip | `WeeklyReportWorker` | 🔶 |
| DietAutoAdjustmentWorker wynik | `DietAutoAdjustmentWorker` | 🔶 |
| HomeAlertNotifier wysyłka | `HomeAlertNotifier` | ✅ (notification_sent) |
| Każde `nm.notify(...)` (wszystkie kanały) | różne | ⬜ |
| Schedulery (enqueue/cancel) | `WeeklyReportScheduler` itd. | ⬜ |
| BootCompletedReceiver | `BootCompletedReceiver` | ⬜ |
| MealStatusReceiver (akcja z notyfikacji) | `MealStatusReceiver` | ⬜ |

## OBSZAR D — AI 🔶
| Punkt | Plik | Status |
|-------|------|--------|
| AiQuickAsk odpowiedź/deflect/fail | `AiQuickAskViewModel` | ✅ |
| Write-tools (logWeight/addMeal/setTarget/setGoal) | `AiToolHandler` | 🔶 (tylko write) |
| Read-tools + propose_* | `AiToolHandler` | ⬜ |
| Parse-fail planu/diety | `WorkoutPlanAiService/DietAiService` | ⬜ |
| AiTrainerViewModel | `AiTrainerViewModel` | ⬜ |

## OBSZAR E — AKCJE USERA / CICHE BŁĘDY ⬜
| Punkt | Plik | Status |
|-------|------|--------|
| Start/koniec treningu | `HomeViewModel` | ⬜ |
| Akcje diety | `DietViewModel` | ⬜ |
| Pomiary | `MeasurementsViewModel` | ⬜ |
| Backup eksport/import | `BackupViewModel/BackupImporter` | ⬜ |
| Ciche `runCatching` (MasterAiContextBuilder, Home, Diet, BackupImporter) | różne | ⬜ |

## EKSPORT (Debug)
| Sekcja | Status |
|--------|--------|
| diagnosticEvents (300) | ✅ |
| tableCounts (z diagnostyką + dietą) | ✅ |
| adherenceLog (wiersze 30 dni) | ✅ v2.24.0 |
| mealConsumptions (statusy posiłków) | ✅ v2.24.0 |

---
**Postęp:** A: 2/11 · B: 0/7 · C: 2/8 · D: 1.5/5 · E: 0/5. Aktualizować po każdym batchu.
