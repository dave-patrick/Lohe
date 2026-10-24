package com.lohe.app.dev

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import com.lohe.app.data.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Local-dev diagnostic recorder and bug-report bundler.
 *
 * Captures:
 *  - Structured app events (navigation, user taps, playback state/errors,
 *    download lifecycle, RSS refresh) in a ring buffer and on disk
 *  - Uncaught crashes with full stack traces + state snapshot
 *  - Live window screenshots (via [PixelCopy]) + `logcat -d` dumps when the
 *    user submits a bug report
 */
object DevDiagnostics {

    private const val TAG = "LoheDev"
    private const val MAX_BUFFER_LINES = 2_000

    private val lock = Any()
    private val ringBuffer = ArrayDeque<String>(MAX_BUFFER_LINES)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var initialized = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    var currentRoute: String = "splash"

    @Volatile
    var lastUserAction: String = "none"

    @Volatile
    var nowPlayingSummary: String = "none"

    @Volatile
    var playbackStateSummary: String = "idle"

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val folderFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            initialized = true

            val dir = reportsRoot(app)
            dir.mkdirs()

            val sessionFile = File(dir, "session.log")
            val prevFile = File(dir, "prev-session.log")
            if (sessionFile.exists() && sessionFile.length() > 0L) {
                runCatching { sessionFile.copyTo(prevFile, overwrite = true) }
            }
            runCatching {
                sessionFile.writeText(
                    "=== Lohe Dev Session Started ${ formatNow() } | SDK=${Build.VERSION.SDK_INT} | Device=${Build.MANUFACTURER} ${Build.MODEL} ===\n",
                )
            }

            installCrashHandler(app)
            log("INIT", "DevDiagnostics initialized on ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
        }
    }

    fun log(category: String, message: String, error: Throwable? = null) {
        val ts = synchronized(timeFormat) { timeFormat.format(Date()) }
        val thread = Thread.currentThread().name
        val stack = if (error != null) "\n" + stackTraceString(error) else ""
        val line = "$ts [$thread] [$category] $message$stack"

        runCatching {
            if (error != null) {
                Log.e(TAG, "[$category] $message", error)
            } else {
                Log.d(TAG, "[$category] $message")
            }
        }

        synchronized(lock) {
            if (ringBuffer.size >= MAX_BUFFER_LINES) {
                ringBuffer.removeFirst()
            }
            ringBuffer.addLast(line)
            appContext?.let { ctx ->
                runCatching {
                    File(reportsRoot(ctx), "session.log").appendText(line + "\n")
                }
            }
        }
    }

    fun recordAction(action: String) {
        lastUserAction = "${formatNow()} -> $action"
        log("ACTION", action)
    }

    fun recordRoute(route: String, args: String = "") {
        currentRoute = if (args.isBlank()) route else "$route ($args)"
        log("NAV", "route=$currentRoute")
    }

    private fun installCrashHandler(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                log("CRASH", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
                val stamp = synchronized(folderFormat) { folderFormat.format(Date()) }
                val crashDir = File(reportsRoot(context), "crash-$stamp").apply { mkdirs() }
                val latestDir = File(reportsRoot(context), "latest").apply { mkdirs() }

                val summary = buildString {
                    appendLine("# Lohe Crash Report ($stamp)")
                    appendLine("- **Timestamp**: ${formatNow()}")
                    appendLine("- **Thread**: `${thread.name}`")
                    appendLine("- **Current Route**: `$currentRoute`")
                    appendLine("- **Last User Action**: `$lastUserAction`")
                    appendLine("- **Now Playing**: `$nowPlayingSummary`")
                    appendLine("- **Playback State**: `$playbackStateSummary`")
                    appendLine("- **Device**: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
                    appendLine()
                    appendLine("## Exception")
                    appendLine("```")
                    appendLine(stackTraceString(throwable))
                    appendLine("```")
                }
                File(crashDir, "report.md").writeText(summary)
                File(crashDir, "app-events.log").writeText(dumpRingBufferWithPrev(context))
                File(crashDir, "logcat.txt").writeText(captureLogcat())

                copyDirectory(crashDir, latestDir)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Captures the current Activity window before the bug-report dialog opens.
     */
    fun captureScreenshot(activity: Activity, onResult: (Bitmap?) -> Unit) {
        val view = activity.window?.decorView?.rootView
        if (view == null || view.width <= 0 || view.height <= 0) {
            onResult(null)
            return
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                PixelCopy.request(
                    activity.window,
                    bitmap,
                    { result ->
                        if (result == PixelCopy.SUCCESS) {
                            onResult(bitmap)
                        } else {
                            log("BUG_REPORT", "PixelCopy returned $result; falling back to Canvas draw")
                            runCatching { view.draw(Canvas(bitmap)) }
                            onResult(bitmap)
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
            } catch (t: Throwable) {
                log("BUG_REPORT", "PixelCopy failed, falling back to Canvas", t)
                runCatching { view.draw(Canvas(bitmap)) }
                onResult(bitmap)
            }
        } else {
            runCatching { view.draw(Canvas(bitmap)) }
            onResult(bitmap)
        }
    }

    /**
     * Writes a complete bug report bundle (`screenshot.png`, `report.md`,
     * `app-events.log`, `logcat.txt`) to both `files/bug-reports/report-<ts>/`
     * and `files/bug-reports/latest/`.
     */
    suspend fun submitBugReport(
        context: Context,
        userNote: String,
        screenshot: Bitmap?,
    ): String = withContext(Dispatchers.IO) {
        val stamp = synchronized(folderFormat) { folderFormat.format(Date()) }
        val reportId = "report-$stamp"
        val root = reportsRoot(context)
        val reportDir = File(root, reportId).apply { mkdirs() }
        val latestDir = File(root, "latest").apply {
            deleteRecursively()
            mkdirs()
        }

        log("BUG_REPORT", "Creating bug report $reportId (note=${userNote.ifBlank { "<empty>" }})")

        if (screenshot != null) {
            runCatching {
                File(reportDir, "screenshot.png").outputStream().use { out ->
                    screenshot.compress(Bitmap.CompressFormat.PNG, 92, out)
                }
            }.onFailure { log("BUG_REPORT", "Failed saving screenshot.png", it) }
        }

        val dbStats = collectDatabaseStats()
        val summary = buildString {
            appendLine("# Lohe Bug Report (`$reportId`)")
            appendLine("- **Timestamp**: ${formatNow()}")
            appendLine("- **User Note**: ${userNote.ifBlank { "_(no note entered)_" }}")
            appendLine("- **Current Route**: `$currentRoute`")
            appendLine("- **Last User Action**: `$lastUserAction`")
            appendLine("- **Now Playing**: `$nowPlayingSummary`")
            appendLine("- **Playback State**: `$playbackStateSummary`")
            appendLine("- **Device**: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("- **Screenshot Attached**: ${screenshot != null}")
            appendLine()
            appendLine("## Database & Queue Snapshot")
            appendLine(dbStats)
            appendLine()
            appendLine("## Recent App Events (last 40 lines)")
            appendLine("```")
            synchronized(lock) {
                ringBuffer.toList().takeLast(40).forEach { appendLine(it) }
            }
            appendLine("```")
        }

        File(reportDir, "report.md").writeText(summary)
        File(reportDir, "app-events.log").writeText(dumpRingBufferWithPrev(context))
        File(reportDir, "logcat.txt").writeText(captureLogcat())

        copyDirectory(reportDir, latestDir)
        log("BUG_REPORT", "Saved bug report bundle to ${reportDir.absolutePath}")
        reportId
    }

    private suspend fun collectDatabaseStats(): String = runCatching {
        val repo = ServiceLocator.subscriptionRepository()
        val podcasts = repo.observePodcasts().firstOrNull().orEmpty()
        val queue = repo.queueSnapshot()
        buildString {
            appendLine("- **Subscribed Podcasts**: ${podcasts.size}")
            podcasts.forEach { p ->
                appendLine("  - `${p.title}` (uuid=${p.uuid}, autoDownload=${p.autoDownload}, feed=${p.feedUrl})")
            }
            appendLine("- **Queued Episodes**: ${queue.size}")
            queue.take(10).forEachIndexed { idx, ep ->
                appendLine("  - [${idx + 1}] `${ep.title}` (uuid=${ep.uuid}, state=${ep.downloadState}, local=${ep.localPath != null})")
            }
        }
    }.getOrElse { "Failed to read DB stats: ${it.message}" }

    private fun dumpRingBufferWithPrev(context: Context): String = buildString {
        val prev = File(reportsRoot(context), "prev-session.log")
        if (prev.exists()) {
            appendLine("=== PREVIOUS SESSION LOG ===")
            appendLine(runCatching { prev.readText() }.getOrDefault(""))
            appendLine("=== CURRENT SESSION LOG ===")
        }
        synchronized(lock) {
            ringBuffer.forEach { appendLine(it) }
        }
    }

    private fun captureLogcat(): String = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "1500")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output
    }.getOrElse { "Failed to capture logcat: ${stackTraceString(it)}" }

    private fun copyDirectory(src: File, dst: File) {
        dst.mkdirs()
        src.listFiles()?.forEach { file ->
            if (file.isFile) {
                runCatching { file.copyTo(File(dst, file.name), overwrite = true) }
            }
        }
    }

    private fun reportsRoot(context: Context): File =
        File(context.filesDir, "bug-reports")

    private fun formatNow(): String =
        synchronized(timeFormat) { timeFormat.format(Date()) }

    private fun stackTraceString(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString().trimEnd()
    }
}
