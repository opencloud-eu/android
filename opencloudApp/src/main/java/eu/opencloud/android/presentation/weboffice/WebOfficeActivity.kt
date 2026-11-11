/**
 * openCloud Android client application
 *
 * Shows a web office editor (e.g. Euro-Office) full screen inside the app,
 * instead of handing the user off to a browser tab.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 */

package eu.opencloud.android.presentation.weboffice

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder

class WebOfficeActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var editorHost: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val payload = intent.getStringExtra(EXTRA_PAYLOAD)
        if (payload == null) {
            finish()
            return
        }

        val root = FrameLayout(this)
        webView = WebView(this)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }
        root.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(progress, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        supportActionBar?.hide()

        // keep the editor out from under the status / navigation bars
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            WindowInsetsCompat.CONSUMED
        }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                // stay inside the editor; anything else (help links, hyperlinks in a doc) goes to a browser tab
                return if (editorHost != null && url.host != editorHost && request.isForMainFrame) {
                    CustomTabsIntent.Builder().build().launchUrl(this@WebOfficeActivity, url)
                    true
                } else {
                    false
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // "insert image" etc. in the editor
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                return try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(params.createIntent(), REQUEST_FILE_CHOOSER)
                    true
                } catch (e: Exception) {
                    fileChooserCallback = null
                    false
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // the editor is a single page app: back closes it (changes autosave server-side)
                finish()
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            loadEditor(payload)
        }
    }

    private fun loadEditor(payload: String) {
        try {
            val json = JSONObject(payload)
            val appUrl = json.getString("app_url")
            val method = json.optString("method", "GET").uppercase()
            val params = json.optJSONObject("form_parameters")
            editorHost = Uri.parse(appUrl).host

            val form = buildString {
                params?.keys()?.forEach { key ->
                    if (isNotEmpty()) append('&')
                    append(URLEncoder.encode(key, "UTF-8")).append('=')
                    append(URLEncoder.encode(params.getString(key), "UTF-8"))
                }
            }

            if (method == "POST") {
                webView.postUrl(appUrl, form.toByteArray())
            } else {
                val sep = if (appUrl.contains('?')) '&' else '?'
                webView.loadUrl(if (form.isEmpty()) appUrl else "$appUrl$sep$form")
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not open web office editor")
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FILE_CHOOSER) {
            fileChooserCallback?.onReceiveValue(
                if (resultCode == Activity.RESULT_OK) WebChromeClient.FileChooserParams.parseResult(resultCode, data) else null
            )
            fileChooserCallback = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PAYLOAD = "web_office_payload"
        private const val REQUEST_FILE_CHOOSER = 4711

        /** true if [data] is an /app/open payload (JSON) rather than a plain browser URL */
        fun isEditorPayload(data: String?): Boolean = data?.trimStart()?.startsWith("{") == true

        /** Opens the editor in-app for a JSON payload, or falls back to a browser tab for a plain URL. */
        fun open(context: Context, data: String) {
            if (isEditorPayload(data)) {
                context.startActivity(Intent(context, WebOfficeActivity::class.java).putExtra(EXTRA_PAYLOAD, data))
            } else {
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(data))
            }
        }
    }
}
