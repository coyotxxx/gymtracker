package pl.filebit.gymtracker.testkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.entity.Gender
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.WeightGoalType
import pl.filebit.gymtracker.data.entity.toDietGoal
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet
import pl.filebit.gymtracker.ai.LoadZone
import pl.filebit.gymtracker.data.repository.DeloadCardState
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.seed.ExerciseSeeder
import pl.filebit.gymtracker.ui.home.HomeCardsResolver

/**
 * v1.27 — Symulacja realnego użytkowania.
 *
 * Buduje profile prawdziwych użytkowników (różne cele wagowe, poziomy
 * zaawansowania, sytuacje) z REALISTYCZNYM rozkładem treningów (3-4×/tydz)
 * i przepuszcza przez pełny pipeline ekranu głównego: detektory →
 * HomeUiState → resolver → checker sprzeczności. Każdy profil ma jawne
 * oczekiwania — odchylenie = błąd logiki do zbadania.
 */
class RealUsageSimulationTest : TestHarness() {

    private val day = 86_400_000L

    private suspend fun seedExercises(): List<Long> {
        ExerciseSeeder(context, db.exerciseDao()).seedIfEmpty()
        return db.exerciseDao().getAll().take(3).map { it.id }
    }

    /**
     * Generuje historię treningów wstecz od teraz — gęsto i realistycznie.
     * @param rpeFor RPE setu wg indeksu treningu (0 = najstarszy).
     */
    private suspend fun simulateTraining(
        weeks: Int,
        perWeek: Int,
        exerciseIds: List<Long>,
        startWeightKg: Double,
        weightStepPerWorkout: Double,
        rpeFor: (workoutIndex: Int, total: Int) -> Int,
        painLastWorkout: String? = null,
        offsetDays: Long = 0
    ) {
        val now = System.currentTimeMillis()
        val total = weeks * perWeek
        val intervalDays = 7.0 / perWeek
        for (i in 0 until total) {
            val daysAgo = ((total - 1 - i) * intervalDays).toLong() + offsetDays
            val startedAt = now - daysAgo * day - 3_600_000L
            val wId = db.workoutDao().insert(Workout(
                startedAt = startedAt,
                finishedAt = startedAt + 3_000_000L,
                painArea = if (i == total - 1) painLastWorkout else null
            ))
            val weight = startWeightKg + i * weightStepPerWorkout
            exerciseIds.forEachIndexed { exIdx, exId ->
                repeat(3) { setNum ->
                    db.workoutSetDao().insert(WorkoutSet(
                        workoutId = wId, exerciseId = exId,
                        setNumber = setNum + 1, orderIndex = exIdx,
                        reps = 8, weightKg = weight, isCompleted = true,
                        rpe = rpeFor(i, total)
                    ))
                }
            }
        }
    }

    private suspend fun simulateWeight(weeks: Int, startKg: Double, kgPerWeek: Double) {
        val now = System.currentTimeMillis()
        for (w in 0..weeks) {
            db.bodyMeasurementDao().upsert(BodyMeasurement(
                date = now - (weeks - w) * 7 * day,
                weightKg = startKg + w * kgPerWeek
            ))
        }
    }

    private suspend fun setProfile(goal: WeightGoalType, weightKg: Double) {
        // v1.28.1 (Etap 2): cel = `goalType`; repo normalizuje legacy `weightGoalType`.
        UserProfileRepository(db.userProfileDao()).save(UserProfile(
            bodyweightKg = weightKg, gender = Gender.MALE, daysPerWeek = 4,
            goalType = goal.toDietGoal()
        ))
    }

    /** Wspólny pipeline: stan → detektory → resolver → checker → raport. */
    private suspend fun analyzeHome(profil: String): List<Inconsistency> {
        val kit = HomeDetectors(db, context)
        val state = kit.buildHomeState()
        val cards = HomeCardsResolver.resolve(state)
        val issues = HomeConsistencyChecker.check(state)

        val tr = TraceReport("sim-$profil")
            .section("DETEKTORY")
            .verdict("deloadCard", state.deloadCard::class.simpleName ?: "?",
                (state.deloadCard as? DeloadCardState.Suggestion)
                    ?.recommendation?.reason?.take(80) ?: "")
            .verdict("trainingPhase", state.trainingPhase?.phase?.name ?: "null", "")
            .verdict("trainingLoad",
                "${state.trainingLoad?.zone?.name} (ACWR ${
                    "%.2f".format(state.trainingLoad?.acwr ?: 0.0)})",
                "treningi14d=${state.trainingLoad?.workoutsCount14d}")
            .verdict("recoveryScore",
                state.recoveryScore?.score?.toString() ?: "null",
                "maturity=${state.recoveryScore?.maturity}")
            .verdict("trainingReadiness",
                state.trainingReadiness?.zone?.name ?: "null",
                "maturity=${state.trainingReadiness?.maturity}")
            .section("CO WIDZI USER (${cards.visible.size} kart)")
        cards.visible.forEach { tr.shows(it.key, it.title) }
        tr.section("SPÓJNOŚĆ SYGNAŁÓW")
        if (issues.isEmpty()) {
            tr.line("brak sprzeczności — sygnały spójne")
        } else {
            issues.forEach { tr.note("[${it.severity}] ${it.message}") }
        }
        tr.emit()
        return issues
    }

    // ── PROFIL A — CUT zaawansowany, przemęczony ──────────────────────────
    @Test
    fun `profil A — CUT zaawansowany przemeczony`() = runBlocking {
        val ex = seedExercises()
        setProfile(WeightGoalType.CUT, 95.0)
        // 8 tyg × 4/tydz, RPE rośnie 8→10, wagi stałe (deficyt = brak progresji),
        // waga ciała spada — klasyczny długi cut
        simulateTraining(weeks = 8, perWeek = 4, exerciseIds = ex,
            startWeightKg = 100.0, weightStepPerWorkout = 0.0,
            rpeFor = { i, total -> (8 + 2 * i / total).coerceAtMost(10) })
        simulateWeight(weeks = 8, startKg = 100.0, kgPerWeek = -0.6)

        val issues = analyzeHome("A-cut-przemeczony")
        val kit = HomeDetectors(db, context)
        val deload = kit.buildHomeState().deloadCard

        assertTrue("przemęczony CUT user → sugestia deloadu",
            deload is DeloadCardState.Suggestion)
        assertTrue("dla CUT deload to refeed (nie redukcja wag)",
            (deload as DeloadCardState.Suggestion).recommendation.recommendsDietBreak)
        assertTrue("brak sprzeczności sygnałów", issues.isEmpty())
    }

    // ── PROFIL B — BULK progresujący zdrowo ───────────────────────────────
    @Test
    fun `profil B — BULK progresujacy zdrowo`() = runBlocking {
        val ex = seedExercises()
        setProfile(WeightGoalType.BULK, 80.0)
        // 6 tyg × 4/tydz, RPE umiarkowane 7-8, wagi rosną, waga ciała rośnie
        simulateTraining(weeks = 6, perWeek = 4, exerciseIds = ex,
            startWeightKg = 80.0, weightStepPerWorkout = 0.5,
            rpeFor = { _, _ -> 7 })
        simulateWeight(weeks = 6, startKg = 80.0, kgPerWeek = 0.3)

        val issues = analyzeHome("B-bulk-progresja")
        val deload = HomeDetectors(db, context).buildHomeState().deloadCard

        assertTrue("zdrowy BULK z progresją → BRAK deloadu",
            deload is DeloadCardState.None)
        assertTrue("brak sprzeczności sygnałów", issues.isEmpty())
    }

    // ── PROFIL C — MAINTAIN stabilny ──────────────────────────────────────
    @Test
    fun `profil C — MAINTAIN stabilny zdrowy`() = runBlocking {
        val ex = seedExercises()
        setProfile(WeightGoalType.MAINTAIN, 88.0)
        simulateTraining(weeks = 6, perWeek = 3, exerciseIds = ex,
            startWeightKg = 90.0, weightStepPerWorkout = 0.3,
            rpeFor = { _, _ -> 7 })
        simulateWeight(weeks = 6, startKg = 88.0, kgPerWeek = 0.0)

        val issues = analyzeHome("C-maintain-stabilny")
        val deload = HomeDetectors(db, context).buildHomeState().deloadCard

        assertTrue("stabilny MAINTAIN → BRAK deloadu", deload is DeloadCardState.None)
        assertTrue("brak sprzeczności sygnałów", issues.isEmpty())
    }

    // ── PROFIL D — początkujący, progresja liniowa ────────────────────────
    @Test
    fun `profil D — beginner progresja liniowa BEZ falszywego deloadu`() = runBlocking {
        val ex = seedExercises()
        setProfile(WeightGoalType.NONE, 70.0)
        // 3 tyg × 3/tydz = 9 treningów, +2.5 kg co trening, RPE niskie 6-7
        simulateTraining(weeks = 3, perWeek = 3, exerciseIds = ex,
            startWeightKg = 40.0, weightStepPerWorkout = 2.5,
            rpeFor = { _, _ -> 6 })

        val issues = analyzeHome("D-beginner-liniowy")
        val state = HomeDetectors(db, context).buildHomeState()

        // regresja B1 + brak fałszywej stagnacji przy rosnących wagach
        assertTrue("początkujący z progresją NIE dostaje deloadu",
            state.deloadCard is DeloadCardState.None)
        // ACWR przy <28 dni historii (ramp-up nowicjusza) jest niewiarygodny —
        // chronic-load baseline niekompletny. Nie wolno krzyczeć RISKY.
        assertEquals("ACWR niewiarygodny przy krótkiej historii → INSUFFICIENT",
            LoadZone.INSUFFICIENT, state.trainingLoad?.zone)
        assertTrue("brak sprzeczności sygnałów", issues.isEmpty())
    }

    // ── PROFIL E — powrót po długiej przerwie ─────────────────────────────
    @Test
    fun `profil E — powrot po dlugiej przerwie`() = runBlocking {
        val ex = seedExercises()
        setProfile(WeightGoalType.MAINTAIN, 85.0)
        // regularny blok 4 tyg, potem przerwa 32 dni, potem 1 trening teraz
        simulateTraining(weeks = 4, perWeek = 3, exerciseIds = ex,
            startWeightKg = 80.0, weightStepPerWorkout = 0.5,
            rpeFor = { _, _ -> 7 }, offsetDays = 32)
        simulateTraining(weeks = 1, perWeek = 1, exerciseIds = ex,
            startWeightKg = 80.0, weightStepPerWorkout = 0.0,
            rpeFor = { _, _ -> 8 })

        val issues = analyzeHome("E-powrot-po-przerwie")
        val state = HomeDetectors(db, context).buildHomeState()
        val cards = HomeCardsResolver.resolve(state)

        assertTrue("po 32-dniowej przerwie → karta powrotu",
            state.deloadCard is DeloadCardState.ReturnAfterBreak)
        // faza analizuje tonaż — po przerwie niski tonaż wygląda jak DELOAD;
        // karta fazy musi być ukryta, żeby nie myliła powrotu z deloadem
        assertFalse("karta FAZA CYKLU NIE jest widoczna przy powrocie",
            cards.visible.any { it.key == "TRAINING_PHASE" })
        assertTrue("faza ukryta z jawnym powodem",
            cards.hidden.any { it.key == "TRAINING_PHASE" })
        assertTrue("brak sprzeczności sygnałów", issues.isEmpty())
    }

    // ── PROFIL F — ból w ostatnim treningu ────────────────────────────────
    @Test
    fun `profil F — bol w ostatnim treningu`() = runBlocking {
        val ex = seedExercises()
        setProfile(WeightGoalType.MAINTAIN, 82.0)
        simulateTraining(weeks = 4, perWeek = 3, exerciseIds = ex,
            startWeightKg = 90.0, weightStepPerWorkout = 0.3,
            rpeFor = { _, _ -> 7 }, painLastWorkout = "LOWER_BACK")

        val issues = analyzeHome("F-bol-ostatni-trening")
        val deload = HomeDetectors(db, context).buildHomeState().deloadCard

        assertTrue("ból w ostatnim treningu → karta WYKRYTO BÓL",
            deload is DeloadCardState.ActiveInjury)
        assertTrue("brak sprzeczności sygnałów", issues.isEmpty())
    }
}
