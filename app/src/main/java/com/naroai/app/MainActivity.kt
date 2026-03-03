package com.naroai.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.webkit.*
import android.webkit.WebView.WebViewTransport
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // 文件选择器启动器
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        filePathCallback?.onReceiveValue(uri?.let { arrayOf(it) })
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查地区
        if (isMainlandChina()) {
            Toast.makeText(this, "The application is only available outside Mainland China.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // 允许在 PC 端的 Chrome 浏览器中调试 (chrome://inspect)
        // 开启此项后，你可以查看 IndexedDB 内部是否真的存入了数据
        WebView.setWebContentsDebuggingEnabled(true)

        webView = findViewById(R.id.webview)

        setupWebView()
        setupDownloadListener()

        webView.loadUrl("https://www.naroai.top/character-cards")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun isMainlandChina(): Boolean {
        val locale = Locale.getDefault()
        return locale.country.equals("CN", ignoreCase = true) || 
               (locale.language.equals("zh", ignoreCase = true) && locale.country.equals("CN", ignoreCase = true))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            // --- 核心：确保 IndexedDB 和 API 正常工作 ---
            javaScriptEnabled = true
            domStorageEnabled = true    // 必须：用于 localStorage 和 IndexedDB
            databaseEnabled = true      // 必须：在 Android WebView 中 IndexedDB 依赖此项开启底层存储支持
            
            // 优化：允许混合内容加载（防止 HTTPS 页面中的 API 请求因证书或同源策略被截断）
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            
            // 优化：跨域访问权限（针对嵌套 Iframe 之间的 IndexDB 互操作）
            allowFileAccess = true
            allowContentAccess = true
            
            // --- 性能与适配 ---
            offscreenPreRaster = true 
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            
            // 必须：支持多窗口，确保 Iframe 内的 JS 逻辑闭环
            setSupportMultipleWindows(true)
            
            // 设置标准 User Agent
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        // 必须：第三方 Cookie 支持，许多 API 依赖 Cookie 进行会话恢复
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush() // 强制将状态写入磁盘
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                val transport = resultMsg?.obj as? WebViewTransport
                transport?.webView = view
                resultMsg?.sendToTarget()
                return true
            }

            override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                this@MainActivity.filePathCallback = filePathCallback
                filePickerLauncher.launch("*/*")
                return true
            }
        }
        
        // 开启硬件加速以确保 JS 执行性能
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
    }

    private fun setupDownloadListener() {
        webView.setDownloadListener { url, _, contentDisposition, mimetype, _ ->
            try {
                val request = DownloadManager.Request(url.toUri())
                request.setMimeType(mimetype)
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                request.setTitle(fileName)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(applicationContext, "Download started: $fileName", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CookieManager.getInstance().flush()
    }
}
