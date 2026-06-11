package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.data.db.dao.DiagnosticEventDao
import pl.filebit.gymtracker.data.entity.DiagnosticCategory
import pl.filebit.gymtracker.data.entity.DiagnosticEvent
import pl.filebit.gymtracker.data.entity.DiagnosticLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lekki, fire-and-forget logger zdarzeń diagnostycznych.
 *
 * Zasady:
 *  - NIGDY nie blokuje ani nie rzuca — instrumentacja nie może zepsuć apki.
 *  - Zapis async (własny scope, IO dispatcher).
 *  - Offline-first: tylko lokalnie; eksport przez Debug na żądanie usera.
 *
 * Komplementarny do AiLogRepository (transkrypty AI). Tu: strumień decyzji,
 * wyników workerów, notyfikacji, akcji usera i cichych błędów.
 */
@Singleton
class DiagnosticLogger @Inject constructor(
    private val dao: DiagnosticEventDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun event(
        category: DiagnosticCategory,
        level: DiagnosticLevel,
        source: String,
        event: String,
        message: String,
        dataJson: String? = null,
        success: Boolean? = null
    ) {
        scope.launch {
            runCatching {
                dao.insert(
                    DiagnosticEvent(
                        category = category.name,
                        level = level.name,
                        source = source,
                        event = event,
                        message = message,
                        dataJson = dataJson,
                        success = success
                    )
                )
            }
        }
    }

    fun info(category: DiagnosticCategory, source: String, eventCode: String, message: String,
             dataJson: String? = null, success: Boolean? = null) =
        event(category, DiagnosticLevel.INFO, source, eventCode, message, dataJson, success)

    fun warn(category: DiagnosticCategory, source: String, eventCode: String, message: String,
             dataJson: String? = null) =
        event(category, DiagnosticLevel.WARN, source, eventCode, message, dataJson, success = false)

    fun error(category: DiagnosticCategory, source: String, eventCode: String, message: String,
              throwable: Throwable? = null) =
        event(category, DiagnosticLevel.ERROR, source, eventCode,
            message + (throwable?.let { " — ${it::class.simpleName}: ${it.message}" } ?: ""),
            success = false)

    /** Cleanup — usuwa wpisy starsze niż [days] dni. Wywoływane przy starcie apki. */
    fun cleanup(days: Int = 30) {
        scope.launch {
            runCatching { dao.deleteOlderThan(System.currentTimeMillis() - days * 24L * 3600 * 1000) }
        }
    }
}
