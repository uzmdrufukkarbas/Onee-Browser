package com.onee.browser

import java.util.UUID

data class TabData(
    val id: String = UUID.randomUUID().toString(),
    var url: String = "onee:home",
    var title: String = "Yeni Sekme"
)

object TabRepository {
    val tabs = mutableListOf<TabData>()
    var activeTabId: String = ""

    fun init() {
        if (tabs.isEmpty()) {
            val t = TabData()
            tabs.add(t)
            activeTabId = t.id
        }
    }

    fun activeTab(): TabData? = tabs.find { it.id == activeTabId }

    fun newTab(url: String = "onee:home"): TabData {
        val t = TabData(url = url, title = "Yeni Sekme")
        tabs.add(t)
        return t
    }

    fun closeTab(id: String): String? {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return null
        tabs.removeAt(idx)
        if (tabs.isEmpty()) {
            val t = TabData()
            tabs.add(t)
            activeTabId = t.id
            return activeTabId
        }
        val newIdx = if (idx >= tabs.size) tabs.size - 1 else idx
        activeTabId = tabs[newIdx].id
        return activeTabId
    }
}
