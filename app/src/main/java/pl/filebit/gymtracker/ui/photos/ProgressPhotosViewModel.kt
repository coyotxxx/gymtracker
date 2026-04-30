package pl.filebit.gymtracker.ui.photos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.entity.PhotoType
import pl.filebit.gymtracker.data.entity.ProgressPhoto
import pl.filebit.gymtracker.data.repository.ProgressPhotoRepository
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProgressPhotosViewModel @Inject constructor(
    private val repo: ProgressPhotoRepository
) : ViewModel() {

    val photos: StateFlow<List<ProgressPhoto>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun fileFor(photo: ProgressPhoto): File = repo.fileFor(photo)

    fun prepareCameraUri(): Uri = repo.prepareCameraCaptureUri()

    fun importPhoto(uri: Uri, type: PhotoType, notes: String = "") {
        viewModelScope.launch {
            runCatching { repo.importPhoto(uri, type, notes = notes) }
        }
    }

    fun delete(photo: ProgressPhoto) {
        viewModelScope.launch { repo.delete(photo) }
    }
}
