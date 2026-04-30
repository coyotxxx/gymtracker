package pl.filebit.gymtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PhotoType { FRONT, SIDE, BACK }

@Entity(tableName = "progress_photos")
data class ProgressPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,                   // epoch millis sesji zdjęciowej
    val photoType: PhotoType,
    val filename: String,             // nazwa pliku w filesDir/progress_photos/
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
