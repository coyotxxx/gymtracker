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
import pl.filebit.gymtracker.data.repository.DeloadPreferences
import pl.filebit.gymtracker.data.repository.LoadIncreasePreferences
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val deloadPrefs: DeloadPreferences,
    private val loadIncreasePrefs: LoadIncreasePreferences
) : ViewModel() {

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val json = Json { prettyPrint = true; encodeDefaults = true }

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
        }
        return json.encodeToString(JsonObject.serializer(), payload)
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
            "meal_entries", "meal_consumptions", "user_diet_profile"
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
                    SELECT pe.id, pe.exerciseId, e.name, pe.dayOfWeek, pe.position
                    FROM plan_exercises pe
                    LEFT JOIN exercises e ON e.id = pe.exerciseId
                    WHERE pe.planId = ?
                    ORDER BY pe.dayOfWeek, pe.position
                    """.trimIndent(),
                    arrayOf(planId)
                ).use { c ->
                    while (c.moveToNext()) {
                        val peId = c.getLong(0)
                        val exId = c.getLong(1)
                        val exName = c.getString(2) ?: "?"
                        val day = c.getInt(3)
                        val pos = c.getInt(4)
                        val sets = buildJsonArray {
                            runCatching {
                                helper.query(
                                    "SELECT setNumber, reps, weightKg FROM plan_exercise_sets WHERE planExerciseId = ? ORDER BY setNumber",
                                    arrayOf(peId)
                                ).use { sc ->
                                    while (sc.moveToNext()) {
                                        add(buildJsonObject {
                                            put("setNumber", sc.getInt(0))
                                            put("reps", sc.getInt(1))
                                            put("weightKg", sc.getDouble(2))
                                        })
                                    }
                                }
                            }
                        }
                        add(buildJsonObject {
                            put("planExerciseId", peId)
                            put("exerciseId", exId)
                            put("name", exName)
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
