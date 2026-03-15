package com.kvideo.tv

import android.annotation.SuppressLint
import android.os.Bundle
import android.app.UiModeManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    companion object {
        /**
         * The URL of your deployed KVideo instance.
         * Change this to your own domain or IP address.
         */
        private const val KVIDEO_URL = "https://kv.wsys.eu.org"
    }

    private lateinit var webView: WebView
    private lateinit var fullscreenContainer: FrameLayout

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Lock landscape on Android TV only; allow rotation on phones/tablets.
        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        val isTv = (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION)
        if (isTv) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        super.onCreate(savedInstanceState)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Default: hide bars (nice for TV/players); during video fullscreen we handle it explicitly too.
        hideSystemBars()

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webview)
        fullscreenContainer = findViewById(R.id.fullscreen_container)

        webView.apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                databaseEnabled = true
            }

            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null) return
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }

                    // Save state
                    originalOrientation = requestedOrientation
                    customView = view
                    customViewCallback = callback

                    // Phone: force landscape in system fullscreen.
                    // TV: already landscape (or locked elsewhere).
                    val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
                    val isTv = (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION)
                    if (!isTv) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }

                    // Show fullscreen container
                    fullscreenContainer.visibility = View.VISIBLE
                    fullscreenContainer.addView(
                        view,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )

                    // Hide system bars
                    hideSystemBars()
                }

                override fun onHideCustomView() {
                    val view = customView ?: return

                    fullscreenContainer.removeView(view)
                    fullscreenContainer.visibility = View.GONE

                    customView = null
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null

                    // Restore orientation
                    requestedOrientation = originalOrientation

                    // Restore bars
                    showSystemBars()
                }
            }

            loadUrl(KVIDEO_URL)
        }
    }

    private fun hideSystemBars() {
        // Backwards-compatible system bars control
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun showSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Map D-pad center to Enter for spatial navigation
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}

