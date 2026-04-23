@file:Suppress("DEPRECATION")

package com.onee.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.onee.browser.databinding.ActivitySettingsBinding
import androidx.core.content.edit

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    private val themeColors = arrayOf("Koyu (Varsayılan)", "Gece Mavisi", "Orman Yeşili", "Kızıl", "Mor")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.headerBar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
            insets
        }

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        binding.switchAdBlock.isChecked = prefs.getBoolean("ad_block", true)
        binding.switchThirdParty.isChecked = prefs.getBoolean("block_third_party", true)
        binding.switchDesktop.isChecked = prefs.getBoolean("desktop_mode", false)

        val themeIndex = prefs.getInt("theme_color", 0)
        binding.tvThemeCurrent.text = themeColors[themeIndex]

        binding.switchAdBlock.setOnCheckedChangeListener { _, c ->
            prefs.edit { putBoolean("ad_block", c) }
            Toast.makeText(this, if (c) "Reklam engelleyici acildi" else "Kapatildi", Toast.LENGTH_SHORT).show()
        }
        binding.switchThirdParty.setOnCheckedChangeListener { _, c ->
            prefs.edit { putBoolean("block_third_party", c) }
            Toast.makeText(this, if (c) "Cerezler engellendi" else "Cerezlere izin verildi", Toast.LENGTH_SHORT).show()
        }
        binding.switchDesktop.setOnCheckedChangeListener { _, c ->
            prefs.edit { putBoolean("desktop_mode", c) }
            Toast.makeText(this, if (c) "Masaustu modu" else "Mobil mod", Toast.LENGTH_SHORT).show()
        }

        binding.rowThemeColor.setOnClickListener {
            AlertDialog.Builder(this, R.style.DarkDialogTheme)
                .setTitle("Tema Rengi Seç")
                .setItems(themeColors) { _, which ->
                    prefs.edit { putInt("theme_color", which) }
                    binding.tvThemeCurrent.text = themeColors[which]
                    Toast.makeText(this, "${themeColors[which]} uygulandı", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        binding.rowClearCache.setOnClickListener {
            prefs.edit { putBoolean("clear_cache_requested", true) }
            Toast.makeText(this, "Onbellek temizlendi", Toast.LENGTH_SHORT).show()
        }

        binding.rowAbout.setOnClickListener {
            AlertDialog.Builder(this, R.style.DarkDialogTheme)
                .setTitle("Onee Browser v2.0")
                .setMessage("Stable Release v2.0\n\nAndroid ${Build.VERSION.RELEASE}\n\nOzellikler:\n- Sekme yonetimi\n- Reklam engelleyici\n- 3. taraf cerez engelleme\n- SSL dogrulama\n- Arama gecmisi")
                .setPositiveButton("Tamam", null).show()
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.")
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, android.R.anim.slide_out_right)
    }
}