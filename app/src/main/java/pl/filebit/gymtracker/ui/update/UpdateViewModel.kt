package pl.filebit.gymtracker.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.repository.DownloadProgress
import pl.filebit.gymtracker.data.repository.UpdateInfo
import pl.filebit.gymtracker.data.repository.UpdateRepository
import java.io.File
import javax.inject.Inject

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    object UpToDate : UpdateUiState()
    data class Available(val info: UpdateInfo) : UpdateUiState()
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateUiState()
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateUiState()
    data class Failed(val message: String) : UpdateUiState()
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repo: UpdateRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    val currentVersion: String get() = repo.currentVersionName()

    fun checkForUpdate(silent: Boolean = false) {
        _state.value = UpdateUiState.Checking
        viewModelScope.launch {
            runCatching { repo.checkForUpdate() }
                .onSuccess { info ->
                    _state.value = if (info != null) UpdateUiState.Available(info)
                    else UpdateUiState.UpToDate
                }
                .onFailure { e ->
                    // ZAWSZE pokazuj Failed — nawet przy silent — żeby user wiedział
                    // że sieć padła i mógł retry-ować ręcznie. Wcześniejsza logika
                    // (silent → Idle) ukrywała problem.
                    _state.value = UpdateUiState.Failed(e.message ?: "Błąd sieci")
                }
        }
    }

    fun downloadAndInstall(info: UpdateInfo) {
        viewModelScope.launch {
            _state.value = UpdateUiState.Downloading(info, 0)
            val fileName = "gymtracker-${info.versionName}.apk"
            repo.downloadApk(info.downloadUrl, fileName).collect { p ->
                when (p) {
                    is DownloadProgress.Started -> {}
                    is DownloadProgress.Progress ->
                        _state.value = UpdateUiState.Downloading(info, p.percent)
                    is DownloadProgress.Done -> {
                        _state.value = UpdateUiState.ReadyToInstall(info, p.file)
                        // Auto-trigger systemowy installer
                        runCatching { repo.installApk(p.file) }
                            .onFailure { e ->
                                _state.value = UpdateUiState.Failed(
                                    e.message ?: "Nie udało się otworzyć instalatora"
                                )
                            }
                    }
                    is DownloadProgress.Failed ->
                        _state.value = UpdateUiState.Failed(p.message)
                }
            }
        }
    }

    fun installAgain(file: File) {
        runCatching { repo.installApk(file) }
            .onFailure { e ->
                _state.value = UpdateUiState.Failed(e.message ?: "?")
            }
    }

    fun dismiss() {
        _state.value = UpdateUiState.Idle
    }
}
