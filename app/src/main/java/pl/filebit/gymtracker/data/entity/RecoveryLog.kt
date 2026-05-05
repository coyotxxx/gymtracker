package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Codzienna ocena samopoczucia / regeneracji. Klucz: dateMs (start dnia).
 * User wypełnia raz dziennie (~30s przez slidery 1-5).
 *
 * Pole nullable = brak odpowiedzi w danym dniu (możliwe częściowe wypełnienie).
 * Każde pole 1-5: 1 = bardzo źle, 5 = bardzo dobrze (oprócz stres/hunger/soreness/difficulty
 * gdzie 1 = nic/lekkie, 5 = bardzo silne).
 */
@Entity(
    tableName = "recovery_logs",
    indices = [Index(value = ["dateMs"], unique = true), Index("createdAt")]
)
data class RecoveryLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Start dnia (00:00 lokalny) — unikat per dzień. */
    val dateMs: Long,
    val sleepHours: Double? = null,
    val sleepQuality: Int? = null,            // 1-5, 5=świetny
    val stressLevel: Int? = null,             // 1-5, 1=spokojny, 5=skrajny stres
    val hungerLevel: Int? = null,             // 1-5, 1=brak głodu, 5=ciągły głód
    val energyLevel: Int? = null,             // 1-5, 5=pełen energii
    val sorenessLevel: Int? = null,           // 1-5, 1=brak DOMS, 5=skrajny
    val difficultyAdherence: Int? = null,     // 1-5, 5=plan był bardzo trudny
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
