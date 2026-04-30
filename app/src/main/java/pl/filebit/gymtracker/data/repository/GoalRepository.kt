package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.flow.Flow
import pl.filebit.gymtracker.data.db.dao.GoalDao
import pl.filebit.gymtracker.data.db.dao.WorkoutSetDao
import pl.filebit.gymtracker.data.entity.Goal
import pl.filebit.gymtracker.data.entity.GoalType
import pl.filebit.gymtracker.data.entity.SetType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class GoalProgress(
    val goal: Goal,
    val currentValue: Double,             // aktualnie odnotowane (z bazy lub goal.currentValue)
    val deltaSoFar: Double,               // ile zrobione (kg / km / min)
    val deltaTotal: Double,               // ile docelowo (cały zakres)
    val percentDone: Int,                 // 0..100+
    val daysElapsed: Int,
    val daysTotal: Int,
    val daysRemaining: Int,
    val onTrack: Boolean,                 // czy idzie zgodnie z trendem linearnym
    val pacePercent: Int,                 // 100 = idziesz zgodnie z planem; >100 = wyprzedzasz
    val achieved: Boolean
)

@Singleton
class GoalRepository @Inject constructor(
    private val dao: GoalDao,
    private val bodyRepo: BodyRepository,
    private val setDao: WorkoutSetDao,
    private val statsRepo: StatsRepository
) {
    fun observeAll(): Flow<List<Goal>> = dao.observeAll()
    suspend fun getActive(): List<Goal> = dao.getActive()
    suspend fun getById(id: Long): Goal? = dao.getById(id)
    suspend fun upsert(goal: Goal): Long = dao.upsert(goal)
    suspend fun delete(goal: Goal) = dao.delete(goal)
    suspend fun deleteById(id: Long) = dao.deleteById(id)

    /**
     * Liczy bieżący postęp celu — pobiera aktualną wartość z auto-source
     * (np. waga ciała z BodyMeasurement, 1RM ze statów dla ćwiczenia).
     */
    suspend fun computeProgress(goal: Goal): GoalProgress {
        val now = System.currentTimeMillis()
        val daysTotal = ((goal.deadline - goal.startDate) / DAY).toInt().coerceAtLeast(1)
        val daysElapsed = ((now - goal.startDate) / DAY).toInt().coerceIn(0, daysTotal)
        val daysRemaining = (daysTotal - daysElapsed).coerceAtLeast(0)

        val currentValue = goal.currentValue ?: computeCurrentValue(goal)

        val deltaSoFar = currentValue - goal.startValue
        val deltaTotal = goal.targetValue - goal.startValue

        // postęp procentowy (znak zachowuje kierunek)
        val percent = if (deltaTotal == 0.0) 100 else
            ((deltaSoFar / deltaTotal) * 100).roundToInt()

        // tempo: gdyby zrobione było 50% w 50% czasu = onTrack=true
        val expectedPercent = if (daysTotal == 0) 100 else
            (daysElapsed * 100) / daysTotal
        val pacePercent = if (expectedPercent == 0) 100
        else ((percent.toFloat() / expectedPercent) * 100).roundToInt()
        val onTrack = pacePercent >= 90    // 90% expected = OK

        // achieved?
        val direction = if (deltaTotal >= 0) 1 else -1
        val achieved = (currentValue * direction) >= (goal.targetValue * direction)

        return GoalProgress(
            goal = goal,
            currentValue = currentValue,
            deltaSoFar = deltaSoFar,
            deltaTotal = deltaTotal,
            percentDone = percent,
            daysElapsed = daysElapsed,
            daysTotal = daysTotal,
            daysRemaining = daysRemaining,
            onTrack = onTrack,
            pacePercent = pacePercent,
            achieved = achieved
        )
    }

    private suspend fun computeCurrentValue(goal: Goal): Double {
        return when (goal.type) {
            GoalType.LOSE_WEIGHT, GoalType.GAIN_MASS ->
                bodyRepo.getLatest()?.weightKg ?: goal.startValue
            GoalType.INCREASE_STRENGTH -> {
                val exId = goal.exerciseId ?: return goal.startValue
                val sets = setDao.getAllForExercise(exId)
                    .filter { it.isCompleted && it.setType != SetType.WARMUP }
                sets.maxOfOrNull { statsRepo.epley1RM(it.weightKg, it.reps) }
                    ?: goal.startValue
            }
            else -> goal.startValue
        }
    }

    suspend fun computeAllActiveProgress(): List<GoalProgress> =
        getActive().map { computeProgress(it) }

    companion object {
        private const val DAY = 24 * 60 * 60 * 1000L
    }
}
