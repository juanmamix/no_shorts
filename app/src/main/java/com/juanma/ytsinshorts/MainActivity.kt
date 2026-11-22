package com.juanma.ytsinshorts

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView

    /** CSS + JS de assets/, listos para evaluateJavascript. */
    private lateinit var injection: String

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // Botón atrás en Android 13+ (back predictivo): solo registramos el callback cuando
    // hay algo que hacer; si no, el sistema cierra la app con su animación normal.
    private var backCallback: Any? = null
    private var backCallbackRegistered = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        injection = buildInjection()

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        webView = WebView(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(
            webView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        setContentView(root)
        goEdgeToEdge()
        applySystemBarInsets()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            userAgentString = chromeMobileUserAgent()
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()

        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = OnBackInvokedCallback { handleBack() }
        }

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(initialUrl(intent))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { webView.loadUrl(initialUrl(intent)) }
    }

    private fun initialUrl(intent: Intent?): String {
        val data = intent?.data?.toString()
        return when {
            data == null -> HOME
            else -> shortsToWatch(data) ?: data
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    // ── Botón atrás ───────────────────────────────────────────────────────────────

    private fun canHandleBack() = customView != null || webView.canGoBack()

    /** Devuelve true si consumió el "atrás". */
    private fun handleBack(): Boolean {
        when {
            customView != null -> exitFullscreen()
            webView.canGoBack() -> webView.goBack()
            else -> return false
        }
        return true
    }

    private fun updateBackCallback() {
        if (Build.VERSION.SDK_INT < 33) return
        val callback = backCallback as OnBackInvokedCallback
        val wanted = canHandleBack()
        if (wanted == backCallbackRegistered) return
        if (wanted) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback,
            )
        } else {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        }
        backCallbackRegistered = wanted
    }

    @Deprecated("Solo se usa antes de Android 13")
    override fun onBackPressed() {
        if (!handleBack()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ── Insets (edge-to-edge obligatorio con targetSdk 35+) ───────────────────────

    @Suppress("DEPRECATION")
    private fun goEdgeToEdge() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = LEGACY_LAYOUT_FLAGS
        }
    }

    private fun applySystemBarInsets() {
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime(),
                )
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom,
                )
            }
            insets
        }
    }

    // ── Pantalla completa de videos ───────────────────────────────────────────────

    private fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        view.setBackgroundColor(Color.BLACK)
        (window.decorView as FrameLayout).addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        root.visibility = View.GONE
        setSystemBarsHidden(true)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        updateBackCallback()
    }

    private fun exitFullscreen() {
        val view = customView ?: return
        (window.decorView as FrameLayout).removeView(view)
        customView = null
        root.visibility = View.VISIBLE
        setSystemBarsHidden(false)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        updateBackCallback()
    }

    private fun setSystemBarsHidden(hidden: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val controller = window.insetsController ?: return
            if (hidden) {
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.systemBars())
            } else {
                controller.show(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (hidden) {
                LEGACY_LAYOUT_FLAGS or View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            } else {
                LEGACY_LAYOUT_FLAGS
            }
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onShowCustomView(view: View, callback: CustomViewCallback) = enterFullscreen(view, callback)
        override fun onHideCustomView() = exitFullscreen()
    }

    // ── Navegación ────────────────────────────────────────────────────────────────

    private inner class Client : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase()

            if (scheme != "http" && scheme != "https") {
                // intent:// (p. ej. "Abrir en la app de YouTube") se ignora; el resto
                // (mailto:, tel:, market:...) va a la app que corresponda.
                if (scheme != "intent" && scheme != "about" && scheme != "javascript") openExternally(uri)
                return true
            }

            if (!request.isForMainFrame) return false

            shortsToWatch(uri.toString())?.let {
                view.loadUrl(it)
                return true
            }

            if (isInternalHost(uri.host)) return false

            openExternally(uri)
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            shortsToWatch(url)?.let {
                view.stopLoading()
                view.loadUrl(it)
                return
            }
            inject(view)
        }

        override fun onPageCommitVisible(view: WebView, url: String) = inject(view)

        override fun onPageFinished(view: WebView, url: String) {
            inject(view)
            updateBackCallback()
        }

        // Se llama también en los cambios de URL de la SPA (history.pushState).
        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            shortsToWatch(url)?.let {
                view.loadUrl(it)
                return
            }
            inject(view)
            updateBackCallback()
        }
    }

    private fun inject(view: WebView) {
        if (isInternalYouTube(view.url)) view.evaluateJavascript(injection, null)
    }

    private fun openExternally(uri: Uri) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun buildInjection(): String {
        val css = assets.open("noshorts.css").bufferedReader().use { it.readText() }
        val js = assets.open("noshorts.js").bufferedReader().use { it.readText() }
        return "window.__noShortsCss = ${JSONObject.quote(css)};\n$js"
    }

    /**
     * User agent de Chrome móvil: el de WebView sin la marca "; wv" ni "Version/4.0",
     * para que Google no rechace el login por ser un WebView.
     */
    private fun chromeMobileUserAgent(): String =
        WebSettings.getDefaultUserAgent(this)
            .replace("; wv)", ")")
            .replace(Regex("""Version/\d+(\.\d+)*\s*"""), "")

    companion object {
        private const val HOME = "https://m.youtube.com/"

        @Suppress("DEPRECATION")
        private const val LEGACY_LAYOUT_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        private val SHORTS = Regex("""^/shorts/([A-Za-z0-9_-]+)""")

        // Dominios que se quedan dentro de la app (YouTube y el login de Google).
        private val INTERNAL = listOf(
            Regex("""(^|\.)youtube\.com$"""),
            Regex("""(^|\.)youtu\.be$"""),
            Regex("""(^|\.)youtube-nocookie\.com$"""),
            Regex("""(^|\.)google(\.com?)?(\.[a-z]{2})?$"""),
            Regex("""(^|\.)gstatic\.com$"""),
            Regex("""(^|\.)googleusercontent\.com$"""),
        )

        fun isInternalHost(host: String?): Boolean {
            val h = host?.lowercase() ?: return false
            return INTERNAL.any { it.containsMatchIn(h) }
        }

        fun isInternalYouTube(url: String?): Boolean {
            val host = url?.let { Uri.parse(it).host }?.lowercase() ?: return false
            return host == "youtube.com" || host.endsWith(".youtube.com")
        }

        /** /shorts/ID → https://m.youtube.com/watch?v=ID (o null si no es un Short). */
        fun shortsToWatch(url: String): String? {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return null
            if (host != "youtube.com" && !host.endsWith(".youtube.com")) return null
            val id = SHORTS.find(uri.path ?: return null)?.groupValues?.get(1) ?: return null
            return "https://m.youtube.com/watch?v=$id"
        }
    }
}
