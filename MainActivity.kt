package com.onee.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.onee.browser.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Dosya seçici için callback
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results: Array<Uri>? = when {
            result.resultCode != RESULT_OK -> null
            result.data?.dataString != null -> arrayOf(Uri.parse(result.data!!.dataString))
            result.data?.clipData != null -> {
                val count = result.data!!.clipData!!.itemCount
                Array(count) { i -> result.data!!.clipData!!.getItemAt(i).uri }
            }
            cameraImageUri != null -> arrayOf(cameraImageUri!!)
            else -> null
        }
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
        cameraImageUri = null
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* izin sonuçları */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge karanlık tema
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // Durum çubuğu ikonlarını açık yap (koyu arka plan için)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissions()
        setupWebView()
        setupAddressBar()
        setupButtons()

        // Intent URL'si ya da ana sayfa
        val intentUrl = intent?.data?.toString()
        loadUrl(intentUrl ?: "https://www.google.com")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            mediaPlaybackRequiresUserGesture = false
            userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; Mobile) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        }

        binding.webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.visibility = View.VISIBLE
                binding.progressBar.progress = 0
                updateNavButtons()
                url?.let { updateAddressBar(it) }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = View.GONE
                updateNavButtons()
                url?.let { updateAddressBar(it) }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return when {
                    url.startsWith("tel:") -> {
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(url)))
                        true
                    }
                    url.startsWith("mailto:") -> {
                        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(url)))
                        true
                    }
                    url.startsWith("intent:") -> {
                        try {
                            startActivity(Intent.parseUri(url, Intent.URI_INTENT_SCHEME))
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Uygulama bulunamadı", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    else -> false
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    view.loadDataWithBaseURL(
                        null,
                        buildErrorPage(error.description?.toString() ?: "Bağlantı hatası"),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }

            @Deprecated("Deprecated in API level 23")
            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    view.loadDataWithBaseURL(
                        null,
                        buildErrorPage(description ?: "Bağlantı hatası"),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val chooserIntent = fileChooserParams.createIntent()

                // Kamera seçeneği ekle
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                createImageFile()?.let { file ->
                    cameraImageUri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.fileprovider",
                        file
                    )
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                }

                val finalIntent = Intent.createChooser(chooserIntent, "Dosya Seç").apply {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                }
                fileChooserLauncher.launch(finalIntent)
                return true
            }
        }

        // İndirme yöneticisi
        binding.webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimetype)
                    setTitle(fileName)
                    setDescription("İndiriliyor...")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    addRequestHeader("User-Agent", userAgent)
                    addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                }
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "İndirme başladı: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "İndirme başlatılamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupAddressBar() {
        binding.etAddressBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                val input = binding.etAddressBar.text?.toString()?.trim() ?: ""
                if (input.isNotEmpty()) {
                    loadUrl(input)
                }
                true
            } else false
        }

        // Odaklanıldığında hint'i temizle
        binding.etAddressBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.etAddressBar.hint = ""
                binding.etAddressBar.selectAll()
            } else {
                binding.etAddressBar.hint = getString(R.string.address_hint)
            }
        }
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack()
        }

        binding.btnRefresh.setOnClickListener {
            if (binding.progressBar.visibility == View.VISIBLE) {
                binding.webView.stopLoading()
                binding.progressBar.visibility = View.GONE
                binding.btnRefresh.setImageResource(R.drawable.ic_refresh)
            } else {
                binding.webView.reload()
            }
        }

        binding.btnMenu.setOnClickListener {
            showMenuDialog()
        }
    }

    private fun loadUrl(input: String) {
        val url = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.matches(Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")) -> "https://$input"
            else -> "https://www.google.com/search?q=${Uri.encode(input)}"
        }
        binding.webView.loadUrl(url)
        hideKeyboard()
    }

    private fun updateAddressBar(url: String) {
        if (!binding.etAddressBar.isFocused) {
            // Sadece domain göster
            val displayUrl = try {
                val uri = Uri.parse(url)
                val host = uri.host ?: url
                if (host.startsWith("www.")) host.substring(4) else host
            } catch (e: Exception) { url }
            binding.etAddressBar.setText(displayUrl)
        }
    }

    private fun updateNavButtons() {
        binding.btnBack.alpha = if (binding.webView.canGoBack()) 1f else 0.35f
        binding.btnBack.isEnabled = binding.webView.canGoBack()

        // Yüklenme durumuna göre ikonunu değiştir
        val isLoading = binding.progressBar.visibility == View.VISIBLE
        binding.btnRefresh.setImageResource(
            if (isLoading) R.drawable.ic_close else R.drawable.ic_refresh
        )
    }

    private fun showMenuDialog() {
        val currentUrl = binding.webView.url ?: "https://www.google.com"
        val options = arrayOf(
            "🏠  Ana Sayfa",
            "🔖  Paylaş",
            "🖥️  Masaüstü Sürümü",
            "⬇️  Sayfayı Yenile",
            "ℹ️  Hakkında"
        )
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> loadUrl("https://www.google.com")
                    1 -> shareUrl(currentUrl)
                    2 -> toggleDesktopMode()
                    3 -> binding.webView.reload()
                    4 -> showAbout()
                }
            }
            .show()
    }

    private fun shareUrl(url: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, "Paylaş"))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun toggleDesktopMode() {
        val settings = binding.webView.settings
        val isDesktop = settings.userAgentString.contains("Mobile").not()
        settings.userAgentString = if (isDesktop) {
            "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        } else {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        }
        settings.useWideViewPort = !isDesktop
        binding.webView.reload()
        Toast.makeText(this, if (isDesktop) "Mobil Mod" else "Masaüstü Mod", Toast.LENGTH_SHORT).show()
    }

    private fun showAbout() {
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("Onee Browser")
            .setMessage("Sürüm 1.0\n\nAndroid ${Build.VERSION.RELEASE} için optimize edilmiştir.")
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun buildErrorPage(error: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    background: #0d0d0d;
                    color: #e0e0e0;
                    font-family: -apple-system, sans-serif;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    min-height: 100vh;
                    text-align: center;
                    padding: 24px;
                }
                .icon { font-size: 64px; margin-bottom: 24px; opacity: 0.6; }
                h1 { font-size: 22px; font-weight: 600; margin-bottom: 12px; color: #fff; }
                p { font-size: 14px; color: #888; line-height: 1.6; max-width: 280px; }
                .error-code {
                    margin-top: 16px;
                    font-size: 11px;
                    color: #444;
                    font-family: monospace;
                }
            </style>
        </head>
        <body>
            <div class="icon">⚡</div>
            <h1>Bağlantı Kurulamadı</h1>
            <p>İnternet bağlantınızı kontrol edin ve tekrar deneyin.</p>
            <p class="error-code">$error</p>
        </body>
        </html>
    """.trimIndent()

    private fun createImageFile(): File? = try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        File.createTempFile("IMG_${timestamp}_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
    } catch (e: Exception) { null }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.etAddressBar.clearFocus()
    }

    private fun requestPermissions() {
        val perms = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (binding.etAddressBar.isFocused) {
                binding.etAddressBar.clearFocus()
                hideKeyboard()
                return true
            }
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        binding.webView.restoreState(savedInstanceState)
    }

    override fun onDestroy() {
        binding.webView.apply {
            loadUrl("about:blank")
            stopLoading()
            clearHistory()
            destroy()
        }
        super.onDestroy()
    }
}
