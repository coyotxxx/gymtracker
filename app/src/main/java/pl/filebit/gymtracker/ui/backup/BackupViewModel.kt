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
import pl.filebit.gymtracker.ai.AiConfig
import pl.filebit.gymtracker.ai.AiPreferences
import pl.filebit.gymtracker.ai.AiProvider
import pl.filebit.gymtracker.data.db.AppDatabase
import pl.filebit.gymtracker.data.entity.AiChatMessageEntity
import pl.filebit.gymtracker.data.entity.AiConversation
import pl.filebit.gymtracker.data.entity.BodyMeasurement
import pl.filebit.gymtracker.data.entity.Equipment
import pl.filebit.gymtracker.data.entity.Exercise
import pl.filebit.gymtracker.data.entity.Goal
import pl.filebit.gymtracker.data.entity.GoalType
import pl.filebit.gymtracker.data.entity.GoalUnit
import pl.filebit.gymtracker.data.entity.MuscleGroup
import pl.filebit.gymtracker.data.entity.PhotoType
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.PlanExerciseSet
import pl.filebit.gymtracker.data.entity.ProgressPhoto
import pl.filebit.gymtracker.data.entity.SetType
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.entity.UserProfile
import pl.filebit.gymtracker.data.entity.Workout
import pl.filebit.gymtracker.data.entity.WorkoutSet
import pl.filebit.gymtracker.data.repository.ExerciseRepository
import pl.filebit.gymtracker.data.repository.UserProfileRepository
import pl.filebit.gymtracker.data.repository.WorkoutRepository
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@Serializable
data class BackupData(
    val version: Int = 2,
    val exportedAt: Long,
    val profile: ProfileDto?,
    val exercises: List<ExerciseDto>,
    val workouts: List<WorkoutDto>,
    val sets: List<SetDto>,
    // v2 — wszystko poniżej dodane, defaults dla wstecz kompatybilności
    val plans: List<PlanDto> = emptyList(),
    val planExercises: List<PlanExerciseDto> = emptyList(),
    val planSets: List<PlanSetDto> = emptyList(),
    val bodyMeasurements: List<BodyMeasurementDto> = emptyList(),
    val goals: List<GoalDto> = emptyList(),
    val progressPhotos: List<ProgressPhotoDto> = emptyList(),
    val aiPrefs: AiPrefsDto? = null,
    val aiConversations: List<AiConversationDto> = emptyList(),
    val aiMessages: List<AiMessageDto> = emptyList()
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
    val aiOverlayEnabled: Boolean = false,
    val displayName: String = ""
)

@Serializable
data class ExerciseDto(
    val id: Long, val name: String,
    val primaryMuscle: String, val equipment: String,
    val isCustom: Boolean, val notes: String,
    val description: String = "",
    val metricType: String = "WEIGHT_REPS"
)

@Serializable
data class WorkoutDto(
    val id: Long, val startedAt: Long,
    val finishedAt: Long?, val notes: String,
    val fromPlanId: Long? = null,
    val fromDayOfWeek: Int? = null,
    val aiSummary: String? = null,
    val aiSummaryGeneratedAt: Long? = null
)

@Serializable
data class SetDto(
    val id: Long, val workoutId: Long, val exerciseId: Long,
    val setNumber: Int, val orderIndex: Int,
    val reps: Int, val weightKg: Double,
    val isCompleted: Boolean,
    val setType: String = "NORMAL",
    val isWarmup: Boolean = false,
    val rpe: Int?, val createdAt: Long,
    val rir: Int? = null,
    val tempo: String? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null
)

@Serializable
data class PlanDto(
    val id: Long, val name: String,
    val daysOfWeek: List<Int>, val notes: String,
    val createdAt: Long, val createdByAi: Boolean = false
)

@Serializable
data class PlanExerciseDto(
    val id: Long, val planId: Long, val exerciseId: Long,
    val dayOfWeek: Int, val orderIndex: Int,
    val supersetGroup: String? = null
)

@Serializable
data class PlanSetDto(
    val id: Long, val planExerciseId: Long, val setNumber: Int,
    val reps: Int, val weightKg: Double? = null,
    val restSeconds: Int? = null,
    val setType: String = "NORMAL",
    val rpe: Int? = null, val rir: Int? = null,
    val tempo: String? = null,
    val durationSec: Int? = null, val distanceM: Double? = null
)

@Serializable
data class BodyMeasurementDto(
    val id: Long, val date: Long,
    val weightKg: Double? = null, val chestCm: Double? = null,
    val waistCm: Double? = null, val hipsCm: Double? = null,
    val armCm: Double? = null, val thighCm: Double? = null,
    val calfCm: Double? = null, val bodyFatPercent: Double? = null,
    val notes: String = "", val createdAt: Long = 0L
)

@Serializable
data class GoalDto(
    val id: Long, val type: String, val title: String,
    val description: String, val unit: String,
    val startValue: Double, val targetValue: Double,
    val currentValue: Double? = null,
    val startDate: Long, val deadline: Long,
    val achieved: Boolean, val achievedAt: Long? = null,
    val createdAt: Long, val exerciseId: Long? = null
)

@Serializable
data class ProgressPhotoDto(
    val id: Long, val date: Long,
    val photoType: String, val filename: String,
    val notes: String = "", val createdAt: Long = 0L
)

/**
 * Ustawienia AI. Pole apiKey jest nullable — domyślnie NULL (eksport bez klucza).
 * Eksport z włączonym togglem "Dołącz klucz API AI" wpisuje go w plain text.
 * To ryzykowne (klucz w pliku JSON), więc default off.
 */
@Serializable
data class AiPrefsDto(
    val provider: String,
    val model: String,
    val systemPrompt: String,
    val apiKey: String? = null
)

@Serializable
data class AiConversationDto(
    val id: Long, val title: String,
    val createdAt: Long, val updatedAt: Long
)

@Serializable
data class AiMessageDto(
    val id: Long, val conversationId: Long,
    val role: String, val text: String,
    val applied: Boolean, val createdAt: Long
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workoutRepo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
    private val profileRepo: UserProfileRepository,
    private val aiPrefs: AiPreferences,
    private val db: AppDatabase,
    private val exerciseSeeder: pl.filebit.gymtracker.data.seed.ExerciseSeeder
) : ViewModel() {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun export(uri: Uri, includeApiKey: Boolean = false) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val allWorkouts = db.workoutDao().observeAll().first()
                val allSets = mutableListOf<WorkoutSet>().apply {
                    for (w in allWorkouts) addAll(workoutRepo.getSetsForWorkout(w.id))
                }
                val allExercises = db.exerciseDao().observeAll().first()
                val profile = profileRepo.get()
                val allPlans = db.trainingPlanDao().getAll()
                val allPlanExercises = mutableListOf<PlanExercise>().apply {
                    for (p in allPlans) addAll(db.planExerciseDao().getForPlan(p.id))
                }
                val allPlanSets = mutableListOf<PlanExerciseSet>().apply {
                    for (pe in allPlanExercises) addAll(db.planExerciseSetDao().getForPlanExercise(pe.id))
                }
                val allBody = db.bodyMeasurementDao().getAllAsc()
                val allGoals = db.goalDao().observeAll().first()
                val allPhotos = db.progressPhotoDao().observeAll().first()
                val aiCfg = aiPrefs.load()
                val allConversations = db.aiConversationDao().observeAll().first()
                val allMessages = mutableListOf<AiChatMessageEntity>().apply {
                    for (c in allConversations) addAll(db.aiChatMessageDao().getForConversation(c.id))
                }

                val data = BackupData(
                    version = 2,
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
                        aiOverlayEnabled = profile.aiOverlayEnabled,
                        displayName = profile.displayName
                    ),
                    exercises = allExercises.map {
                        ExerciseDto(
                            id = it.id, name = it.name,
                            primaryMuscle = it.primaryMuscle.name,
                            equipment = it.equipment.name,
                            isCustom = it.isCustom, notes = it.notes,
                            description = it.description,
                            metricType = it.metricType.name
                        )
                    },
                    workouts = allWorkouts.map {
                        WorkoutDto(it.id, it.startedAt, it.finishedAt, it.notes,
                            it.fromPlanId, it.fromDayOfWeek,
                            it.aiSummary, it.aiSummaryGeneratedAt)
                    },
                    sets = allSets.map {
                        SetDto(
                            id = it.id, workoutId = it.workoutId, exerciseId = it.exerciseId,
                            setNumber = it.setNumber, orderIndex = it.orderIndex,
                            reps = it.reps, weightKg = it.weightKg,
                            isCompleted = it.isCompleted,
                            setType = it.setType.name,
                            isWarmup = it.setType == SetType.WARMUP,
                            rpe = it.rpe, createdAt = it.createdAt,
                            rir = it.rir, tempo = it.tempo,
                            durationSec = it.durationSec, distanceM = it.distanceM
                        )
                    },
                    plans = allPlans.map {
                        PlanDto(it.id, it.name, it.daysOfWeek, it.notes, it.createdAt, it.createdByAi)
                    },
                    planExercises = allPlanExercises.map {
                        PlanExerciseDto(it.id, it.planId, it.exerciseId, it.dayOfWeek, it.orderIndex, it.supersetGroup)
                    },
                    planSets = allPlanSets.map {
                        PlanSetDto(it.id, it.planExerciseId, it.setNumber, it.reps, it.weightKg,
                            it.restSeconds, it.setType.name, it.rpe, it.rir, it.tempo,
                            it.durationSec, it.distanceM)
                    },
                    bodyMeasurements = allBody.map {
                        BodyMeasurementDto(it.id, it.date, it.weightKg, it.chestCm, it.waistCm,
                            it.hipsCm, it.armCm, it.thighCm, it.calfCm, it.bodyFatPercent,
                            it.notes, it.createdAt)
                    },
                    goals = allGoals.map {
                        GoalDto(it.id, it.type.name, it.title, it.description, it.unit.name,
                            it.startValue, it.targetValue, it.currentValue,
                            it.startDate, it.deadline, it.achieved, it.achievedAt,
                            it.createdAt, it.exerciseId)
                    },
                    progressPhotos = allPhotos.map {
                        ProgressPhotoDto(it.id, it.date, it.photoType.name, it.filename, it.notes, it.createdAt)
                    },
                    aiPrefs = AiPrefsDto(
                        provider = aiCfg.provider.name,
                        model = aiCfg.model,
                        systemPrompt = aiCfg.systemPrompt,
                        apiKey = if (includeApiKey && aiCfg.apiKey.isNotBlank()) aiCfg.apiKey else null
                    ),
                    aiConversations = allConversations.map {
                        AiConversationDto(it.id, it.title, it.createdAt, it.updatedAt)
                    },
                    aiMessages = allMessages.map {
                        AiMessageDto(it.id, it.conversationId, it.role, it.text, it.applied, it.createdAt)
                    }
                )

                val text = json.encodeToString(data)
                val photosDir = File(context.filesDir, "progress_photos")
                val photoFiles = allPhotos.mapNotNull { p ->
                    val f = File(photosDir, p.filename)
                    if (f.exists()) f else null
                }

                // ZIP: data.json + photos/{filename}
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    ZipOutputStream(os).use { zip ->
                        zip.putNextEntry(ZipEntry("data.json"))
                        zip.write(text.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                        for (f in photoFiles) {
                            zip.putNextEntry(ZipEntry("photos/${f.name}"))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
                _status.value = "Eksport: ${allWorkouts.size} treningów, " +
                    "${allPlans.size} planów, ${allConversations.size} rozmów AI, " +
                    "${photoFiles.size} zdjęć"
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
                try {
                    runImport(uri)
                } catch (e: Exception) {
                    android.util.Log.e("BackupVM", "Import failed", e)
                    val msg = when {
                        e.message?.contains("FOREIGN KEY") == true ->
                            "Błąd importu: brak ćwiczeń w bazie. Zrestartuj aplikację i spróbuj ponownie."
                        e.message?.contains("kotlinx.serialization") == true ||
                            e.message?.contains("decodeFromString") == true ||
                            e is kotlinx.serialization.SerializationException ->
                            "Błąd importu: niepoprawny format pliku JSON."
                        else ->
                            "Błąd importu: ${e.message ?: e::class.simpleName ?: "nieznany"}"
                    }
                    _status.value = msg
                }
            }
        }
    }

    private suspend fun runImport(uri: Uri) {
        // FIX v0.89.9: wymusza wykonanie ExerciseSeeder przed importem.
        // Bez tego po świeżej instalacji / wipe baza może być pusta — wstawianie
        // WorkoutSet z exerciseId pada na FK constraint (Exercise(id) nie istnieje).
        exerciseSeeder.seedIfEmpty()

        // Wykryj typ pliku: ZIP (PK\x03\x04) vs JSON. Czytamy bajty raz —
        // contentResolver może nie pozwolić na drugie otwarcie, więc
        // buforujemy całość w pamięci.
        val rawBytes = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes()
        } ?: return

                val isZip = rawBytes.size >= 4 &&
                    rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte() &&
                    rawBytes[2] == 0x03.toByte() && rawBytes[3] == 0x04.toByte()

                val text = if (isZip) {
                    var jsonText: String? = null
                    val photosDir = File(context.filesDir, "progress_photos")
                    if (!photosDir.exists()) photosDir.mkdirs()
                    ZipInputStream(rawBytes.inputStream()).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            when {
                                name == "data.json" -> {
                                    jsonText = zip.readBytes().toString(Charsets.UTF_8)
                                }
                                name.startsWith("photos/") && !entry.isDirectory -> {
                                    val safe = name.removePrefix("photos/")
                                        .substringAfterLast('/')
                                    if (safe.isNotBlank()) {
                                        File(photosDir, safe).outputStream().use { os ->
                                            zip.copyTo(os)
                                        }
                                    }
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                    jsonText ?: return@withContext
                } else {
                    rawBytes.toString(Charsets.UTF_8)
                }

                val data = json.decodeFromString<BackupData>(text)

                val exDao = db.exerciseDao()
                val wDao = db.workoutDao()
                val sDao = db.workoutSetDao()
                val pDao = db.userProfileDao()
                val planDao = db.trainingPlanDao()
                val peDao = db.planExerciseDao()
                val pesDao = db.planExerciseSetDao()
                val bDao = db.bodyMeasurementDao()
                val gDao = db.goalDao()
                val phDao = db.progressPhotoDao()
                val convDao = db.aiConversationDao()
                val msgDao = db.aiChatMessageDao()

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
                            aiOverlayEnabled = p.aiOverlayEnabled,
                            displayName = p.displayName
                        )
                    )
                }

                // Exercises - tylko niestandardowe (seed jest auto)
                for (ex in data.exercises.filter { it.isCustom }) {
                    exDao.upsert(
                        Exercise(
                            id = ex.id, name = ex.name,
                            primaryMuscle = runCatching { MuscleGroup.valueOf(ex.primaryMuscle) }
                                .getOrDefault(MuscleGroup.OTHER),
                            equipment = runCatching { Equipment.valueOf(ex.equipment) }
                                .getOrDefault(Equipment.OTHER),
                            isCustom = true,
                            notes = ex.notes,
                            description = ex.description,
                            metricType = runCatching {
                                pl.filebit.gymtracker.data.entity.MetricType.valueOf(ex.metricType)
                            }.getOrDefault(pl.filebit.gymtracker.data.entity.MetricType.WEIGHT_REPS)
                        )
                    )
                }

                // Workouts + sets
                for (w in data.workouts) {
                    wDao.insert(
                        Workout(
                            id = w.id, startedAt = w.startedAt, finishedAt = w.finishedAt,
                            fromPlanId = w.fromPlanId, fromDayOfWeek = w.fromDayOfWeek,
                            notes = w.notes,
                            aiSummary = w.aiSummary,
                            aiSummaryGeneratedAt = w.aiSummaryGeneratedAt
                        )
                    )
                }
                for (s in data.sets) {
                    sDao.insert(
                        WorkoutSet(
                            id = s.id, workoutId = s.workoutId, exerciseId = s.exerciseId,
                            setNumber = s.setNumber, orderIndex = s.orderIndex,
                            reps = s.reps, weightKg = s.weightKg, isCompleted = s.isCompleted,
                            setType = if (s.setType.isNotBlank()) SetType.safeValueOf(s.setType)
                                else if (s.isWarmup) SetType.WARMUP else SetType.NORMAL,
                            rpe = s.rpe, createdAt = s.createdAt,
                            rir = s.rir,
                            tempo = s.tempo,
                            durationSec = s.durationSec,
                            distanceM = s.distanceM
                        )
                    )
                }

                // Plans + plan exercises + plan sets
                for (p in data.plans) {
                    planDao.upsert(
                        TrainingPlan(
                            id = p.id, name = p.name, daysOfWeek = p.daysOfWeek,
                            notes = p.notes, createdAt = p.createdAt, createdByAi = p.createdByAi
                        )
                    )
                }
                for (pe in data.planExercises) {
                    peDao.upsert(
                        PlanExercise(
                            id = pe.id, planId = pe.planId, exerciseId = pe.exerciseId,
                            dayOfWeek = pe.dayOfWeek, orderIndex = pe.orderIndex,
                            supersetGroup = pe.supersetGroup
                        )
                    )
                }
                for (ps in data.planSets) {
                    pesDao.upsert(
                        PlanExerciseSet(
                            id = ps.id, planExerciseId = ps.planExerciseId,
                            setNumber = ps.setNumber, reps = ps.reps,
                            weightKg = ps.weightKg, restSeconds = ps.restSeconds,
                            setType = SetType.safeValueOf(ps.setType),
                            rpe = ps.rpe, rir = ps.rir, tempo = ps.tempo,
                            durationSec = ps.durationSec, distanceM = ps.distanceM
                        )
                    )
                }

                // Body measurements
                for (b in data.bodyMeasurements) {
                    bDao.upsert(
                        BodyMeasurement(
                            id = b.id, date = b.date,
                            weightKg = b.weightKg, chestCm = b.chestCm,
                            waistCm = b.waistCm, hipsCm = b.hipsCm,
                            armCm = b.armCm, thighCm = b.thighCm,
                            calfCm = b.calfCm, bodyFatPercent = b.bodyFatPercent,
                            notes = b.notes,
                            createdAt = if (b.createdAt > 0) b.createdAt else System.currentTimeMillis()
                        )
                    )
                }

                // Goals
                for (g in data.goals) {
                    gDao.upsert(
                        Goal(
                            id = g.id,
                            type = runCatching { GoalType.valueOf(g.type) }.getOrDefault(GoalType.CUSTOM),
                            title = g.title, description = g.description,
                            unit = runCatching { GoalUnit.valueOf(g.unit) }.getOrDefault(GoalUnit.CUSTOM),
                            startValue = g.startValue, targetValue = g.targetValue,
                            currentValue = g.currentValue,
                            startDate = g.startDate, deadline = g.deadline,
                            achieved = g.achieved, achievedAt = g.achievedAt,
                            createdAt = g.createdAt, exerciseId = g.exerciseId
                        )
                    )
                }

                // Progress photos (tylko metadata — pliki .jpg zostają w cache, gdy
                // usunięte przez czyszczenie aplikacji odzyskanie nie jest możliwe)
                for (ph in data.progressPhotos) {
                    phDao.upsert(
                        ProgressPhoto(
                            id = ph.id, date = ph.date,
                            photoType = runCatching { PhotoType.valueOf(ph.photoType) }
                                .getOrDefault(PhotoType.FRONT),
                            filename = ph.filename, notes = ph.notes,
                            createdAt = if (ph.createdAt > 0) ph.createdAt else System.currentTimeMillis()
                        )
                    )
                }

                // AI prefs. apiKey: jeśli backup zawiera klucz (toggle "Dołącz klucz")
                // wgrywamy go; w przeciwnym razie zachowujemy obecny lokalny klucz.
                data.aiPrefs?.let { ap ->
                    val current = aiPrefs.load()
                    aiPrefs.save(
                        AiConfig(
                            provider = runCatching { AiProvider.valueOf(ap.provider) }
                                .getOrDefault(AiProvider.ANTHROPIC),
                            apiKey = ap.apiKey?.takeIf { it.isNotBlank() } ?: current.apiKey,
                            model = ap.model,
                            systemPrompt = ap.systemPrompt
                        )
                    )
                }

                // AI conversations + messages
                for (c in data.aiConversations) {
                    convDao.upsert(
                        AiConversation(id = c.id, title = c.title,
                            createdAt = c.createdAt, updatedAt = c.updatedAt)
                    )
                }
                for (m in data.aiMessages) {
                    msgDao.upsert(
                        AiChatMessageEntity(
                            id = m.id, conversationId = m.conversationId,
                            role = m.role, text = m.text,
                            applied = m.applied, createdAt = m.createdAt
                        )
                    )
                }

        _status.value = "Import: ${data.workouts.size} treningów, " +
            "${data.plans.size} planów, ${data.aiConversations.size} rozmów AI"
    }

    fun clearStatus() { _status.value = null }

    /**
     * Pełne wyczyszczenie: wszystkie tabele Room + ustawienia AI (klucz, prompt).
     * Profil zostaje zresetowany do domyślnego przy kolejnym `pDao.upsert()` w UI.
     */
    fun wipeAll(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.clearAllTables()
                aiPrefs.clear()
                // FIX v0.89.9: po wyczyszczeniu DB seed nie uruchamia się sam
                // (działa tylko na fresh install). Wymuszamy by baza nie była
                // pusta — bez tego import po wipe pada na FK constraint.
                exerciseSeeder.seedIfEmpty()
                _status.value = "Wyczyszczono wszystkie dane"
            }
            onDone()
        }
    }
}
