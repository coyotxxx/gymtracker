package pl.filebit.gymtracker.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.filebit.gymtracker.data.db.dao.NotificationHistoryDao
import pl.filebit.gymtracker.data.entity.NotificationHistory
import pl.filebit.gymtracker.data.entity.NotificationKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.47.0 — zapis historii powiadomień (fire-and-forget, jak [DiagnosticLogger]).
 *
 * Notifiery wołają [record] w chwili wysłania push. Dedup przy zapisie (ten sam tytuł
 * w oknie 30 min → pomiń) chroni przed powtórkami nawet gdyby worker odpalił kilka razy.
 * Czyszczenie wpisów starszych niż 30 dni.
 */
@Singleton
class NotificationHistoryStore @Inject constructor(
    private val dao: NotificationHistoryDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Mutex serializuje „sprawdź + wstaw" — suspend-DAO Rooma przełącza kontekst w środku,
    // więc bez tego równoległe record() mogłyby wszystkie zobaczyć count=0 i zapisać duplikaty.
    private val mutex = Mutex()

    fun record(kind: NotificationKind, title: String, body: String, payload: String? = null) {
        scope.launch {
            runCatching {
                mutex.withLock {
                    val now = System.currentTimeMillis()
                    if (dao.countSameTitleSince(title, now - DEDUP_WINDOW_MS) > 0) return@withLock  // powtórka
                    dao.insert(
                        NotificationHistory(
                            timestampMs = now, kind = kind.name,
                            title = title, body = body, payload = payload
                        )
                    )
                    dao.deleteOlderThan(now - RETENTION_MS)
                }
            }
        }
    }

    companion object {
        private const val DEDUP_WINDOW_MS = 30L * 60 * 1000        // 30 min
        private const val RETENTION_MS = 30L * 24 * 3600 * 1000    // 30 dni
    }
}
