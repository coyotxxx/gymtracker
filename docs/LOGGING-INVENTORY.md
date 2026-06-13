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

## OBSZAR B — TRENING / DETEKTORY / PERIODYZACJA 🔶
| Punkt | Plik | Status |
|-------|------|--------|
| Apply / blocked / restore / repair / cancel / dismiss deload | `DeloadService` | ✅ v2.25.0 |
| Apply / blocked / restore / cancel / dismiss zwiększenia obciążenia | `LoadIncreaseService` | ✅ v2.25.0 |
| Orchestrator: utworzenie mezo, week_advanced, transition, end-early, extend | `PeriodizationOrchestrator` | ✅ v2.25.0 |
| EventDetector: PR / gap / deload / injury / plan start-end (+ błąd) | `EventDetectorService` | ✅ v2.25.0 |
| Stagnation / ReturnAfterBreak / ActiveInjury | analyzery | 🔶 (część przez ProactiveAiCheckWorker) |
| Widoczność kart Home | `HomeCardsResolver` | ⬜ |
| Analyzery (TrainingLoad/Readiness/MuscleRecovery/RecoveryScore) werdykty | ai/*Analyzer | ⬜ |

## OBSZAR C — WORKERY / NOTYFIKACJE / SCHEDULERY ✅
| Punkt | Plik | Status |
|-------|------|--------|
| ProactiveAiCheckWorker wynik + notification_sent | `ProactiveAiCheckWorker` | ✅ v2.26.0 |
| WeeklyReportWorker wynik + notification_sent | `WeeklyReportWorker` | ✅ v2.26.0 |
| DietAutoAdjustmentWorker wynik + notification_sent + weigh-in | `DietAutoAdjustmentWorker` | ✅ v2.26.0 |
| MealReminderWorker wysyłka | `MealReminderWorker` | ✅ v2.26.0 |
| UnfinishedWorkoutWorker wysyłka | `UnfinishedWorkoutWorker` | ✅ v2.26.0 |
| HealthConnectSyncWorker (skip/no-perm/done/błąd) | `HealthConnectSyncWorker` | ✅ v2.26.0 |
| HomeAlertNotifier wysyłka | `HomeAlertNotifier` | ✅ (notification_sent) |
| BootCompletedReceiver (reschedule + błąd) | `BootCompletedReceiver` | ✅ v2.26.0 |
| MealStatusReceiver (akcja z notyfikacji + błąd) | `MealStatusReceiver` | ✅ v2.26.0 |
| WorkerRescheduler (reschedule_all) | `WorkerRescheduler` | ✅ v2.26.0 |
| Schedulery enqueue/cancel | 5× Scheduler | ⏭️ pominięte (reschedule_all pokrywa decyzję; per-scheduler = szum co start) |
| RestTimerService start/stop | `RestTimerService` | ⏭️ pominięte (czysty Service, nie-Hilt, niska wartość) |
| NotificationCenter (in-app bell) | `NotificationCenter` | ⏭️ pominięte (liczony co render Home) |

## OBSZAR D — AI ✅
| Punkt | Plik | Status |
|-------|------|--------|
| AiQuickAsk odpowiedź/deflect/fail | `AiQuickAskViewModel` | ✅ |
| KAŻDE narzędzie (read/propose/write) + rate-limit + unknown + too-large | `AiToolHandler.execute` | ✅ v2.27.0 |
| Każde wywołanie AI: ok/błąd + czas + źródło | `AiClient.logCall` | ✅ v2.27.0 |
| Generowanie planu: parse-fail/empty/fail/sukces | `WorkoutPlanAiService` | ✅ v2.27.0 |
| Generowanie diety: parse-fail/HARD-violations/fail/sukces | `DietAiService` | ✅ v2.27.0 |
| AiTrainerViewModel (błąd UI) | `AiTrainerViewModel` | ⏭️ batch E (call i tak loguje AiClient) |

## OBSZAR E — AKCJE USERA / CICHE BŁĘDY ✅
| Punkt | Plik | Status |
|-------|------|--------|
| Start/koniec/porzucenie/usunięcie treningu + feedback | `WorkoutRepository` | ✅ v2.28.0 |
| Zapis/usunięcie pomiaru (waga) | `BodyRepository` | ✅ v2.28.0 |
| Backup import (sukces/błąd, oba wejścia) | `BackupImporter` | ✅ v2.28.0 |
| Backup eksport + WIPE wszystkich danych | `BackupViewModel` | ✅ v2.28.0 |
| Przełączniki proaktywne AI / auto-raport + zapis profilu | `ProfileViewModel` | ✅ v2.28.0 |
| Ręczne dodanie/usunięcie posiłku | `DietViewModel` | ✅ v2.28.0 |
| Ciche `runCatching` (MasterAiContextBuilder, Home) | różne | ⬜ (follow-up) |

## EKSPORT (Debug)
| Sekcja | Status |
|--------|--------|
| diagnosticEvents (300) | ✅ |
| tableCounts (z diagnostyką + dietą) | ✅ |
| adherenceLog (wiersze 30 dni) | ✅ v2.24.0 |
| mealConsumptions (statusy posiłków) | ✅ v2.24.0 |

---
**Postęp:** A: 2/11 · B: 4/7 · C: 10/10 (3 świadomie pominięte) · D: 5/5 · E: 6/7. Aktualizować po każdym batchu.

**Pozostało (po v2.28.0):** A-reszta diety (DietGoals cap/carb-cycling, CalorieAdjustmentEngine ~25 gałęzi, SafetyGuard, PhaseManager, hydration) + B-reszta (HomeCardsResolver, analyzery werdykty) + ciche runCatching (MasterAiContextBuilder/Home). ⭐ Osobno: **reakcja dietetyka AI na pominięte posiłki = FUNKCJA, nie log**.
