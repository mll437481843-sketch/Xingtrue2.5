package com.ncmd

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings as SysSettings
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {

    companion object {
        @Volatile var current: MainActivity? = null
    }

    lateinit var web: WebView
    private var bridge: Bridge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        Settings.init(this)
        FileOps.init(this)
        NeteaseApi.cookieProvider = { Settings.cookie }
        Log.init(this)
        TaskManager.init(this)
        try { TaskManager.recover() } catch (e: Throwable) { Log.e("app", "recover", e) }

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#0d0f12"))
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.allowFileAccessFromFileURLs = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.loadWithOverviewMode = false
        s.useWideViewPort = false
        s.textZoom = 100
        if (Build.VERSION.SDK_INT >= 21) s.mediaPlaybackRequiresUserGesture = false
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                bridge?.emit("ready", "{}")
            }
        }
        web.webChromeClient = WebChromeClient()
        bridge = Bridge(this, web)
        web.addJavascriptInterface(bridge!!, "Native")
        setContentView(web)
        web.loadUrl("file:///android_asset/ui/index.html")
        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val perms = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (perms.isNotEmpty()) try { requestPermissions(perms.toTypedArray(), 1001) } catch (e: Throwable) { Log.e("app", "perm", e) }
    }

    fun hasAllFiles(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

    fun askAllFiles() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val i = Intent(SysSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + packageName))
                startActivity(i)
            }
        } catch (e: Throwable) {
            try { startActivity(Intent(SysSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            catch (e2: Throwable) { Log.e("app", "askAllFiles", e2) }
        }
    }

    override fun onBackPressed() {
        val w = web
        w.evaluateJavascript("(window.__onBack&&window.__onBack())||false") { r ->
            if (r == null || r == "false" || r == "null") finish()
        }
    }

    override fun onDestroy() {
        current = null
        try { web.destroy() } catch (e: Throwable) {}
        super.onDestroy()
    }
}
