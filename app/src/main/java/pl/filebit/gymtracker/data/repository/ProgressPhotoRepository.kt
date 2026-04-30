package pl.filebit.gymtracker.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import pl.filebit.gymtracker.data.db.dao.ProgressPhotoDao
import pl.filebit.gymtracker.data.entity.PhotoType
import pl.filebit.gymtracker.data.entity.ProgressPhoto
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressPhotoRepository @Inject constructor(
    private val dao: ProgressPhotoDao,
    @ApplicationContext private val context: Context
) {
    fun observeAll(): Flow<List<ProgressPhoto>> = dao.observeAll()
    suspend fun getById(id: Long): ProgressPhoto? = dao.getById(id)

    fun photosDir(): File {
        val dir = File(context.filesDir, "progress_photos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun fileFor(photo: ProgressPhoto): File = File(photosDir(), photo.filename)

    /**
     * Kopiuje zdjęcie spod zewnętrznego Uri do app-private storage,
     * tworzy wpis w DB. Zwraca id wpisu.
     */
    suspend fun importPhoto(
        sourceUri: Uri,
        photoType: PhotoType,
        date: Long = System.currentTimeMillis(),
        notes: String = ""
    ): Long = withContext(Dispatchers.IO) {
        val fname = "${date}_${photoType.name}_${System.nanoTime()}.jpg"
        val target = File(photosDir(), fname)
        context.contentResolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Nie można otworzyć źródłowego Uri" }
            FileOutputStream(target).use { out ->
                input.copyTo(out)
            }
        }
        dao.upsert(
            ProgressPhoto(
                date = date,
                photoType = photoType,
                filename = fname,
                notes = notes
            )
        )
    }

    suspend fun delete(photo: ProgressPhoto) = withContext(Dispatchers.IO) {
        runCatching { fileFor(photo).delete() }
        dao.delete(photo)
    }
}
