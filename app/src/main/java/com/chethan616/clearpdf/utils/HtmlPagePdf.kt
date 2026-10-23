package com.chethan616.clearpdf.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.chethan616.clearpdf.util.OfflineWebContent
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lays a self-contained HTML page (the Markdown and text viewers' print pages) out in an offscreen
 * WebView and prints it to an A4 PDF, through the same print route the Word renderer uses
 * ([DocxWebRenderer.printToPdf]): real page breaks, a table header repeated on each page. Where
 * that route is unavailable it falls back to slicing.
 *
 * JavaScript stays off and the WebView can't reach the network or any file.
 */
internal object HtmlPagePdf {

    private const val TimeoutSeconds = 45L

    /** Blocks until done; call off the main thread. True only if [outFile] holds a non-empty PDF. */
    fun render(context: Context, html: String, outFile: File): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return false
        outFile.parentFile?.mkdirs()
        val latch = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        val main = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        val finish = { result: Boolean -> ok.set(result); latch.countDown() }

        main.post {
            runCatching {
                val view = WebView(context)
                webView = view
                view.settings.javaScriptEnabled = false
                view.settings.allowFileAccess = false
                OfflineWebContent.harden(view)
                var started = false
                view.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest) =
                        OfflineWebContent.intercept(request)

                    override fun onPageFinished(view: WebView, url: String?) {
                        if (started) return
                        started = true
                        // A short settle so inline images are decoded before the layout is printed. Not
                        // view.postDelayed: an offscreen view isn't attached, so its queue never runs.
                        main.postDelayed({
                            val w = DocxWebRenderer.A4WidthMils
                            val h = DocxWebRenderer.A4HeightMils
                            val slice = { finish(runCatching { DocxWebRenderer.sliceToPdf(view, w, h, outFile) }.getOrDefault(false)) }
                            if (!DocxWebRenderer.printRouteAvailable) slice()
                            else runCatching {
                                DocxWebRenderer.printToPdf(view, w, h, outFile) { done -> if (done) finish(true) else slice() }
                            }.onFailure { slice() }
                        }, 150)
                    }
                }
                view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }.onFailure { finish(false) }
        }

        val completed = runCatching { latch.await(TimeoutSeconds, TimeUnit.SECONDS) }.getOrDefault(false)
        main.post { runCatching { webView?.destroy() } }
        if (!completed || !ok.get() || outFile.length() <= 0L) {
            runCatching { outFile.delete() }
            return false
        }
        return true
    }
}
