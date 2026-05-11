package pl.filebit.gymtracker.data.repository

import pl.filebit.gymtracker.data.db.dao.TrainingEventDao
import pl.filebit.gymtracker.data.db.dao.TrainingMesocycleDao
import pl.filebit.gymtracker.data.db.dao.WorkoutDao
import pl.filebit.gymtracker.data.entity.MesocyclePhase
import pl.filebit.gymtracker.data.entity.MesocycleStatus
import pl.filebit.gymtracker.data.entity.TrainingEvent
import pl.filebit.gymtracker.data.entity.TrainingEventType
import pl.filebit.gymtracker.data.entity.TrainingMesocycle
import pl.filebit.gymtracker.data.entity.Workout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jednorazowa rekonstrukcja historii mezo-cykli z istniejących danych.
 *
 * Filozofia: po wprowadzeniu entity TrainingMesocycle (v1.13.0) baza Macieja
 * zawiera 3.5 lat treningów ale 0 cykli. Backfill tworzy syntetyczne cykle
 * z ostatnich N tygodni, używając wykrytych deloadów jako "knot's" do podziału:
 *
 *   [trening][trening][DELOAD]   [trening][trening]
 *   └──── cycle 1 ─────┘ deload  └─── cycle 2 (active) ──┘
 *
 * Idempotentne: jeśli mesoDao.count() > 0 → no-op (już istnieją cykle).
 * Bezpieczne dla wielokrotnego wywołania.
 *
 * Wywoływane raz po update do v1.13.0 — z GymTrackerApp.onCreate w Dispatchers.IO.
 */
@Singleton
class MesocycleBackfillService @Inject constructor(
    private val mesoDao: TrainingMesocycleDao,
    private val eventDao: TrainingEventDao,
    private val workoutDao: WorkoutDao
) {
    /**
     * Główne entry point.
     * @param windowWeeks ile tygodni wstecz analizujemy (default 12 = ~3 miesiące)
     * @param nowMs aktualny czas (parametryzowane dla testów)
     * @return liczba utworzonych cykli (0 jeśli no-op lub brak danych)
     */
    suspend fun backfillFromHistory(
        windowWeeks: Int = 12,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        // 1. Idempotencja
        if (mesoDao.count() > 0) return 0

        val windowMs = windowWeeks * 7L * 24 * 60 * 60 * 1000
        val cutoffMs = nowMs - windowMs

        // 2. Pobierz dane wejściowe
        val finishedWorkouts = workoutDao.observeAllOnce()
            .filter { it.finishedAt != null && it.startedAt >= cutoffMs }
            .sortedBy { it.startedAt }

        if (finishedWorkouts.isEmpty()) return 0

        val deloadEvents = eventDao.getByType(TrainingEventType.DELOAD_DETECTED, limit = 50)
            .filter { it.date >= cutoffMs }
            .sortedBy { it.date }

        // 3. Rekonstrukcja cykli — podziel timeline na boundaries z deloadów
        val cycles = reconstructMesocycles(
            firstWorkoutMs = finishedWorkouts.first().startedAt,
            deloadEvents = deloadEvents,
            nowMs = nowMs
        )

        // 4. Zapisz do bazy
        cycles.forEach { mesoDao.upsert(it) }
        return cycles.size
    }
}

/**
 * Pure function — rekonstrukcja cykli z dat treningu + eventów deload.
 *
 * Top-level dla testowalności (wzorzec z `computeTrainingPhaseFromSnapshot`).
 *
 * Algorytm:
 *  - Start = pierwszy trening
 *  - Każdy DELOAD_DETECTED event:
 *      → kończy bieżący cykl ACCUMULATION (COMPLETED, jeśli było >1 tydzień treningu)
 *      → tworzy cykl DELOAD (1 tydzień)
 *  - Po ostatnim deloadzie (od cursor do nowMs) → ostatni ACCUMULATION:
 *      → ACTIVE jeśli >1 tydzień, inaczej brak (nie tworzymy <1 tyg cyklu)
 *
 * Min 1 tydzień (7 dni) per cykl żeby nie tworzyć śmieci dla pojedynczych dni.
 */
internal fun reconstructMesocycles(
    firstWorkoutMs: Long,
    deloadEvents: List<TrainingEvent>,
    nowMs: Long
): List<TrainingMesocycle> {
    val result = mutableListOf<TrainingMesocycle>()
    val cycleLen = 3 * 7L * 24 * 60 * 60 * 1000  // 3 tygodnie default
    val deloadLen = 7L * 24 * 60 * 60 * 1000     // 1 tydzień deload
    val minCycleLen = 7L * 24 * 60 * 60 * 1000   // próg minimalny cyklu
    var cursor = firstWorkoutMs

    deloadEvents.forEach { deload ->
        // Cykl akumulacji do deloadu (jeśli było tu >=1 tydzień treningu)
        val accumulationEnd = deload.date
        if (accumulationEnd - cursor > minCycleLen) {
            result += TrainingMesocycle(
                startDateMs = cursor,
                endDateMs = accumulationEnd,
                plannedEndDateMs = cursor + cycleLen,
                phase = MesocyclePhase.ACCUMULATION,
                weekInPhase = TrainingMesocycle.defaultLengthForPhase(MesocyclePhase.ACCUMULATION),
                phaseLengthWeeks = TrainingMesocycle.defaultLengthForPhase(MesocyclePhase.ACCUMULATION),
                volumeProgression = TrainingMesocycle.defaultVolumeProgression(MesocyclePhase.ACCUMULATION),
                intensityProgression = TrainingMesocycle.defaultIntensityProgression(MesocyclePhase.ACCUMULATION),
                targetRpe = TrainingMesocycle.defaultTargetRpe(MesocyclePhase.ACCUMULATION),
                triggerReason = "backfill_from_workouts",
                status = MesocycleStatus.COMPLETED,
                notes = "Backfill v1.13.0 — akumulacja między ${formatDateMs(cursor)} a ${formatDateMs(accumulationEnd)}"
            )
        }
        // Cykl deloadu (1 tydzień)
        val deloadEnd = deload.date + deloadLen
        result += TrainingMesocycle(
            startDateMs = deload.date,
            endDateMs = if (deloadEnd <= nowMs) deloadEnd else null,
            plannedEndDateMs = deloadEnd,
            phase = MesocyclePhase.DELOAD,
            weekInPhase = 1,
            phaseLengthWeeks = 1,
            volumeProgression = TrainingMesocycle.defaultVolumeProgression(MesocyclePhase.DELOAD),
            intensityProgression = TrainingMesocycle.defaultIntensityProgression(MesocyclePhase.DELOAD),
            targetRpe = TrainingMesocycle.defaultTargetRpe(MesocyclePhase.DELOAD),
            triggerReason = "backfill_from_deload_event",
            status = if (deloadEnd <= nowMs) MesocycleStatus.COMPLETED else MesocycleStatus.ACTIVE,
            notes = "Backfill v1.13.0 — DELOAD_DETECTED ${formatDateMs(deload.date)}"
        )
        cursor = deloadEnd
    }

    // Ostatni cykl (od cursor do teraz) — ACTIVE jeśli >=1 tydzień
    if (nowMs - cursor > minCycleLen) {
        result += TrainingMesocycle(
            startDateMs = cursor,
            endDateMs = null,
            plannedEndDateMs = cursor + cycleLen,
            phase = MesocyclePhase.ACCUMULATION,
            weekInPhase = ((nowMs - cursor) / (7L * 24 * 60 * 60 * 1000)).toInt().coerceIn(1, 3),
            phaseLengthWeeks = TrainingMesocycle.defaultLengthForPhase(MesocyclePhase.ACCUMULATION),
            volumeProgression = TrainingMesocycle.defaultVolumeProgression(MesocyclePhase.ACCUMULATION),
            intensityProgression = TrainingMesocycle.defaultIntensityProgression(MesocyclePhase.ACCUMULATION),
            targetRpe = TrainingMesocycle.defaultTargetRpe(MesocyclePhase.ACCUMULATION),
            triggerReason = "backfill_active_cycle",
            status = MesocycleStatus.ACTIVE,
            notes = "Backfill v1.13.0 — aktywny cykl od ${formatDateMs(cursor)}"
        )
    }

    return result
}

private fun formatDateMs(ms: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return "%02d.%02d.%d".format(
        cal.get(java.util.Calendar.DAY_OF_MONTH),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.YEAR)
    )
}
