package com.chethan616.clearpdf.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.chethan616.clearpdf.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A newer Docsy announced in update.json on GitHub. [url] is the APK for this phone's CPU. */
data class UpdateInfo(
    val version: String,
    val versionCode: Long,
    val url: String,
    val sha256: String,
    val size: Long,
    val notesRu: String,
    val notesEn: String
) {
    fun notes(language: String): String = if (language == "ru") notesRu else notesEn
}

/** Where an update stands, shared by every place that shows it (Home, a document, About). */
sealed interface UpdateState {
    /** Nothing checked yet in this run. */
    data object Unknown : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object CheckFailed : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    /**
     * Downloaded and checked. Android's installer is open, or — with [needsPermission] — Android
     * first wants "install unknown apps" allowed for Docsy. Kept until Docsy restarts as the new
     * version, so a cancelled installer can be opened again without downloading twice.
     */
    data class ReadyToInstall(val info: UpdateInfo, val file: File, val needsPermission: Boolean) : UpdateState
    data class DownloadFailed(val info: UpdateInfo, val badFile: Boolean) : UpdateState
}

/** The newer version an [UpdateState] is about, if any. */
val UpdateState.newVersion: UpdateInfo?
    get() = when (this) {
        is UpdateState.Available -> info
        is UpdateState.Downloading -> info
        is UpdateState.ReadyToInstall -> info
        is UpdateState.DownloadFailed -> info
        else -> null
    }

/**
 * Docsy updates itself from its GitHub releases, the way Гига Писарь does: no server and no
 * tracking. Once a day the app reads a small update.json in the repository; a newer version
 * shows as a card. "Update" downloads the APK, checks its SHA-256 and opens Android's own
 * installer, which also checks the APK is signed with the same key. Only the GitHub build
 * (foss) does this: Google Play and F-Droid update apps themselves.
 */
object Updates {
    private const val MANIFEST_URL = "https://raw.githubusercontent.com/poneslas/Docsy/main/update.json"
    private const val TRUSTED_PREFIX = "https://github.com/poneslas/Docsy/releases/download/"
    private const val PREFS = "docsy_updates"
    private const val CHECK_EVERY_MS = 24 * 60 * 60 * 1000L

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Unknown)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** "Open About": set by the Home row and the document pill, consumed by the app and Settings. */
    val openAbout = MutableStateFlow(false)

    /** Downloads outlive the screen that started them. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether this build updates itself at all (the GitHub build only). */
    val supported: Boolean get() = BuildConfig.SELF_UPDATE

    /** The "Check for updates" switch in Settings → About; on by default. */
    fun autoCheck(context: Context): Boolean = prefs(context).getBoolean("autoCheck", true)
    fun setAutoCheck(context: Context, on: Boolean) = prefs(context).edit().putBoolean("autoCheck", on).apply()

    fun installedCode(context: Context): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrDefault(0L)

    /** The newer version found by the last check, if any (survives restarts). */
    fun pending(context: Context): UpdateInfo? {
        if (!supported) return null
        val raw = prefs(context).getString("latest", null) ?: return null
        val info = runCatching { parse(JSONObject(raw)) }.getOrNull() ?: return null
        // APKs only ever come from this repository's releases.
        return info.takeIf { it.versionCode > installedCode(context) && it.url.startsWith(TRUSTED_PREFIX) }
    }

    /** Whether a day has passed since the last look. */
    fun due(context: Context): Boolean =
        supported && autoCheck(context) && System.currentTimeMillis() - prefs(context).getLong("checkedAt", 0) > CHECK_EVERY_MS

    /** Blocking: reads update.json; a newer version, null when this is the latest. Throws when offline. */
    private fun fetch(context: Context): UpdateInfo? {
        val connection = URL(MANIFEST_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Cache-Control", "no-cache")
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        prefs(context).edit().putLong("checkedAt", System.currentTimeMillis()).putString("latest", text).apply()
        return pending(context)
    }

    /** Busy with an update the user started: a check must not replace its state. */
    private fun busy() = _state.value.let { it is UpdateState.Downloading || it is UpdateState.ReadyToInstall }

    /** On every app start: show what an earlier check found, and look again once a day (if allowed). */
    fun onAppResume(context: Context) {
        if (!supported) return
        val app = context.applicationContext
        // Back from the "install unknown apps" switch: carry on with the install.
        (_state.value as? UpdateState.ReadyToInstall)?.takeIf { it.needsPermission && canInstall(app) }?.let {
            _state.value = it.copy(needsPermission = false)
            install(app, it.file)
        }
        if (_state.value == UpdateState.Unknown && autoCheck(app)) pending(app)?.let { _state.value = UpdateState.Available(it) }
        if (busy() || !due(app)) return
        scope.launch {
            val found = runCatching { fetch(app) }.getOrNull() ?: return@launch
            if (!busy()) _state.value = UpdateState.Available(found)
        }
    }

    /** About: always look now, whatever the daily switch says (opening About asks for it). */
    fun checkNow(context: Context) {
        if (!supported || busy()) return
        val app = context.applicationContext
        _state.value = UpdateState.Checking
        scope.launch {
            val result = runCatching { fetch(app) }
            if (busy()) return@launch
            _state.value = result.fold(
                onSuccess = { found -> found?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate },
                onFailure = { UpdateState.CheckFailed }
            )
        }
    }

    /** "Update": download, check, then Android's installer (or its permission page first). */
    fun startUpdate(context: Context) {
        val info = _state.value.newVersion ?: return
        val app = context.applicationContext
        val ready = (_state.value as? UpdateState.ReadyToInstall)?.file?.takeIf { it.exists() }
        if (ready != null) { proceed(app, info, ready); return }
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(info, 0)
        scope.launch {
            try {
                val file = download(app, info) { _state.value = UpdateState.Downloading(info, it) }
                proceed(app, info, file)
            } catch (e: Exception) {
                _state.value = UpdateState.DownloadFailed(info, badFile = e is BadUpdateFile)
            }
        }
    }

    private fun proceed(context: Context, info: UpdateInfo, file: File) {
        val allowed = canInstall(context)
        _state.value = UpdateState.ReadyToInstall(info, file, needsPermission = !allowed)
        if (allowed) install(context, file) else openInstallPermission(context)
    }

    /** The document pill shows once per version. */
    fun pillShown(context: Context, info: UpdateInfo): Boolean = prefs(context).getLong("pillShownCode", 0) >= info.versionCode
    fun markPillShown(context: Context, info: UpdateInfo) = prefs(context).edit().putLong("pillShownCode", info.versionCode).apply()

    /** The APK for this phone: arm64 when the CPU is 64-bit ARM (almost every phone), else universal. */
    private fun parse(json: JSONObject): UpdateInfo {
        val apks = json.getJSONObject("apk")
        val abi = if (Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a" && apks.has("arm64-v8a")) "arm64-v8a" else "universal"
        val apk = apks.getJSONObject(abi)
        val notes = json.optJSONObject("notes")
        return UpdateInfo(
            version = json.getString("version"),
            versionCode = json.getLong("versionCode"),
            url = apk.getString("url"),
            sha256 = apk.getString("sha256").lowercase(),
            size = apk.optLong("size"),
            notesRu = notes?.optString("ru").orEmpty(),
            notesEn = notes?.optString("en").orEmpty()
        )
    }

    /**
     * Blocking: downloads the APK into the cache, reporting 0..100, and checks its SHA-256.
     * Returns the file; throws when the download breaks or the file doesn't match.
     */
    private fun download(context: Context, info: UpdateInfo, progress: (Int) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // old downloads
        val file = File(dir, "Docsy-${info.version}.apk")
        val connection = URL(info.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.size
        val digest = MessageDigest.getInstance("SHA-256")
        connection.inputStream.use { input ->
            file.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                var last = -1
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    done += n
                    if (total > 0) {
                        val p = (100 * done / total).toInt()
                        if (p != last) { last = p; progress(p) }
                    }
                }
            }
        }
        connection.disconnect()
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (sha != info.sha256) {
            file.delete()
            throw BadUpdateFile()
        }
        return file
    }

    class BadUpdateFile : Exception()

    /** Android asks once whether Docsy may install apps; until then its settings page opens instead. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Opens Android's installer on the downloaded APK (served by the app's own FileProvider). */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
