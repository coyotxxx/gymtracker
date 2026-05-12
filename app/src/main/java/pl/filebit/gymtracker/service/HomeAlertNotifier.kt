package pl.filebit.gymtracker.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.filebit.gymtracker.MainActivity
import pl.filebit.gymtracker.data.repository.AlertType
import pl.filebit.gymtracker.data.repository.DeloadCardState
import pl.filebit.gymtracker.data.repository.DeloadPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wysyła natychmiastową notyfikację gdy na Home pojawia się nowy alert.
 *
 * Filozofia (feedback Macieja 2026-05-12):
 *  - "wszystko musi trafiać do powiadomień by był jakiś znak po takich informacjach"
 *  - X-owanie alertu nie usuwa notyfikacji — user ma ślad w shade
 *
 * Anti-spam:
 *  - Per typ alertu zapisany hash zawartości w SharedPreferences
 *  - Jeśli hash bez zmian → nie notyfikujemy ponownie
 *  - Hash zmienia się gdy zmienia się partia bólu / severity / liczba dni przerwy
 */
@Singleton
class HomeAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: DeloadPreferences
) {
    /**
     * Wywoływane przez HomeViewModel po obliczeniu cardState.
     * Wysyła notyfikację jeśli alert jest nowy lub się zmienił.
     */
    fun maybeNotify(cardState: DeloadCardState) {
        val (type, title, body, hash) = describe(cardState) ?: return
        val lastHash = prefs.lastNotifiedHash(type)
        if (lastHash == hash) return  // Bez zmian — nie spamuj

        sendNotification(type, title, body)
        prefs.setLastNotifiedHash(type, hash)
    }

    private fun describe(cardState: DeloadCardState): NotificationContent? = when (cardState) {
        is DeloadCardState.ActiveInjury -> {
            val r = cardState.recommendation
            NotificationContent(
                type = AlertType.ACTIVE_INJURY,
                title = "GymTracker — wykryto ból (${r.painArea.lowercase()})",
                body = r.reason.take(200),
                hash = "INJ:${r.painArea}:${r.severity}:${r.occurrences}:${r.daysSinceLast}"
            )
        }
        is DeloadCardState.ReturnAfterBreak -> {
            val r = cardState.recommendation
            NotificationContent(
                type = AlertType.RETURN_AFTER_BREAK,
                title = "GymTracker — powrót po przerwie",
                body = r.reason.take(200),
                hash = "RTN:${r.severity}:${r.breakDays}"
            )
        }
        is DeloadCardState.Suggestion -> {
            val r = cardState.recommendation
            NotificationContent(
                type = AlertType.DELOAD_SUGGESTION,
                title = "GymTracker — sugestia deload",
                body = r.reason.take(200),
                hash = "DLD:${r.severity}:${r.reason.hashCode()}"
            )
        }
        is DeloadCardState.Active,
        is DeloadCardState.None -> null  // Active = już zastosowany, None = brak alertu
    }

    private fun sendNotification(type: AlertType, title: String, body: String) {
        val ctx = context
        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, type.ordinal + NOTIFICATION_ID_BASE, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(type.ordinal + NOTIFICATION_ID_BASE, notification)
    }

    private data class NotificationContent(
        val type: AlertType,
        val title: String,
        val body: String,
        val hash: String
    )

    companion object {
        /** Dedykowany channel dla alertów Home (różny od ProactiveAiCheckWorker). */
        const val CHANNEL_ID = "home_alerts_channel"
        /** Bazowy ID — dodajemy ordinal AlertType żeby każdy typ miał inny notification slot. */
        private const val NOTIFICATION_ID_BASE = 6000
    }
}
