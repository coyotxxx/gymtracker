package pl.filebit.gymtracker.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val tag: String,                 // np. "v0.43.0-redesign"
    val versionName: String,         // np. "0.43.0"
    val title: String,               // tytuł release
    val notes: String,               // body release (changelog)
    val downloadUrl: String,         // bezpośredni link do .apk
    val isPrerelease: Boolean,
    val publishedAt: String          // ISO date
)

sealed class DownloadProgress {
    object Started : DownloadProgress()
    data class Progress(val percent: Int) : DownloadProgress()
    data class Done(val file: File) : DownloadProgress()
    data class Failed(val message: String) : DownloadProgress()
}

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** Aktualna wersja aplikacji z PackageManager. */
    fun currentVersionName(): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")
    }

    /**
     * Pobiera listę ostatnich releasów z GitHub i wybiera najnowszy z assetem .apk.
     * Bierze pod uwagę prereleases (testujemy je też). Zwraca null gdy brak nowszej.
     */
    /**
     * Rzuca wyjątek przy network/API error. Zwraca null gdy brak nowszej wersji.
     * Dzięki temu VM rozróżnia 'wszystko OK, jesteś na bieżąco' vs 'sieć padła'.
     */
    suspend fun checkForUpdate(): UpdateInfo? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/coyotxxx/gymtracker/releases?per_page=50")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "GymTracker-Android")
            .build()
        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) {
            val code = resp.code
            resp.close()
            error("GitHub API HTTP $code")
        }
        val body = resp.body?.string().orEmpty()
        resp.close()
        if (body.isBlank()) error("Pusta odpowiedź z GitHub API")

        val releases = json.decodeFromString<List<GhRelease>>(body)

        // v1.22.2 FIX: GitHub releases API sortuje po created_at DESC, ale wydania
        // mogą mieć created_at w przeszłości (np. odziedziczone z istniejącego draftu/taga).
        // Sortujemy ręcznie po publishedAt DESC żeby "najnowszy" znaczyło "ostatnio opublikowany",
        // nie "ostatnio utworzony jako draft".
        val latest = releases
            .filter { !it.draft }
            .sortedByDescending { it.publishedAt.orEmpty() }
            .firstOrNull { rel -> rel.assets.any { it.name.endsWith(".apk") } }
            ?: return@withContext null   // brak żadnego releasu z APK = legitnie 'brak update'

        val asset = latest.assets.first { it.name.endsWith(".apk") }
        // Czyścimy tag z prefiksów: 'v' i sufiksów typu '-redesign'.
        val cleanVersion = latest.tagName
            .removePrefix("v")
            .substringBeforeLast("-redesign")
            .substringBeforeLast("-rc")
            .substringBeforeLast("-beta")

        val current = currentVersionName()
            .substringBefore("-debug")
            .substringBefore("-")

        if (!isNewer(remote = cleanVersion, local = current)) return@withContext null

        UpdateInfo(
            tag = latest.tagName,
            versionName = cleanVersion,
            title = latest.name.ifBlank { latest.tagName },
            notes = latest.body.orEmpty().take(2000),
            downloadUrl = asset.browserDownloadUrl,
            isPrerelease = latest.prerelease,
            publishedAt = latest.publishedAt.orEmpty()
        )
    }

    /** Naive semver-ish compare: 0.42.1 < 0.43.0. Zwraca true gdy remote > local. */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.', '-').take(3).mapNotNull { it.toIntOrNull() }
        val l = local.split('.', '-').take(3).mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /**
     * Pobiera APK do app-specific external files (nie wymaga WRITE_EXTERNAL_STORAGE).
     * Emit-uje progress co ~2%.
     */
    fun downloadApk(url: String, fileName: String): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Started)
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        // Wyczyść stare APK
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, fileName)

        val req = Request.Builder().url(url).build()
        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) {
            emit(DownloadProgress.Failed("HTTP ${resp.code}"))
            resp.close()
            return@flow
        }
        val body = resp.body ?: run {
            emit(DownloadProgress.Failed("Pusta odpowiedź"))
            resp.close()
            return@flow
        }
        val total = body.contentLength()
        body.byteStream().use { input ->
            target.outputStream().use { output ->
                val buf = ByteArray(8 * 1024)
                var read = 0L
                var lastEmitted = -1
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    output.write(buf, 0, n)
                    read += n
                    if (total > 0) {
                        val pct = (read * 100 / total).toInt()
                        if (pct != lastEmitted && (pct % 2 == 0 || pct == 100)) {
                            emit(DownloadProgress.Progress(pct))
                            lastEmitted = pct
                        }
                    }
                }
            }
        }
        resp.close()
        emit(DownloadProgress.Done(target))
    }.flowOn(Dispatchers.IO)

    /**
     * Otwiera systemowy installer dla pobranego APK. Wymaga ze user wcześniej zezwolił
     * 'Install unknown apps' dla naszej apki (jednorazowo, system Android pyta).
     */
    fun installApk(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

@Serializable
private data class GhRelease(
    @kotlinx.serialization.SerialName("tag_name") val tagName: String,
    val name: String = "",
    val body: String? = null,
    @kotlinx.serialization.SerialName("draft") val draft: Boolean = false,
    @kotlinx.serialization.SerialName("prerelease") val prerelease: Boolean = false,
    @kotlinx.serialization.SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GhAsset> = emptyList()
)

@Serializable
private data class GhAsset(
    val name: String,
    @kotlinx.serialization.SerialName("browser_download_url") val browserDownloadUrl: String
)
