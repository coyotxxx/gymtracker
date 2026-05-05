package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class HydrationSource {
    WATER,      // czysta woda
    TEA,        // herbata (zlicza się 1:1)
    COFFEE,     // kawa (zlicza się 1:1, ale max 4 kawy/dzień bo diuretyk)
    JUICE,      // sok (zlicza się 0.7:1 — cukier)
    OTHER       // inne (np. zupa, mleko)
}

@Entity(
    tableName = "hydration_logs",
    indices = [Index("dateMs"), Index("createdAt")]
)
data class HydrationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Start dnia (00:00 lokalny) — agregacja per dzień. */
    val dateMs: Long,
    val ml: Int,
    val source: HydrationSource = HydrationSource.WATER,
    val createdAt: Long = System.currentTimeMillis()
)
