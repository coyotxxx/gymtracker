package pl.filebit.gymtracker.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

enum class HealthConnectAvailability {
    INSTALLED,
    NOT_INSTALLED,    // Health Connect app not installed (Android 13-)
    NOT_SUPPORTED     // Below Android 8 / API 26
}

/**
 * Wrapper na Health Connect client. Off-device tylko z permissions.
 *
 * Flow:
 *  1. checkAvailability() — czy HC dostępne na tym urządzeniu
 *  2. hasAllPermissions() — czy mamy permissions
 *  3. requestPermissions() — uruchom UI permission request (z Activity)
 *  4. readStepsForDate(dateMs) — pobierz kroki dnia
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val requiredPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    fun checkAvailability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.INSTALLED
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.NOT_INSTALLED
            else -> HealthConnectAvailability.NOT_SUPPORTED
        }

    /**
     * Intent do uruchomienia Play Store (lub HC settings) jeśli HC nieinstalowane.
     */
    fun providerInstallIntent(): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    private fun client(): HealthConnectClient? =
        if (checkAvailability() == HealthConnectAvailability.INSTALLED) {
            HealthConnectClient.getOrCreate(context)
        } else null

    suspend fun hasAllPermissions(): Boolean {
        val c = client() ?: return false
        return c.permissionController.getGrantedPermissions().containsAll(requiredPermissions)
    }

    /**
     * Permission contract — używać z `rememberLauncherForActivityResult` w Compose.
     */
    fun permissionContract() =
        PermissionController.createRequestPermissionResultContract()

    fun permissionsToRequest(): Set<String> = requiredPermissions

    /**
     * Pobiera sumę kroków dla danej daty (start dnia → następny dzień start, lokalnie).
     * Zwraca 0 jeśli brak danych lub brak permissions.
     */
    suspend fun readStepsForDate(dateMs: Long): Int {
        val c = client() ?: return 0
        if (!hasAllPermissions()) return 0

        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(dateMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val end = start.plusSeconds(24 * 3600)

        return try {
            val response = c.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            response.records.sumOf { it.count }.toInt()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Pobiera kroki dla N ostatnich dni (włącznie z dziś). Zwraca mapę dateMs (start dnia) → steps.
     */
    suspend fun readStepsForLastDays(days: Int): Map<Long, Int> {
        val c = client() ?: return emptyMap()
        if (!hasAllPermissions()) return emptyMap()

        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val map = mutableMapOf<Long, Int>()
        for (d in 0 until days) {
            val day = now.minusDays(d.toLong()).toLocalDate().atStartOfDay(zone)
            val dayStartMs = day.toInstant().toEpochMilli()
            map[dayStartMs] = readStepsForDate(dayStartMs)
        }
        return map
    }
}
