package pl.filebit.gymtracker.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pl.filebit.gymtracker.data.db.AppDatabase
import pl.filebit.gymtracker.data.db.dao.ExerciseDao
import pl.filebit.gymtracker.data.entity.PlanExercise
import pl.filebit.gymtracker.data.entity.PlanExerciseSet
import pl.filebit.gymtracker.data.entity.TrainingPlan
import pl.filebit.gymtracker.data.repository.DeloadPreferences
import pl.filebit.gymtracker.data.repository.DeloadService
import pl.filebit.gymtracker.data.repository.LoadIncreasePreferences
import pl.filebit.gymtracker.data.repository.PlanRepository
import pl.filebit.gymtracker.util.DeloadSeverity
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val DELOAD_TEST_PLAN_NAME = "DEBUG_DELOAD_TEST"
private const val DELOAD_TEST_WEIGHT_KG = 80.0

@HiltViewModel
class DebugViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val deloadPrefs: DeloadPreferences,
    private val loadIncreasePrefs: LoadIncreasePreferences,
    private val planRepo: PlanRepository,
    private val deloadService: DeloadService,
    private val exerciseDao: ExerciseDao,
    private val backupImporter: pl.filebit.gymtracker.data.backup.BackupImporter
) : ViewModel() {

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    // v2.7.1: stan bazy w DIALOGU (nie w StatusBox na dole listy — był niewidoczny
    // bez scrollowania). Popup widoczny od razu, niezależnie od pozycji scrolla.
    private val _dbStateDialog = MutableStateFlow<String?>(null)
    val dbStateDialog: StateFlow<String?> = _dbStateDialog.asStateFlow()
    fun dismissDbStateDialog() { _dbStateDialog.value = null }

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    /**
     * v2.7.0 — czytelny odczyt stanu bazy apki: ile treningów/setów realnie jest
     * i od kiedy. Pozwala zweryfikować "zaczynamy od nowa" zamiast zgadywać.
     * v2.7.1 — wynik w dialogu (widoczny natychmiast, niezależnie od scrolla).
     */
    fun showDbState() = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { dbStateReadable() } }
            .onSuccess { _dbStateDialog.value = it }
            .onFailure { _dbStateDialog.value = "✗ Błąd odczytu: ${it.message}" }
    }

    /**
     * v2.7.1 — czyści TYLKO historię treningów: workouts (CASCADE usuwa
     * workout_sets) + eventy/mezocykle/day-summaries pochodne od treningów.
     * ZOSTAWIA: ćwiczenia, plany, profil, pomiary, cele, dietę, AI.
     */
    fun wipeWorkoutHistory() = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { wipeWorkoutHistoryBlocking() } }
            .onSuccess { _dbStateDialog.value = it }
            .onFailure { _dbStateDialog.value = "✗ Błąd czyszczenia: ${it.message}" }
    }

    private fun wipeWorkoutHistoryBlocking(): String {
        val helper = db.openHelper.writableDatabase
        fun count(t: String): Long = runCatching {
            helper.query("SELECT COUNT(*) FROM `$t`").use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        }.getOrElse { 0L }
        val before = count("workouts")
        val setsBefore = count("workout_sets")
        // Kolejność: najpierw tabele zależne/pochodne, potem workouts (CASCADE → sety).
        // pending_periodization_decisions może mieć FK do mezocykli → usuwamy je pierwsze.
        listOf(
            "pending_periodization_decisions",
            "training_mesocycles",
            "training_events",
            "training_day_summary",
            "workouts" // CASCADE usuwa workout_sets
        ).forEach { t -> runCatching { helper.execSQL("DELETE FROM `$t`") } }
        val after = count("workouts")
        val setsAfter = count("workout_sets")
        return buildString {
            appendLine("WYCZYSZCZONO HISTORIĘ TRENINGÓW")
            appendLine("Treningi: $before → $after")
            appendLine("Serie: $setsBefore → $setsAfter")
            append("Ćwiczenia, plany, profil i dieta — zachowane.")
        }
    }

    private fun dbStateReadable(): String {
        val helper = db.openHelper.readableDatabase
        fun count(table: String): Long = runCatching {
            helper.query("SELECT COUNT(*) FROM `$table`").use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }
        }.getOrElse { -1L }
        fun firstLong(sql: String): Long? = runCatching {
            helper.query(sql).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
        }.getOrNull()

        val workouts = count("workouts")
        val finished = firstLong("SELECT COUNT(*) FROM workouts WHERE finishedAt IS NOT NULL") ?: 0L
        val sets = count("workout_sets")
        val oldest = firstLong("SELECT MIN(startedAt) FROM workouts")
        val newest = firstLong("SELECT MAX(startedAt) FROM workouts")
        val exercises = count("exercises")

        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val oldestStr = oldest?.let { fmt.format(java.util.Date(it)) } ?: "—"
        val newestStr = newest?.let { fmt.format(java.util.Date(it)) } ?: "—"
        val spanDays = if (oldest != null && newest != null) ((newest - oldest) / 86_400_000L) else 0L

        return buildString {
            appendLine("STAN BAZY APLIKACJI")
            appendLine("Treningi: $workouts (zakończone: $finished)")
            appendLine("Serie (workout_sets): $sets")
            appendLine("Ćwiczenia w bazie: $exercises")
            appendLine("Najstarszy trening: $oldestStr")
            appendLine("Najnowszy trening: $newestStr")
            append("Rozpiętość historii: $spanDays dni")
        }
    }

    fun exportJsonToDownloads() = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { writeJsonToDownloads() } }
            .onSuccess { _status.value = "✓ Zapisano: $it" }
            .onFailure { _status.value = "✗ Błąd: ${it.message}" }
    }

    fun exportDbToDownloads() = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { writeDbToDownloads() } }
            .onSuccess { _status.value = "✓ Zapisano: $it" }
            .onFailure { _status.value = "✗ Błąd: ${it.message}" }
    }

    fun setupDeloadTestPlan() = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { createDeloadTestPlan() } }
            .onSuccess { (planId, sets) -> _status.value = "✓ Plan ID=$planId · sety=$sets z ${DELOAD_TEST_WEIGHT_KG}kg" }
            .onFailure { _status.value = "✗ Błąd setup: ${it.message}" }
    }

    fun applyTestDeload() = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { applyTestDeloadHigh() } }
            .onSuccess { _status.value = it }
            .onFailure { _status.value = "✗ Błąd apply: ${it.message}" }
    }

    fun resetDeloadTest() = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { resetDeloadTestData() } }
            .onSuccess { _status.value = "✓ Reset OK: $it" }
            .onFailure { _status.value = "✗ Błąd reset: ${it.message}" }
    }

    /**
     * 1-klik import scenariusza testowego z /sdcard/Download/import.json
     * (lub /sdcard/Download/GymTracker/import.json jako fallback).
     * Zastępuje 5+ tapowy flow: Profil → scroll → Backup → Importuj → file picker.
     */
    fun importFromDownloads() = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { runImportFromDownloads() } }
            .onSuccess { _status.value = "✓ $it" }
            .onFailure { _status.value = "✗ Błąd importu: ${it.message}" }
    }

    private suspend fun runImportFromDownloads(): String {
        // 1) Najpierw spróbuj prywatnego app-scope Downloads (bez permission)
        //    Wymaga adb push do /sdcard/Android/data/pl.filebit.gymtracker.debug/files/Download/import.json
        val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (appDownloads != null) {
            val appFile = File(appDownloads, "import.json")
            if (appFile.exists() && appFile.isFile) {
                val result = backupImporter.importFromFile(appFile)
                return "(app-scope): ${result.toUserMessage()}"
            }
        }

        // 2) Fallback: MediaStore.Downloads (public Downloads, scoped storage compliant)
        val uri = findDownloadInMediaStore("import.json")
            ?: error("Brak pliku — wgraj 'import.json' do /sdcard/Download/ albo /sdcard/Android/data/pl.filebit.gymtracker.debug/files/Download/")
        val result = backupImporter.importFromUri(uri)
        return "(MediaStore): ${result.toUserMessage()}"
    }

    private fun findDownloadInMediaStore(filename: String): android.net.Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        val args = arrayOf(filename)
        return context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                android.content.ContentUris.withAppendedId(collection, id)
            } else null
        }
    }

    private suspend fun createDeloadTestPlan(): Pair<Long, Int> {
        // Reset jeśli istnieje
        planRepo.getAll().firstOrNull { it.name == DELOAD_TEST_PLAN_NAME }
            ?.let { planRepo.deletePlanById(it.id) }
        deloadPrefs.clearActiveDeload()

        // Stwórz plan
        val planId = planRepo.upsertPlan(
            TrainingPlan(name = DELOAD_TEST_PLAN_NAME, daysOfWeek = listOf(1))
        )

        // Pobierz pierwsze 1 ćwiczenie z seed (193 ćw. po seedowaniu)
        val exercises = exerciseDao.getAll().take(1)
        if (exercises.isEmpty()) error("Brak ćwiczeń w bazie (seed nie odpalił?)")

        var totalSets = 0
        exercises.forEachIndexed { idx, ex ->
            val peId = planRepo.upsertPlanExercise(
                PlanExercise(planId = planId, exerciseId = ex.id, dayOfWeek = 1, orderIndex = idx)
            )
            // 3 sety z weight 80kg
            repeat(3) { setIdx ->
                planRepo.upsertPlanSet(
                    PlanExerciseSet(
                        planExerciseId = peId,
                        setNumber = setIdx + 1,
                        reps = 8,
                        weightKg = DELOAD_TEST_WEIGHT_KG
                    )
                )
                totalSets++
            }
        }
        return planId to totalSets
    }

    private suspend fun applyTestDeloadHigh(): String {
        val plan = planRepo.getAll().firstOrNull { it.name == DELOAD_TEST_PLAN_NAME }
            ?: return "✗ Brak planu — najpierw Setup"
        val result = deloadService.apply(plan.id, DeloadSeverity.HIGH)

        // Po apply odczytaj aktualne wagi
        val weights = mutableListOf<Double>()
        planRepo.getPlanExercises(plan.id).forEach { pe ->
            planRepo.getSetsForPlanExercise(pe.id).forEach { s ->
                s.weightKg?.let { weights.add(it) }
            }
        }
        val weightsStr = weights.joinToString(",") { "%.2f".format(it) }
        return "apply: updated=${result.updatedSets} factor=${result.factor} alreadyActive=${result.alreadyActive} | wagi=[$weightsStr]"
    }

    private suspend fun resetDeloadTestData(): String {
        deloadService.cancelWithoutRestore()
        val plan = planRepo.getAll().firstOrNull { it.name == DELOAD_TEST_PLAN_NAME }
        if (plan != null) {
            planRepo.deletePlanById(plan.id)
            return "skasowano plan ID=${plan.id} + active deload"
        }
        return "brak planu test, active deload skasowany"
    }

    fun copyJsonToClipboard() = viewModelScope.launch {
        runCatching {
            val payload = withContext(Dispatchers.IO) { collectDiagnosticsJson() }
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("GymTracker debug", payload))
            payload.length
        }
            .onSuccess { _status.value = "✓ Skopiowano do schowka ($it znaków)" }
            .onFailure { _status.value = "✗ Błąd: ${it.message}" }
    }

    private suspend fun collectDiagnosticsJson(): String {
        val app = appInfoBlock()
        val deload = deloadBlock()
        val loadIncrease = loadIncreaseBlock()
        val tables = tableCountsBlock()
        val plans = allPlansBlock()
        val recentWorkouts = recentWorkoutsBlock()
        val activeMeso = activeMesoBlock()
        val diagnostics = diagnosticEventsBlock()

        val payload = buildJsonObject {
            put("schema", "gymtracker-debug-v1")
            put("timestamp", isoTimestamp())
            put("app", app)
            put("deloadPreferences", deload)
            put("loadIncreasePreferences", loadIncrease)
            put("tableCounts", tables)
            put("activeMesocycle", activeMeso)
            put("plans", plans)
            put("recentWorkouts", recentWorkouts)
            put("diagnosticEvents", diagnostics)
        }
        return json.encodeToString(JsonObject.serializer(), payload)
    }

    /** v2.20.0: ostatnie zdarzenia diagnostyczne — co apka robiła/decydowała. */
    private suspend fun diagnosticEventsBlock(): kotlinx.serialization.json.JsonArray =
        kotlinx.serialization.json.buildJsonArray {
            runCatching { db.diagnosticEventDao().getRecent(300) }.getOrDefault(emptyList())
                .forEach { e ->
                    add(buildJsonObject {
                        put("ts", e.timestampMs)
                        put("category", e.category)
                        put("level", e.level)
                        put("source", e.source)
                        put("event", e.event)
                        put("message", e.message)
                        e.dataJson?.let { put("data", it) }
                        e.success?.let { put("success", it) }
                    })
                }
        }

    private fun appInfoBlock(): JsonObject {
        val info = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        return buildJsonObject {
            put("packageName", context.packageName)
            put("versionName", info?.versionName ?: "?")
            put("versionCode", info?.longVersionCode ?: -1L)
            put("dbVersion", db.openHelper.readableDatabase.version)
            put("androidSdk", Build.VERSION.SDK_INT)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        }
    }

    private fun deloadBlock(): JsonObject {
        val state = deloadPrefs.activeDeload()
        return buildJsonObject {
            put("dismissedAtMs", deloadPrefs.dismissedAtMs())
            if (state == null) {
                put("active", false)
            } else {
                put("active", true)
                put("startedAtMs", state.startedAtMs)
                put("planId", state.planId)
                put("planName", state.planName)
                put("factor", state.factor)
                put("originalWeights", buildJsonObject {
                    state.originalWeights.forEach { (setId, w) ->
                        put(setId.toString(), w)
                    }
                })
            }
        }
    }

    private fun loadIncreaseBlock(): JsonObject {
        val state = loadIncreasePrefs.activeIncrease()
        return buildJsonObject {
            if (state == null) {
                put("active", false)
            } else {
                put("active", true)
                put("startedAtMs", state.startedAtMs)
                put("planId", state.planId)
                put("planName", state.planName)
                put("factor", state.factor)
                put("originalWeights", buildJsonObject {
                    state.originalWeights.forEach { (setId, w) ->
                        put(setId.toString(), w)
                    }
                })
            }
        }
    }

    private suspend fun tableCountsBlock(): JsonObject = buildJsonObject {
        val helper = db.openHelper.readableDatabase
        val names = listOf(
            "workouts", "workout_sets", "exercises",
            "training_plans", "plan_exercises", "plan_exercise_sets",
            "user_profile", "body_measurements", "goals",
            "training_mesocycles", "pending_periodization_decisions",
            "ai_conversations", "ai_chat_messages", "ai_weekly_reports",
            // v2.17.0 (P2-2): usunięto martwą "user_diet_profile" (scalona w user_profile w v1.28).
            // Dodano istniejące tabele diety dla pełniejszego debugu.
            "meal_entries", "meal_consumptions", "diet_phases", "adherence_log",
            "diagnostic_events"
        )
        names.forEach { name ->
            val count = runCatching {
                helper.query("SELECT COUNT(*) FROM `$name`").use { c ->
                    if (c.moveToFirst()) c.getLong(0) else -1L
                }
            }.getOrElse { -1L }
            put(name, count)
        }
    }

    private suspend fun allPlansBlock(): JsonObject {
        val helper = db.openHelper.readableDatabase
        val plans = buildJsonArray {
            runCatching {
                helper.query("SELECT id, name, createdAt FROM training_plans ORDER BY id ASC").use { pc ->
                    while (pc.moveToNext()) {
                        val planId = pc.getLong(0)
                        val planName = pc.getString(1) ?: ""
                        val createdAt = pc.getLong(2)
                        val exercises = exercisesForPlan(planId)
                        add(buildJsonObject {
                            put("planId", planId)
                            put("planName", planName)
                            put("createdAt", createdAt)
                            put("exercises", exercises)
                        })
                    }
                }
            }
        }
        return buildJsonObject { put("items", plans) }
    }

    private fun exercisesForPlan(planId: Long): kotlinx.serialization.json.JsonArray {
        val helper = db.openHelper.readableDatabase
        return buildJsonArray {
            runCatching {
                helper.query(
                    """
                    SELECT pe.id, pe.exerciseId, e.name, e.metricType, pe.dayOfWeek, pe.orderIndex
                    FROM plan_exercises pe
                    LEFT JOIN exercises e ON e.id = pe.exerciseId
                    WHERE pe.planId = ?
                    ORDER BY pe.dayOfWeek, pe.orderIndex
                    """.trimIndent(),
                    arrayOf(planId)
                ).use { c ->
                    while (c.moveToNext()) {
                        val peId = c.getLong(0)
                        val exId = c.getLong(1)
                        val exName = c.getString(2) ?: "?"
                        val metricType = c.getString(3) ?: "?"
                        val day = c.getInt(4)
                        val pos = c.getInt(5)
                        val sets = buildJsonArray {
                            runCatching {
                                helper.query(
                                    "SELECT setNumber, reps, weightKg, durationSec, distanceM FROM plan_exercise_sets WHERE planExerciseId = ? ORDER BY setNumber",
                                    arrayOf(peId)
                                ).use { sc ->
                                    while (sc.moveToNext()) {
                                        add(buildJsonObject {
                                            put("setNumber", sc.getInt(0))
                                            put("reps", sc.getInt(1))
                                            put("weightKg", sc.getDouble(2))
                                            put("durationSec", if (sc.isNull(3)) null else sc.getInt(3))
                                            put("distanceM", if (sc.isNull(4)) null else sc.getDouble(4))
                                        })
                                    }
                                }
                            }
                        }
                        add(buildJsonObject {
                            put("planExerciseId", peId)
                            put("exerciseId", exId)
                            put("name", exName)
                            put("metricType", metricType)
                            put("dayOfWeek", day)
                            put("position", pos)
                            put("sets", sets)
                        })
                    }
                }
            }
        }
    }

    private suspend fun recentWorkoutsBlock(): JsonObject {
        val helper = db.openHelper.readableDatabase
        val list = buildJsonArray {
            runCatching {
                helper.query(
                    "SELECT id, startedAt, finishedAt, fromPlanId, fromDayOfWeek FROM workouts ORDER BY startedAt DESC LIMIT 10"
                ).use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val startedAt = c.getLong(1)
                        val finishedAt: Long? = if (c.isNull(2)) null else c.getLong(2)
                        val fromPlanId: Long? = if (c.isNull(3)) null else c.getLong(3)
                        val fromDow: Int? = if (c.isNull(4)) null else c.getInt(4)
                        add(buildJsonObject {
                            put("id", id)
                            put("startedAt", startedAt)
                            put("finishedAt", if (finishedAt == null) JsonPrimitive(null as String?) else JsonPrimitive(finishedAt))
                            put("durationMs", if (finishedAt == null) JsonPrimitive(null as String?) else JsonPrimitive(finishedAt - startedAt))
                            put("fromPlanId", if (fromPlanId == null) JsonPrimitive(null as String?) else JsonPrimitive(fromPlanId))
                            put("fromDayOfWeek", if (fromDow == null) JsonPrimitive(null as String?) else JsonPrimitive(fromDow))
                            put("completed", finishedAt != null)
                        })
                    }
                }
            }
        }
        return buildJsonObject { put("items", list) }
    }

    private suspend fun activeMesoBlock(): JsonObject {
        val helper = db.openHelper.readableDatabase
        val list = buildJsonArray {
            runCatching {
                helper.query(
                    """
                    SELECT id, startDateMs, plannedEndDateMs, endDateMs, phase, weekInPhase, status, trainingPlanId, triggerReason
                    FROM training_mesocycles
                    WHERE status = 'ACTIVE'
                    ORDER BY startDateMs DESC
                    """.trimIndent()
                ).use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val startDateMs = c.getLong(1)
                        val plannedEnd = c.getLong(2)
                        val endDate: Long? = if (c.isNull(3)) null else c.getLong(3)
                        val phase = c.getString(4) ?: ""
                        val weekInPhase = c.getInt(5)
                        val status = c.getString(6) ?: ""
                        val planId: Long? = if (c.isNull(7)) null else c.getLong(7)
                        val trigger = c.getString(8) ?: ""
                        add(buildJsonObject {
                            put("id", id)
                            put("startDateMs", startDateMs)
                            put("plannedEndDateMs", plannedEnd)
                            put("endDateMs", if (endDate == null) JsonPrimitive(null as String?) else JsonPrimitive(endDate))
                            put("phase", phase)
                            put("weekInPhase", weekInPhase)
                            put("status", status)
                            put("trainingPlanId", if (planId == null) JsonPrimitive(null as String?) else JsonPrimitive(planId))
                            put("triggerReason", trigger)
                        })
                    }
                }
            }
        }
        return buildJsonObject { put("active", list) }
    }

    private fun writeJsonToDownloads(): String {
        val payload = kotlinx.coroutines.runBlocking { collectDiagnosticsJson() }
        val filename = "gymtracker-debug-${fileTimestamp()}.json"
        return writeToDownloadsMediaStore(filename, "application/json", payload.toByteArray(Charsets.UTF_8))
    }

    private fun writeDbToDownloads(): String {
        val helper = db.openHelper
        helper.writableDatabase.let { runCatching { it.query("PRAGMA wal_checkpoint(TRUNCATE)").close() } }
        val dbFile = context.getDatabasePath(helper.databaseName)
        val bytes = FileInputStream(dbFile).use { it.readBytes() }
        val filename = "gymtracker-db-${fileTimestamp()}.db"
        return writeToDownloadsMediaStore(filename, "application/octet-stream", bytes)
    }

    private fun writeToDownloadsMediaStore(filename: String, mime: String, bytes: ByteArray): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GymTracker")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore.insert zwrócił null")
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("openOutputStream zwrócił null")
            "Downloads/GymTracker/$filename"
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GymTracker")
            dir.mkdirs()
            val out = File(dir, filename)
            out.writeBytes(bytes)
            out.absolutePath
        }
    }

    private fun isoTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())

    private fun fileTimestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
