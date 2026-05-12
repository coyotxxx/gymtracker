package pl.filebit.gymtracker.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
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
import pl.filebit.gymtracker.ai.detectInjuryFromWorkout
import pl.filebit.gymtracker.ui.backup.BackupData
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wynik importu — gotowy do wyświetlenia (toUserMessage) lub do dalszej obróbki.
 */
data class BackupImportResult(
    val workouts: Int,
    val plans: Int,
    val aiConversations: Int,
    val skippedSets: Int,
    val isDietBackup: Boolean = false,
    val dietSummary: String? = null
) {
    fun toUserMessage(): String {
        if (isDietBackup) return dietSummary ?: "Zaimportowano dane diety."
        val base = "Import: $workouts treningów, $plans planów, $aiConversations rozmów AI"
        return if (skippedSets > 0) "$base ($skippedSets serii pominiętych — brak ćwiczenia w bazie)" else base
    }
}

/**
 * Wspólna logika importu backupu (treningowego lub dietetycznego).
 * Wynik dostępny dla BackupViewModel (UI Backup) oraz DebugViewModel (1-tap import).
 *
 * @see BackupImportResult
 */
@Singleton
class BackupImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val aiPrefs: AiPreferences,
    private val exerciseSeeder: pl.filebit.gymtracker.data.seed.ExerciseSeeder,
    private val dietBackupManager: DietBackupManager,
    // v1.24.7: po imporcie odpal backfill mesocykli (zalecenie aplikacji
    // "utworzy się gdy ≥4 treningi" w realu nie działa bez tego wywołania)
    private val mesocycleBackfillService: pl.filebit.gymtracker.data.repository.MesocycleBackfillService
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Import z URI (content://, file://, dowolny).
     */
    suspend fun importFromUri(uri: Uri): BackupImportResult {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Nie można odczytać pliku")
        return importBytes(rawBytes)
    }

    /**
     * Import z pliku — używane przez Debug 1-klik (omija file picker).
     */
    suspend fun importFromFile(file: File): BackupImportResult {
        if (!file.exists()) error("Plik nie istnieje: ${file.absolutePath}")
        val rawBytes = file.readBytes()
        return importBytes(rawBytes)
    }

    private suspend fun importBytes(rawBytes: ByteArray): BackupImportResult {
        // FIX v0.89.9: wymusza wykonanie ExerciseSeeder przed importem.
        exerciseSeeder.seedIfEmpty()

        val isZip = rawBytes.size >= 4 &&
            rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte() &&
            rawBytes[2] == 0x03.toByte() && rawBytes[3] == 0x04.toByte()

        val text = if (isZip) extractJsonFromZip(rawBytes) else rawBytes.toString(Charsets.UTF_8)

        // v1.10.1: smart detection — czy to plik treningowy czy dietetyczny?
        val looksLikeDietBackup = (
            text.contains("\"userDietProfile\"") ||
            text.contains("\"mealEntries\"") ||
            text.contains("\"recoveryLogs\"")
        ) && !text.contains("\"workouts\"")

        if (looksLikeDietBackup) {
            val summary = dietBackupManager.importFromText(text)
            return BackupImportResult(
                workouts = 0,
                plans = 0,
                aiConversations = 0,
                skippedSets = 0,
                isDietBackup = true,
                dietSummary = summary.toUserMessage()
            )
        }

        val data = json.decodeFromString<BackupData>(text)
        return applyBackupData(data)
    }

    private fun extractJsonFromZip(rawBytes: ByteArray): String {
        var jsonText: String? = null
        val photosDir = File(context.filesDir, "progress_photos")
        if (!photosDir.exists()) photosDir.mkdirs()
        ZipInputStream(rawBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "data.json" -> jsonText = zip.readBytes().toString(Charsets.UTF_8)
                    name.startsWith("photos/") && !entry.isDirectory -> {
                        val safe = name.removePrefix("photos/").substringAfterLast('/')
                        if (safe.isNotBlank()) {
                            File(photosDir, safe).outputStream().use { os -> zip.copyTo(os) }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return jsonText ?: error("Brak data.json w ZIP")
    }

    private suspend fun applyBackupData(data: BackupData): BackupImportResult {
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

        // REMAPPING ID ćwiczeń: plik → baza (FIX v0.89.10)
        val existingByName: Map<String, Long> = exDao.getAll().associate { it.name.lowercase() to it.id }
        val exerciseIdMap: MutableMap<Long, Long> = mutableMapOf()

        // Krok 1: ćwiczenia obecne w pliku
        for (ex in data.exercises) {
            val muscle = runCatching { MuscleGroup.valueOf(ex.primaryMuscle) }.getOrDefault(MuscleGroup.OTHER)
            val equip = runCatching { Equipment.valueOf(ex.equipment) }.getOrDefault(Equipment.OTHER)
            val metric = runCatching { pl.filebit.gymtracker.data.entity.MetricType.valueOf(ex.metricType) }
                .getOrDefault(pl.filebit.gymtracker.data.entity.MetricType.WEIGHT_REPS)

            val existingId = existingByName[ex.name.lowercase()]
            if (existingId != null) {
                exerciseIdMap[ex.id] = existingId
            } else {
                val newId = exDao.upsert(
                    Exercise(
                        id = 0L,
                        name = ex.name,
                        primaryMuscle = muscle,
                        equipment = equip,
                        isCustom = ex.isCustom,
                        notes = ex.notes,
                        description = ex.description,
                        metricType = metric
                    )
                )
                exerciseIdMap[ex.id] = newId
            }
        }

        // Krok 2: ID użyte w setach/planExercises których nie ma w pliku
        val missingIds = (
            data.sets.map { it.exerciseId } +
            data.planExercises.map { it.exerciseId }
        ).toSet() - exerciseIdMap.keys
        for (oldId in missingIds) {
            val existing = exDao.getById(oldId)
            if (existing != null) exerciseIdMap[oldId] = oldId
        }

        // Workouts + sets + automatyczna detekcja INJURY z painArea (v1.23.2)
        val eventDao = db.trainingEventDao()
        for (w in data.workouts) {
            val workout = Workout(
                id = w.id, startedAt = w.startedAt, finishedAt = w.finishedAt,
                fromPlanId = w.fromPlanId, fromDayOfWeek = w.fromDayOfWeek,
                notes = w.notes,
                aiSummary = w.aiSummary,
                aiSummaryGeneratedAt = w.aiSummaryGeneratedAt,
                wellbeingRating = w.wellbeingRating,
                painArea = w.painArea,
                painNotes = w.painNotes
            )
            wDao.insert(workout)
            // Auto-utwórz INJURY event jeśli workout ma painArea (analogicznie do
            // EventDetectorService.onPostWorkoutFeedback w prawdziwym UI flow).
            // Bez tego: zaimportowane workouty z bólem nie wyzwalają detekcji kontuzji.
            if (!workout.painArea.isNullOrBlank()) {
                eventDao.insertAll(detectInjuryFromWorkout(workout))
            }
        }
        var skippedSets = 0
        for (s in data.sets) {
            val mappedExId = exerciseIdMap[s.exerciseId]
            if (mappedExId == null) {
                skippedSets++
                continue
            }
            sDao.insert(
                WorkoutSet(
                    id = s.id, workoutId = s.workoutId, exerciseId = mappedExId,
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
        val skippedPlanExIds = mutableSetOf<Long>()
        for (pe in data.planExercises) {
            val mappedExId = exerciseIdMap[pe.exerciseId]
            if (mappedExId == null) {
                skippedPlanExIds += pe.id
                continue
            }
            peDao.upsert(
                PlanExercise(
                    id = pe.id, planId = pe.planId, exerciseId = mappedExId,
                    dayOfWeek = pe.dayOfWeek, orderIndex = pe.orderIndex,
                    supersetGroup = pe.supersetGroup
                )
            )
        }
        for (ps in data.planSets) {
            if (ps.planExerciseId in skippedPlanExIds) continue
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

        // Progress photos (tylko metadata)
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

        // AI prefs
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

        // v1.24.7: spróbuj utworzyć mesocykle z importowanej historii treningowej.
        // Idempotentne (no-op gdy mesoDao.count() > 0). Bez tego aplikacja po imporcie
        // mówi "Brak mesocykli — utworzy się gdy ≥4 treningi" mimo że są treningi.
        if (data.workouts.isNotEmpty()) {
            runCatching { mesocycleBackfillService.backfillFromHistory() }
        }

        // v1.24.12: po imporcie planów ustaw najnowszy jako aktywny — żeby
        // user nie został z brakiem aktywnego planu (filozofia: jeden user,
        // jeden aktywny stan). Idempotentne (no-op gdy ktoś już aktywny).
        if (data.plans.isNotEmpty()) {
            runCatching {
                if (planDao.getActive() == null) {
                    val newest = planDao.getAll().maxByOrNull { it.createdAt }
                    if (newest != null) {
                        planDao.clearActive()
                        planDao.markActive(newest.id)
                    }
                }
            }
        }

        return BackupImportResult(
            workouts = data.workouts.size,
            plans = data.plans.size,
            aiConversations = data.aiConversations.size,
            skippedSets = skippedSets
        )
    }
}
