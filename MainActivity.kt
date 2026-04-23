@file:Suppress("DEPRECATION")

package com.onee.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.onee.browser.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    private var statusBarH = 0
    private var isPageLoading = false
    private var activePopup: PopupWindow? = null

    private lateinit var searchAdapter: ArrayAdapter<String>
    private val searchHistory = mutableListOf<String>()

    private val tabsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            TabsActivity.RESULT_SELECT -> {
                val id = result.data?.getStringExtra(TabsActivity.EXTRA_TAB_ID) ?: return@registerForActivityResult
                TabRepository.activeTabId = id
            }
            TabsActivity.RESULT_CLOSE -> {
                val id = result.data?.getStringExtra(TabsActivity.EXTRA_TAB_ID) ?: return@registerForActivityResult
                TabRepository.closeTab(id)
            }
            TabsActivity.RESULT_NEW -> {
                openNewTab()
            }
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results: Array<Uri>? = when {
            result.resultCode != RESULT_OK -> null
            result.data?.dataString != null -> arrayOf(result.data!!.dataString!!.toUri())
            result.data?.clipData != null -> {
                val count = result.data!!.clipData!!.itemCount
                Array(count) { i -> result.data!!.clipData!!.getItemAt(i).uri }
            }
            cameraImageUri != null -> arrayOf(cameraImageUri!!)
            else -> null
        }
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null; cameraImageUri = null
    }

    @SuppressLint("JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.headerContainer) { v, insets ->
            statusBarH = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, statusBarH, v.paddingRight, v.paddingBottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.webView) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }

        TabRepository.init()
        setupWebView()
        setupSearchHistory()
        setupAddressBar()
        setupButtons()
        setupSwipeRefresh()
        updateTabCount()

        val intentUrl = intent?.data?.toString()
        if (intentUrl != null && intentUrl != "about:blank" && intentUrl != "about:blank#") {
            TabRepository.activeTab()?.url = intentUrl
            loadUrl(intentUrl)
        } else {
            loadUrl(TabRepository.activeTab()?.url ?: "onee:home")
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activePopup?.isShowing == true) { activePopup?.dismiss(); return }
                when {
                    binding.etAddressBar.isFocused -> { binding.etAddressBar.clearFocus(); hideKeyboard() }
                    binding.webView.canGoBack() -> binding.webView.goBack()
                    TabRepository.tabs.size > 1 -> {
                        TabRepository.closeTab(TabRepository.activeTabId)
                        loadUrl(TabRepository.activeTab()?.url ?: "onee:home")
                        updateTabCount()
                    }
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })

        binding.webView.addJavascriptInterface(WebAppInterface(this), "android")
    }

    private fun setupSearchHistory() {
        val prefs = getSharedPreferences("search_history", MODE_PRIVATE)
        val set = prefs.getStringSet("queries", emptySet()) ?: emptySet()
        searchHistory.clear()
        searchHistory.addAll(set.sortedByDescending { it })
        searchAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, searchHistory)
        (binding.etAddressBar as AutoCompleteTextView).setAdapter(searchAdapter)
    }

    private fun saveSearchQuery(query: String) {
        if (query.isBlank() || query == "onee:home") return
        val prefs = getSharedPreferences("search_history", MODE_PRIVATE)
        val set = prefs.getStringSet("queries", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.add(query)
        prefs.edit { putStringSet("queries", set.toList().takeLast(50).toSet()) }
        searchHistory.clear()
        searchHistory.addAll(set.sortedByDescending { it })
        searchAdapter.notifyDataSetChanged()
    }

    private fun openNewTab(url: String = "onee:home") {
        val tab = TabRepository.newTab(url)
        tab.title = "Yeni Sekme"
        TabRepository.activeTabId = tab.id
        updateTabCount()
        loadUrl(url)
    }

    private fun updateTabCount() {
        binding.tvTabCount.text = TabRepository.tabs.size.toString()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        // Çerez ve depolama ayarları (sessionStorage hatası için)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().flush()
        }

        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
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
            saveFormData = true
            savePassword = true

            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            userAgentString = if (prefs.getBoolean("desktop_mode", false)) {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            } else {
                "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            }
        }

        val adServers = hashSetOf(
            "doubleclick.net","googleadservices.com","googlesyndication.com",
            "moatads.com","adservice.google.com","quantserve.com",
            "scorecardresearch.com","zedo.com"
        )

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                val host = req.url.host ?: ""
                val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                if (prefs.getBoolean("ad_block", true)) {
                    for (ad in adServers) {
                        if (host.contains(ad))
                            return WebResourceResponse("text/plain","utf-8", java.io.ByteArrayInputStream("".toByteArray()))
                    }
                }
                return super.shouldInterceptRequest(view, req)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isPageLoading = true
                binding.btnRefresh.setImageResource(R.drawable.ic_close)
                binding.progressBar.visibility = View.VISIBLE
                binding.progressBar.progress = 0
                url?.let {
                    updateAddressBar(it)
                    if (it != "about:blank" && !it.startsWith("data:")) {
                        saveToHistory(it)
                    }
                }
                TabRepository.activeTab()?.url = url ?: "onee:home"
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                isPageLoading = false
                binding.btnRefresh.setImageResource(R.drawable.ic_refresh)
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                url?.let { updateAddressBar(it) }
                val title = view.title?.takeIf { it.isNotBlank() } ?: "Yeni Sekme"
                TabRepository.activeTab()?.title = title
                injectCookieRejecter(view)
            }

            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val url = req.url.toString()
                return when {
                    url.startsWith("tel:") -> { startActivity(Intent(Intent.ACTION_DIAL, url.toUri())); true }
                    url.startsWith("mailto:") -> { startActivity(Intent(Intent.ACTION_SENDTO, url.toUri())); true }
                    url.startsWith("intent:") -> {
                        try { startActivity(Intent.parseUri(url, Intent.URI_INTENT_SCHEME)) }
                        catch (_: Exception) { Toast.makeText(this@MainActivity, "Uygulama bulunamadi", Toast.LENGTH_SHORT).show() }
                        true
                    }
                    else -> false
                }
            }

            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) {
                    binding.swipeRefresh.isRefreshing = false
                    view.loadDataWithBaseURL(null, buildErrorPage(err.description?.toString() ?: "Hata"), "text/html", "UTF-8", null)
                }
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) {
                binding.progressBar.progress = p
                if (p >= 100) binding.progressBar.visibility = View.GONE
            }
            override fun onShowFileChooser(wv: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = cb
                val chooser = p.createIntent()
                val cam = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                createImageFile()?.let { f ->
                    cameraImageUri = FileProvider.getUriForFile(this@MainActivity, "${packageName}.fileprovider", f)
                    cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                }
                fileChooserLauncher.launch(Intent.createChooser(chooser, "Dosya Sec").apply {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cam))
                })
                return true
            }
        }

        binding.webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                val req = DownloadManager.Request(url.toUri()).apply {
                    setMimeType(mimetype); setTitle(fileName); setDescription("Indiriliyor...")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    addRequestHeader("User-Agent", userAgent)
                    addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                }
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(this, "Indirme basladi: $fileName", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Indirme baslatılamadi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun injectCookieRejecter(view: WebView) {
        val js = """(function(){
var rt=['reddet','reddedin','decline','reject','deny','refuse','kabul etme',
'sadece zorunlu','only necessary','only essential','reject all','tumu reddet',
'tumunu reddet','yalnizca zorunlu','zorunlu cerezler'];
var els=document.querySelectorAll('button,a,[role="button"],[class*="reject"],[class*="decline"],[id*="reject"],[id*="decline"]');
for(var i=0;i<els.length;i++){
  var t=(els[i].innerText||els[i].value||'').toLowerCase().trim();
  for(var j=0;j<rt.length;j++){if(t.includes(rt[j])){els[i].click();return;}}
}})();"""
        view.evaluateJavascript(js, null)
    }

    private fun setupAddressBar() {
        binding.etAddressBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                val input = binding.etAddressBar.text?.toString()?.trim() ?: ""
                if (input.isNotEmpty()) {
                    saveSearchQuery(input)
                    loadUrl(input)
                }
                true
            } else false
        }
        binding.etAddressBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) { binding.etAddressBar.hint = ""; binding.etAddressBar.selectAll() }
            else binding.etAddressBar.hint = getString(R.string.address_hint)
        }
        (binding.etAddressBar as AutoCompleteTextView).setOnItemClickListener { _, _, _, _ ->
            val query = binding.etAddressBar.text.toString()
            if (query.isNotBlank()) {
                saveSearchQuery(query)
                loadUrl(query)
            }
        }
    }

    private fun setupButtons() {
        binding.btnHome.setOnClickListener { loadUrl("onee:home") }

        binding.btnRefresh.setOnClickListener {
            if (isPageLoading) {
                binding.webView.stopLoading()
                isPageLoading = false
                binding.btnRefresh.setImageResource(R.drawable.ic_refresh)
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            } else {
                binding.webView.reload()
            }
        }

        binding.btnNewTab.setOnClickListener {
            openNewTab()
            Toast.makeText(this, "Yeni sekme acildi", Toast.LENGTH_SHORT).show()
        }

        binding.btnTabCount.setOnClickListener {
            TabRepository.activeTab()?.url = binding.webView.url ?: TabRepository.activeTab()?.url ?: "onee:home"
            TabRepository.activeTab()?.title = binding.webView.title ?: TabRepository.activeTab()?.title ?: "Sekme"
            tabsLauncher.launch(Intent(this, TabsActivity::class.java))
        }

        binding.btnMenu.setOnClickListener { v ->
            if (activePopup?.isShowing == true) activePopup?.dismiss()
            else showRoundedMenu(v)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            binding.webView.reload()
        }
        binding.swipeRefresh.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
    }

    @SuppressLint("InflateParams")
    private fun showRoundedMenu(anchor: View) {
        val menuView = LayoutInflater.from(this).inflate(R.layout.popup_menu, null)
        val popup = PopupWindow(menuView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.elevation = 24f
        popup.isOutsideTouchable = true
        popup.setOnDismissListener { activePopup = null }
        activePopup = popup

        menuView.findViewById<TextView>(R.id.menuShare).setOnClickListener {
            popup.dismiss(); shareUrl(binding.webView.url ?: "")
        }
        menuView.findViewById<TextView>(R.id.menuDesktop).setOnClickListener {
            popup.dismiss(); toggleDesktopMode()
        }
        menuView.findViewById<TextView>(R.id.menuHistory).setOnClickListener {
            popup.dismiss()
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        menuView.findViewById<TextView>(R.id.menuSettings).setOnClickListener {
            popup.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        popup.showAtLocation(binding.root, Gravity.NO_GRAVITY,
            loc[0] - 220 + anchor.width, loc[1] + anchor.height + 8)

        menuView.scaleX = 0.7f; menuView.scaleY = 0.7f; menuView.alpha = 0f
        menuView.pivotX = menuView.width.toFloat(); menuView.pivotY = 0f
        menuView.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(200).setInterpolator(OvershootInterpolator(1.8f)).start()
    }

    private fun loadUrl(input: String) {
        var finalUrl = input
        if (finalUrl == "about:blank" || finalUrl.isBlank() || finalUrl == "about:blank#") {
            finalUrl = "onee:home"
        }
        if (finalUrl == "onee:home") {
            binding.webView.loadDataWithBaseURL("https://home", buildHomePage(), "text/html", "UTF-8", null)
            binding.etAddressBar.setText("")
            TabRepository.activeTab()?.let { it.url = "onee:home"; it.title = "Ana Sayfa" }
            return
        }
        val url = when {
            finalUrl.startsWith("http://") || finalUrl.startsWith("https://") -> finalUrl
            finalUrl.matches(Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")) -> "https://$finalUrl"
            else -> "https://www.google.com/search?q=${Uri.encode(finalUrl)}"
        }
        binding.webView.loadUrl(url)
        hideKeyboard()
    }

    inner class WebAppInterface(private val activity: MainActivity) {
        @JavascriptInterface
        fun search(query: String) {
            runOnUiThread {
                if (query.isNotBlank()) {
                    activity.saveSearchQuery(query)
                    activity.loadUrl(query)
                }
            }
        }
    }

    private fun updateAddressBar(url: String) {
        if (binding.etAddressBar.isFocused) return
        if (url == "https://home/" || url.startsWith("data:")) {
            binding.etAddressBar.setText("")
            binding.etAddressBar.hint = "Onee Browser"
            return
        }
        binding.etAddressBar.hint = getString(R.string.address_hint)
        val display = try {
            val h = url.toUri().host ?: url
            if (h.startsWith("www.")) h.substring(4) else h
        } catch (_: Exception) { url }
        binding.etAddressBar.setText(display)
    }

    private fun saveToHistory(url: String) {
        if (url == "about:blank" || url.startsWith("data:") || url.contains("https://home")) return
        val prefs = getSharedPreferences("history", MODE_PRIVATE)
        val set = prefs.getStringSet("urls", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.removeAll { it.substringAfter("|") == url }
        set.add("${System.currentTimeMillis()}|$url")
        val trimmed = set.sortedByDescending { it.substringBefore("|").toLongOrNull() ?: 0L }.take(200).toSet()
        prefs.edit { putStringSet("urls", trimmed) }
    }

    private fun shareUrl(url: String) {
        if (url.isBlank()) return
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }, "Paylas"
        ))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun toggleDesktopMode() {
        val s = binding.webView.settings
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (!s.userAgentString.contains("Windows NT")) {
            s.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            s.useWideViewPort = true; s.loadWithOverviewMode = true
            prefs.edit { putBoolean("desktop_mode", true) }
            Toast.makeText(this, "Masaustu Modu", Toast.LENGTH_SHORT).show()
        } else {
            s.userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; Mobile) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
            s.useWideViewPort = false; s.loadWithOverviewMode = false
            prefs.edit { putBoolean("desktop_mode", false) }
            Toast.makeText(this, "Mobil Mod", Toast.LENGTH_SHORT).show()
        }
        binding.webView.reload()
    }

    private fun buildHomePage(): String {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val themeColor = prefs.getInt("theme_color", 0)
        val bg = when (themeColor) {
            1 -> "linear-gradient(135deg,#0a0e27,#1a2a6c,#0d0d2b)"
            2 -> "linear-gradient(135deg,#0d1f0d,#1a3a1a,#0f2d0f)"
            3 -> "linear-gradient(135deg,#1a0d0d,#3a1a0a,#2a1515)"
            4 -> "linear-gradient(135deg,#150d1f,#2a1a3a,#1a0d2d)"
            else -> "#0d0d0d"
        }
        return """<!DOCTYPE html><html>
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>*{margin:0;padding:0;box-sizing:border-box}
body{background:$bg;color:#e0e0e0;font-family:-apple-system,sans-serif;
display:flex;flex-direction:column;align-items:center;justify-content:center;
min-height:100vh;text-align:center;padding:24px;animation:fi .4s ease}
@keyframes fi{from{opacity:0;transform:translateY(12px)}to{opacity:1;transform:translateY(0)}}
.logo{font-size:34px;font-weight:bold;margin-bottom:28px;color:#fff;letter-spacing:-1px}
.sc{width:100%;max-width:400px;margin-bottom:40px}
.si{width:100%;background:rgba(255,255,255,.08);border:1px solid rgba(255,255,255,.15);
border-radius:25px;padding:12px 20px;color:#fff;font-size:16px;outline:none;
transition:border-color .25s,background .25s,box-shadow .25s}
.si:focus{border-color:rgba(255,255,255,.4);background:rgba(255,255,255,.12)}
.sh{display:flex;justify-content:center;gap:40px}
.s{display:flex;flex-direction:column;align-items:center;text-decoration:none;transition:transform .2s}
.s:active{transform:scale(.9)}
.si2{width:60px;height:60px;background:rgba(255,255,255,.08);border-radius:16px;
display:flex;align-items:center;justify-content:center;font-size:24px;margin-bottom:8px;
color:#fff;font-weight:bold;border:1px solid rgba(255,255,255,.12)}
.sl{font-size:13px;color:#aaa}
</style></head>
<body>
<div class="logo">Onee Browser</div>
<div class="sc"><input type="text" class="si" placeholder="Google'da ara veya adres yaz"
onkeydown="if(event.key==='Enter')android.search(this.value)"></div>
<div class="sh">
<a href="https://www.hltv.org/" class="s"><div class="si2" style="color:#3399ff">H</div><div class="sl">HLTV</div></a>
<a href="https://www.epey.com/" class="s"><div class="si2" style="color:#ff9900">E</div><div class="sl">Epey</div></a>
</div></body></html>"""
    }

    private fun buildErrorPage(error: String) = """<!DOCTYPE html><html>
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>*{margin:0;padding:0}body{background:#0d0d0d;color:#e0e0e0;font-family:sans-serif;
display:flex;flex-direction:column;align-items:center;justify-content:center;
min-height:100vh;text-align:center;padding:24px}
h1{font-size:20px;color:#fff;margin-bottom:10px}p{font-size:13px;color:#777;max-width:280px}
</style></head>
<body><h1>Baglanti Kurulamadi</h1><p>$error</p></body></html>"""

    private fun createImageFile(): File? = try {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (storageDir?.exists() == false) storageDir.mkdirs()
        File.createTempFile("IMG_${ts}_", ".jpg", storageDir)
    } catch (_: Exception) { null }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.etAddressBar.clearFocus()
    }

    override fun onResume() {
        super.onResume()
        val activeTab = TabRepository.activeTab()
        if (activeTab != null && binding.webView.url != activeTab.url) {
            loadUrl(activeTab.url)
        }
        updateTabCount()

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("clear_cache_requested", false)) {
            binding.webView.clearCache(true)
            prefs.edit { putBoolean("clear_cache_requested", false) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val url = intent.data?.toString()
        if (url != null && url != "about:blank" && url != "about:blank#") {
            loadUrl(url)
        } else {
            loadUrl("onee:home")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); binding.webView.saveState(outState) }
    override fun onRestoreInstanceState(s: Bundle) { super.onRestoreInstanceState(s); binding.webView.restoreState(s) }
    override fun onDestroy() {
        binding.webView.apply { loadUrl("about:blank"); stopLoading(); clearHistory(); destroy() }
        super.onDestroy()
    }
}