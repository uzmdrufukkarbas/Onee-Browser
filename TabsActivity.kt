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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.onee.browser.databinding.ActivityTabsBinding

class TabsAdapter(
    private val tabs: List<TabData>,
    private val activeId: String,
    private val onSelect: (String) -> Unit,
    private val onClose: (String) -> Unit
) : RecyclerView.Adapter<TabsAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvTabTitle)
        val url: TextView = v.findViewById(R.id.tvTabUrl)
        val close: ImageButton = v.findViewById(R.id.btnCloseTab)
        val indicator: View = v.findViewById(R.id.activeIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false))

    override fun getItemCount() = tabs.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val t = tabs[pos]
        h.title.text = t.title.ifBlank { "Yeni Sekme" }
        h.url.text = t.url
        h.indicator.visibility = if (t.id == activeId) View.VISIBLE else View.INVISIBLE
        h.itemView.setOnClickListener { onSelect(t.id) }
        h.close.setOnClickListener { onClose(t.id) }
    }
}

class TabsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTabsBinding

    companion object {
        const val RESULT_SELECT = 100
        const val RESULT_CLOSE = 101
        const val RESULT_NEW = 102
        const val EXTRA_TAB_ID = "tab_id"
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        binding = ActivityTabsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.recyclerTabs.layoutManager = LinearLayoutManager(this)
        binding.recyclerTabs.adapter = TabsAdapter(
            TabRepository.tabs,
            TabRepository.activeTabId,
            onSelect = { id ->
                setResult(RESULT_SELECT, Intent().putExtra(EXTRA_TAB_ID, id))
                finish()
            },
            onClose = { id ->
                TabRepository.closeTab(id)
                binding.recyclerTabs.adapter?.notifyDataSetChanged()
                if (TabRepository.tabs.size == 1) {
                    setResult(RESULT_SELECT, Intent().putExtra(EXTRA_TAB_ID, TabRepository.activeTabId))
                    finish()
                }
            }
        )

        binding.btnNewTabFromList.setOnClickListener {
            setResult(RESULT_NEW)
            finish()
        }

        binding.btnBack.setOnClickListener { finish() }
    }
}
