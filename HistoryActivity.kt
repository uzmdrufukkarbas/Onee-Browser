@file:Suppress("DEPRECATION")

package com.onee.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.onee.browser.databinding.ActivityHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.edit

// Geçmiş veri modeli
data class HistoryEntry(val url: String, val timestamp: Long)

// Geçmiş listesi adaptörü
class HistoryAdapter(
    private val items: MutableList<HistoryEntry>,
    private val onClick: (String) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvUrl: TextView = v.findViewById(R.id.tvUrl)
        val tvTime: TextView = v.findViewById(R.id.tvTime)
        val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val e = items[pos]
        h.tvUrl.text = e.url
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        h.tvTime.text = sdf.format(Date(e.timestamp))
        
        h.itemView.setOnClickListener { onClick(e.url) }
        h.btnDelete.setOnClickListener { onDelete(h.adapterPosition) }
    }

    override fun getItemCount() = items.size
}

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val entries = mutableListOf<HistoryEntry>()
    private lateinit var adapter: HistoryAdapter

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Kenardan kenara görünüm ve sistem çubukları ayarları
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Status bar boşluğu (padding)
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerBar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
            insets
        }

        loadHistory()

        adapter = HistoryAdapter(
            items = entries,
            onClick = { url ->
                val intent = Intent(this, MainActivity::class.java).apply {
                    data = url.toUri()
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
                finish()
            },
            onDelete = { pos ->
                if (pos >= 0 && pos < entries.size) {
                    entries.removeAt(pos)
                    adapter.notifyItemRemoved(pos)
                    saveHistory()
                    if (entries.isEmpty()) binding.tvEmpty.visibility = View.VISIBLE
                }
            }
        )

        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        if (entries.isEmpty()) binding.tvEmpty.visibility = View.VISIBLE

        binding.btnBack.setOnClickListener { finish() }

        // Tümünü temizle
        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(this, R.style.DarkDialogTheme)
                .setTitle("Geçmişi Temizle")
                .setMessage("Tüm geçmiş silinsin mi?")
                .setPositiveButton("Sil") { _, _ ->
                    entries.clear()
                    adapter.notifyDataSetChanged()
                    saveHistory()
                    binding.tvEmpty.visibility = View.VISIBLE
                }
                .setNegativeButton("İptal", null)
                .show()
        }
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences("history", MODE_PRIVATE)
        val set = prefs.getStringSet("urls", emptySet()) ?: emptySet()
        entries.clear()
        entries.addAll(
            set.mapNotNull { s ->
                val parts = s.split("|", limit = 2)
                if (parts.size == 2) HistoryEntry(parts[1], parts[0].toLongOrNull() ?: 0L)
                else null
            }.sortedByDescending { it.timestamp }
        )
    }

    private fun saveHistory() {
        val prefs = getSharedPreferences("history", MODE_PRIVATE)
        val set = entries.map { "${it.timestamp}|${it.url}" }.toSet()
        prefs.edit { putStringSet("urls", set) }
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, android.R.anim.slide_out_right)
    }
}
