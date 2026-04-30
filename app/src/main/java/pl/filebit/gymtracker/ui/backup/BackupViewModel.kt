package pl.filebit.gymtracker.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import pl.filebit.gymtracker.data.db.AppDatabase
import javax.inject.Inject

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long,
    val profile: ProfileDto?,
    val exercises: List<ExerciseDto>,
    val workouts: List<WorkoutDto>,
    val sets: List<SetDto>
)

@Serializable
data class ProfileDto(
    val goal: String, val experience: String,
    val daysPerWeek: Int, val sessionMinutes: Int,
    val preferredUnit: String, val defaultRestSeconds: Int,
    val injuriesNotes: String,
    val showAdvancedSetFields: Boolean = false,
    val weightGoalType: String = "NONE",
    val targetWeightKg: Double? = null,
    val unfinishedWorkoutNotifyEnabled: Boolean = true,
    val unfinishedWorkoutNotifyHours: Int = 3,
    val gender: String = "MALE",
    val bodyweightKg: Double? = null,
    val flashOnTimerEnd: Boolean = false,
    val aiOverlayEnabled: Boolean = false
)

@Serializable
data class ExerciseDto(
    val id: Long, val name: String,
    val primaryMuscle: String, val equipment: String,
    val isCustom: Boolean, val notes: String
)

@Serializable
data class WorkoutDto(
    val id: Long, val startedAt: Long,
    val finishedAt: Long?, val notes: String
)

@Serializable
data class SetDto(
    val id: Long, val workoutId: Long, val exerciseId: Long,
    val setNumber: Int, val orderIndex: Int,
    val reps: Int, val weightKg: Double,
    val isCompleted: Boolean,
    val setType: String = "NORMAL",
    // legacy field (stary backup) — zachowane dla kompatybilności wstecz
    val isWarmup: Boolean = false,
    val rpe: Int?, val createdAt: Long
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workoutRepo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
    private val profileRepo: UserProfileRepository,
    private val db: AppDatabase
) : ViewModel() {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun export(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val workouts = mutableListOf<Workout>()
                val sets = mutableListOf<WorkoutSet>()
                val exercises = mutableListOf<Exercise>()

                // Pobierz wszystkie dane jednorazowo (snapshot)
                val allWorkouts = db.workoutDao().observeAll().first()
                workouts.addAll(allWorkouts)
                for (w in allWorkouts) sets.addAll(workoutRepo.getSetsForWorkout(w.id))

                val allExercises = db.exerciseDao().observeAll().first()
                exercises.addAll(allExercises)

                val profile = profileRepo.get()

                val data = BackupData(
                    exportedAt = System.currentTimeMillis(),
                    profile = ProfileDto(
                        goal = profile.goal.name,
                        experience = profile.experience.name,
                        daysPerWeek = profile.daysPerWeek,
                        sessionMinutes = profile.sessionMinutes,
                        preferredUnit = profile.preferredUnit.name,
                        defaultRestSeconds = profile.defaultRestSeconds,
                        injuriesNotes = profile.injuriesNotes,
                        showAdvancedSetFields = profile.showAdvancedSetFields,
                        weightGoalType = profile.weightGoalType.name,
                        targetWeightKg = profile.targetWeightKg,
                        unfinishedWorkoutNotifyEnabled = profile.unfinishedWorkoutNotifyEnabled,
                        unfinishedWorkoutNotifyHours = profile.unfinishedWorkoutNotifyHours,
                        gender = profile.gender.name,
                        bodyweightKg = profile.bodyweightKg,
                        flashOnTimerEnd = profile.flashOnTimerEnd,
                        aiOverlayEnabled = profile.aiOverlayEnabled
                    ),
                    exercises = exercises.map {
                        ExerciseDto(
                            it.id, it.name,
                            it.primaryMuscle.name, it.equipment.name,
                            it.isCustom, it.notes
                        )
                    },
                    workouts = workouts.map {
                        WorkoutDto(it.id, it.startedAt, it.finishedAt, it.notes)
                    },
                    sets = sets.map {
                        SetDto(
                            it.id, it.workoutId, it.exerciseId,
                            it.setNumber, it.orderIndex,
                            it.reps, it.weightKg,
                            it.isCompleted,
                            setType = it.setType.name,
                            isWarmup = it.setType == pl.filebit.gymtracker.data.entity.SetType.WARMUP,
                            it.rpe, it.createdAt
                        )
                    }
                )

                val text = json.encodeToString(data)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(text.toByteArray(Charsets.UTF_8))
                }
                _status.value = "Eksport: ${workouts.size} treningów, ${sets.size} serii"
            }
        }
    }

    /**
     * Eksport CSV — jeden wiersz per WorkoutSet, JOIN z workout (data) i ćwiczeniem (nazwa).
     */
    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val workouts = db.workoutDao().observeAll().first()
                val exMap = db.exerciseDao().observeAll().first().associateBy { it.id }
                val sb = StringBuilder()
                sb.append("workout_id,workout_started_at,workout_finished_at,exercise,set_number,set_type,reps,weight_kg,is_completed\n")
                for (w in workouts) {
                    val sets = workoutRepo.getSetsForWorkout(w.id)
                        .sortedWith(compareBy({ it.orderIndex }, { it.setNumber }))
                    for (s in sets) {
                        val exName = exMap[s.exerciseId]?.name ?: "?"
                        sb.append(w.id).append(',')
                        sb.append(w.startedAt).append(',')
                        sb.append(w.finishedAt ?: "").append(',')
                        sb.append('"').append(exName.replace("\"", "\"\"")).append('"').append(',')
                        sb.append(s.setNumber).append(',')
                        sb.append(s.setType.name).append(',')
                        sb.append(s.reps).append(',')
                        sb.append(s.weightKg).append(',')
                        sb.append(if (s.isCompleted) "1" else "0").append('\n')
                    }
                }
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(sb.toString().toByteArray(Charsets.UTF_8))
                }
                _status.value = "CSV: ${workouts.size} treningów"
            }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val text = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: return@withContext

                val data = json.decodeFromString<BackupData>(text)

                // Restoring: wstawiamy customowe ćwiczenia, wszystkie workouts i sets
                // Zachowujemy ID dla relacji
                val exDao = db.exerciseDao()
                val wDao = db.workoutDao()
                val sDao = db.workoutSetDao()
                val pDao = db.userProfileDao()

                // Profile
                data.profile?.let { p ->
                    pDao.upsert(
                        UserProfile(
                            goal = runCatching { pl.filebit.gymtracker.data.entity.TrainingGoal.valueOf(p.goal) }
                                .getOrDefault(pl.filebit.gymtracker.data.entity.TrainingGoal.HYPERTROPHY),
                            experience = runCatching { pl.filebit.gymtracker.data.entity.ExperienceLevel.valueOf(p.experience) }
                                .getOrDefault(pl.filebit.gymtracker.data.entity.ExperienceLevel.INTERMEDIATE),
                            daysPerWeek = p.daysPerWeek,
                            sessionMinutes = p.sessionMinutes,
                            preferredUnit = runCatching { pl.filebit.gymtracker.data.entity.WeightUnit.valueOf(p.preferredUnit) }
                                .getOrDefault(pl.filebit.gymtracker.data.entity.WeightUnit.KG),
                            defaultRestSeconds = p.defaultRestSeconds,
                            injuriesNotes = p.injuriesNotes,
                            showAdvancedSetFields = p.showAdvancedSetFields,
                            weightGoalType = runCatching { pl.filebit.gymtracker.data.entity.WeightGoalType.valueOf(p.weightGoalType) }
                                .getOrDefault(pl.filebit.gymtracker.data.entity.WeightGoalType.NONE),
                            targetWeightKg = p.targetWeightKg,
                            unfinishedWorkoutNotifyEnabled = p.unfinishedWorkoutNotifyEnabled,
                            unfinishedWorkoutNotifyHours = p.unfinishedWorkoutNotifyHours,
                            gender = runCatching { pl.filebit.gymtracker.data.entity.Gender.valueOf(p.gender) }
                                .getOrDefault(pl.filebit.gymtracker.data.entity.Gender.MALE),
                            bodyweightKg = p.bodyweightKg,
                            flashOnTimerEnd = p.flashOnTimerEnd,
                            aiOverlayEnabled = p.aiOverlayEnabled
                        )
                    )
                }

                // Exercises - wstawiamy tylko niestandardowe (seed już jest)
                val customExercises = data.exercises.filter { it.isCustom }
                for (ex in customExercises) {
                    exDao.upsert(
                        Exercise(
                            id = ex.id,
                            name = ex.name,
                            primaryMuscle = runCatching { MuscleGroup.valueOf(ex.primaryMuscle) }
                                .getOrDefault(MuscleGroup.OTHER),
                            equipment = runCatching { Equipment.valueOf(ex.equipment) }
                                .getOrDefault(Equipment.OTHER),
                            isCustom = true,
                            notes = ex.notes
                        )
                    )
                }

                // Workouts + sets
                for (w in data.workouts) {
                    wDao.insert(
                        Workout(
                            id = w.id,
                            startedAt = w.startedAt,
                            finishedAt = w.finishedAt,
                            fromPlanId = null,
                            fromDayOfWeek = null,
                            notes = w.notes
                        )
                    )
                }
                for (s in data.sets) {
                    sDao.insert(
                        WorkoutSet(
                            id = s.id,
                            workoutId = s.workoutId,
                            exerciseId = s.exerciseId,
                            setNumber = s.setNumber,
                            orderIndex = s.orderIndex,
                            reps = s.reps,
                            weightKg = s.weightKg,
                            isCompleted = s.isCompleted,
                            setType = if (s.setType.isNotBlank()) {
                                pl.filebit.gymtracker.data.entity.SetType.safeValueOf(s.setType)
                            } else if (s.isWarmup) {
                                pl.filebit.gymtracker.data.entity.SetType.WARMUP
                            } else pl.filebit.gymtracker.data.entity.SetType.NORMAL,
                            rpe = s.rpe,
                            createdAt = s.createdAt
                        )
                    )
                }

                _status.value = "Import: ${data.workouts.size} treningów, ${data.sets.size} serii"
            }
        }
    }

    fun clearStatus() { _status.value = null }
}
