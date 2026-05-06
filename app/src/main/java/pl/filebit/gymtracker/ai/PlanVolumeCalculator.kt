package pl.filebit.gymtracker.ai

import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.PlanExerciseSet
import pl.filebit.gymtracker.data.entity.TrainingGoal

/**
 * Deterministyczne liczenie volume planu treningowego.
 *
 * AI nie potrafi konsekwentnie liczyć setów per partia — raz wlicza Hip Thrust do
 * GLUTES, raz do QUADS, raz hallucynuje liczby. Ten kalkulator robi to twardo,
 * zawsze tak samo. AI dostaje gotowe liczby + werdykt i tylko proponuje zmiany.
 *
 * Reguła: setów per partia/tydzień = SUMA setów wszystkich ćwiczeń, których
 * `primaryMuscle` to ta partia. Liczymy tylko mięśnie siłowe (CHEST..CALVES),
 * bez CARDIO i OTHER.
 */
object PlanVolumeCalculator {

    /** Mięśnie liczone w audycie (siłowe). CARDIO/OTHER pomijamy. */
    private val TRACKED = listOf(
        MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS,
        MuscleGroup.BICEPS, MuscleGroup.TRICEPS,
        MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES,
        MuscleGroup.CALVES, MuscleGroup.CORE
    )

    /**
     * Liczba setów per partia mięśniowa w tygodniu.
     * Klucz: MuscleGroup. Wartość: liczba setów. Tylko TRACKED.
     */
    fun computePerMuscle(
        exercises: List<PlanExercise>,
        setsByPlanExerciseId: Map<Long, List<PlanExerciseSet>>,
        exerciseById: Map<Long, Exercise?>
    ): Map<MuscleGroup, Int> {
        val result = TRACKED.associateWith { 0 }.toMutableMap()
        exercises.forEach { pe ->
            val ex = exerciseById[pe.exerciseId] ?: return@forEach
            val muscle = ex.primaryMuscle
            if (muscle !in TRACKED) return@forEach
            val sets = setsByPlanExerciseId[pe.id]?.size ?: 0
            result[muscle] = (result[muscle] ?: 0) + sets
        }
        return result
    }

    /** Liczba ćwiczeń per dzień. Klucz: dayOfWeek (1-7). */
    fun computeExercisesPerDay(exercises: List<PlanExercise>): Map<Int, Int> =
        exercises.groupBy { it.dayOfWeek }.mapValues { it.value.size }

    /**
     * Stosunek push:pull (sety). Liczone:
     *  - PUSH = CHEST + SHOULDERS + TRICEPS
     *  - PULL = BACK + BICEPS
     *  - NOGI/CORE pomijamy w tym ratio
     */
    fun computePushPull(perMuscle: Map<MuscleGroup, Int>): PushPullRatio {
        val push = (perMuscle[MuscleGroup.CHEST] ?: 0) +
            (perMuscle[MuscleGroup.SHOULDERS] ?: 0) +
            (perMuscle[MuscleGroup.TRICEPS] ?: 0)
        val pull = (perMuscle[MuscleGroup.BACK] ?: 0) +
            (perMuscle[MuscleGroup.BICEPS] ?: 0)
        return PushPullRatio(push, pull)
    }
}

data class PushPullRatio(val pushSets: Int, val pullSets: Int) {
    val ratioStr: String
        get() = if (pullSets == 0) "$pushSets:0" else "$pushSets:$pullSets"

    /** balanced gdy żadna strona nie ma >1.5× drugiej; przy 0 setach po jednej stronie zawsze unbalanced. */
    val isBalanced: Boolean
        get() {
            if (pushSets == 0 || pullSets == 0) return false
            val bigger = maxOf(pushSets, pullSets)
            val smaller = minOf(pushSets, pullSets)
            return bigger.toDouble() / smaller <= 1.5
        }
}

enum class VolumeStatus { LOW, OK, HIGH }

data class MuscleFinding(
    val muscle: MuscleGroup,
    val sets: Int,
    val targetMin: Int,
    val targetMax: Int,
    val status: VolumeStatus
) {
    val targetRange: String get() = "$targetMin-$targetMax"
    val polishName: String get() = when (muscle) {
        MuscleGroup.CHEST -> "Klatka"
        MuscleGroup.BACK -> "Plecy"
        MuscleGroup.SHOULDERS -> "Bark"
        MuscleGroup.BICEPS -> "Biceps"
        MuscleGroup.TRICEPS -> "Triceps"
        MuscleGroup.QUADS -> "Czworogłowy"
        MuscleGroup.HAMSTRINGS -> "Dwugłowy uda"
        MuscleGroup.GLUTES -> "Pośladki"
        MuscleGroup.CALVES -> "Łydki"
        MuscleGroup.CORE -> "Brzuch/core"
        else -> muscle.name
    }
    val statusEmoji: String get() = when (status) {
        VolumeStatus.LOW -> "⚠️ ZA MAŁO"
        VolumeStatus.OK -> "✅ OK"
        VolumeStatus.HIGH -> "⚠️ ZA DUŻO"
    }
}

data class DayLoadFinding(
    val dayOfWeek: Int,
    val exerciseCount: Int,
    val status: VolumeStatus,
    val recommendedMax: Int = 6
) {
    val polishDay: String get() = when (dayOfWeek) {
        1 -> "Poniedziałek"; 2 -> "Wtorek"; 3 -> "Środa"; 4 -> "Czwartek"
        5 -> "Piątek"; 6 -> "Sobota"; 7 -> "Niedziela"
        else -> "Dzień $dayOfWeek"
    }
}

data class AuditReport(
    val muscleFindings: List<MuscleFinding>,
    val dayLoad: List<DayLoadFinding>,
    val pushPull: PushPullRatio,
    val totalSets: Int,
    val problemCount: Int,
    val isPlanGood: Boolean
) {
    val problemsSummary: String
        get() {
            val low = muscleFindings.filter { it.status == VolumeStatus.LOW }
            val high = muscleFindings.filter { it.status == VolumeStatus.HIGH }
            val parts = mutableListOf<String>()
            if (low.isNotEmpty()) parts += "ZA MAŁO: ${low.joinToString { it.polishName }}"
            if (high.isNotEmpty()) parts += "ZA DUŻO: ${high.joinToString { it.polishName }}"
            if (!pushPull.isBalanced) parts += "Push:Pull niezbalansowany (${pushPull.ratioStr})"
            val overloaded = dayLoad.filter { it.status == VolumeStatus.HIGH }
            if (overloaded.isNotEmpty()) parts += "Za dużo ćwiczeń: ${overloaded.joinToString { it.polishDay }}"
            return parts.joinToString("; ").ifEmpty { "Brak istotnych problemów" }
        }
}

/**
 * Twarde reguły volume — bazują na konsensusie naukowym (Schoenfeld, Helms,
 * Israetel/RP). AI dostaje gotowe werdykty, nie ocenia samodzielnie.
 *
 * Liczby dobrane konserwatywnie dla średnio zaawansowanych. Dla początkujących
 * progi byłyby niższe (6-12), dla zaawansowanych wyższe (16-25).
 */
object PlanAuditEngine {

    /**
     * Progi setów/tydzień per partia, dla danego celu.
     *
     * HYPERTROPHY: 10-20 setów/partia/tydzień (Schoenfeld 2017 meta-analysis)
     * STRENGTH: niższe volume, wyższa intensywność (5-12 setów)
     * Inne cele (ENDURANCE, GENERAL) — używamy hipertrofii jako bezpiecznego defaultu.
     *
     * Uwaga: bark/biceps/triceps/łydki mają niższe górne progi — to MAŁE mięśnie,
     * dostają już volume z compoundów (OHP → bark, wyciskania → triceps,
     * podciągania → biceps). Bezpośrednie setów potrzebują mniej.
     */
    fun targetRangeFor(muscle: MuscleGroup, goal: TrainingGoal): IntRange {
        return when (goal) {
            TrainingGoal.STRENGTH -> when (muscle) {
                MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.QUADS -> 8..16
                MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES -> 6..12
                MuscleGroup.SHOULDERS, MuscleGroup.BICEPS, MuscleGroup.TRICEPS -> 5..10
                MuscleGroup.CALVES, MuscleGroup.CORE -> 3..8
                else -> 5..12
            }
            else -> when (muscle) {
                MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.QUADS -> 10..20
                MuscleGroup.HAMSTRINGS -> 8..16
                MuscleGroup.GLUTES -> 8..16
                MuscleGroup.SHOULDERS -> 8..15
                MuscleGroup.BICEPS, MuscleGroup.TRICEPS -> 6..12
                MuscleGroup.CALVES -> 6..12
                MuscleGroup.CORE -> 4..10
                else -> 8..15
            }
        }
    }

    fun audit(
        exercises: List<PlanExercise>,
        setsByPlanExerciseId: Map<Long, List<PlanExerciseSet>>,
        exerciseById: Map<Long, Exercise?>,
        goal: TrainingGoal
    ): AuditReport {
        val perMuscle = PlanVolumeCalculator.computePerMuscle(exercises, setsByPlanExerciseId, exerciseById)
        val muscleFindings = perMuscle.map { (muscle, sets) ->
            val range = targetRangeFor(muscle, goal)
            val status = when {
                sets < range.first -> VolumeStatus.LOW
                sets > range.last -> VolumeStatus.HIGH
                else -> VolumeStatus.OK
            }
            MuscleFinding(muscle, sets, range.first, range.last, status)
        }.sortedBy { it.muscle.ordinal }

        val perDay = PlanVolumeCalculator.computeExercisesPerDay(exercises)
        val dayLoad = perDay.map { (day, count) ->
            val status = when {
                count > 6 -> VolumeStatus.HIGH
                count == 0 -> VolumeStatus.LOW
                else -> VolumeStatus.OK
            }
            DayLoadFinding(day, count, status)
        }.sortedBy { it.dayOfWeek }

        val pushPull = PlanVolumeCalculator.computePushPull(perMuscle)

        val totalSets = perMuscle.values.sum()
        val problems = muscleFindings.count { it.status != VolumeStatus.OK } +
            dayLoad.count { it.status == VolumeStatus.HIGH } +
            (if (!pushPull.isBalanced) 1 else 0)
        val isGood = problems == 0

        return AuditReport(
            muscleFindings = muscleFindings,
            dayLoad = dayLoad,
            pushPull = pushPull,
            totalSets = totalSets,
            problemCount = problems,
            isPlanGood = isGood
        )
    }

    /** Buduje gotową sekcję promptu z deterministycznymi liczbami. */
    fun toPromptSection(report: AuditReport): String = buildString {
        append("# OBLICZONE VOLUME (deterministyczne — NIE LICZ SAMODZIELNIE, używaj tych liczb)\n")
        append("Łączne sety/tydzień: ${report.totalSets}\n")
        append("Push:Pull = ${report.pushPull.ratioStr} ")
        append(if (report.pushPull.isBalanced) "(zbalansowany ✅)\n" else "(NIEZBALANSOWANY — ratio >1.5×)\n")
        append("\n")

        append("## Sety per partia (z twardymi normami):\n")
        report.muscleFindings.forEach { f ->
            append("- ${f.polishName}: ${f.sets} setów (cel ${f.targetRange}) → ${f.statusEmoji}\n")
        }
        append("\n")

        append("## Liczba ćwiczeń per dzień (max 6 zalecane):\n")
        report.dayLoad.forEach { d ->
            val mark = if (d.status == VolumeStatus.HIGH) " ⚠️ ZA DUŻO" else ""
            append("- ${d.polishDay}: ${d.exerciseCount} ćwiczeń$mark\n")
        }
        append("\n")

        if (report.isPlanGood) {
            append("## WERDYKT ALGORYTMU: ✅ PLAN JEST DOBRY\n")
            append("Wszystkie partie w normie, balans OK, brak przeciążenia. ")
            append("**Twoja rola**: potwierdź że plan jest OK. NIE wymyślaj problemów. ")
            append("Możesz wskazać 1-2 mocne strony. Koniec.\n")
        } else {
            append("## WERDYKT ALGORYTMU: ${report.problemCount} problem(ów) do adresowania\n")
            append("${report.problemsSummary}\n")
            append("**Twoja rola**: dla KAŻDEGO problemu z listy wyżej zaproponuj KONKRETNĄ zmianę ")
            append("(nazwa ćwiczenia z biblioteki + ile setów). NIE wymyślaj problemów ")
            append("których algorytm nie zgłosił. NIE komentuj partii oznaczonych ✅.\n")
        }
    }
}
