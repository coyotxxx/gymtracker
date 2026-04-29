package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "body_measurements")
data class BodyMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,                       // epoch millis
    val weightKg: Double? = null,
    val chestCm: Double? = null,          // klatka
    val waistCm: Double? = null,          // pas
    val hipsCm: Double? = null,           // biodra
    val armCm: Double? = null,            // ramię
    val thighCm: Double? = null,          // udo
    val calfCm: Double? = null,           // łydka
    val bodyFatPercent: Double? = null,   // % tkanki tłuszczowej
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
