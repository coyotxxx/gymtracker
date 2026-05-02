package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "body_measurements")
data class BodyMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,                       // epoch millis
    val weightKg: Double? = null,
    val muscleMassKg: Double? = null,     // masa mięśniowa (z wagi smart)
    val chestCm: Double? = null,          // klatka piersiowa
    val waistCm: Double? = null,          // talia (najwęższe miejsce)
    val bellyCm: Double? = null,          // pas (na pępku)
    val hipsCm: Double? = null,           // biodra
    val neckCm: Double? = null,           // kark
    val armCm: Double? = null,            // ramię (rozluźnione)
    val bicepsCm: Double? = null,         // biceps (zgięte)
    val thighCm: Double? = null,          // udo
    val calfCm: Double? = null,           // łydka
    val bodyFatPercent: Double? = null,   // % tkanki tłuszczowej
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
