package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ActivitySource {
    MANUAL,         // user wpisał ręcznie
    HEALTH_CONNECT, // sync z Google Health Connect (v0.92+)
    PEDOMETER,      // Android STEP_COUNTER sensor (v0.92+)
    PLACEHOLDER     // fallback z avgStepsPerDay z UserDietProfile
}

/**
 * Codzienny log aktywności (NEAT). Klucz: dateMs (start dnia, unikalny per dzień).
 *
 * Filozofia: większość stagnacji w cut to spadek kroków (NEAT), nie wolniejszy
 * metabolizm. Logując kroki daily, NeatAnalyzer wykryje spadek i ZABLOKUJE
 * niepotrzebne cięcia kcal.
 */
@Entity(
    tableName = "daily_activity_logs",
    indices = [Index(value = ["dateMs"], unique = true), Index("createdAt")]
)
data class DailyActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Start dnia (00:00 lokalny). */
    val dateMs: Long,
    /** Liczba kroków. */
    val steps: Int = 0,
    /** Aktywne minuty (>3 MET, ruch średnio-intensywny). */
    val activeMinutes: Int = 0,
    /** Szacowane kcal NEAT (kroki × 0.04 dla 70-80kg). */
    val estimatedKcalNeat: Int = 0,
    val source: ActivitySource = ActivitySource.MANUAL,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
