package com.trabe.meucaixa

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AlertDialog

object Novidades {
    data class Sec(val id: String, val title: String, val lines: List<String>)

    fun load(ctx: Context): List<Sec> {
        val txt = try {
            ctx.assets.open("novidades.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return emptyList()
        }
        val out = mutableListOf<Sec>()
        var id: String? = null
        var title = ""
        var lines = mutableListOf<String>()
        fun flush() {
            val i = id
            if (i != null) out.add(Sec(i, title, lines.toList()))
        }
        for (raw in txt.lines()) {
            val l = raw.trim()
            when {
                l.startsWith("#id ") -> {
                    flush()
                    id = l.removePrefix("#id ").trim()
                    title = ""
                    lines = mutableListOf()
                }
                l.startsWith("#titulo ") -> title = l.removePrefix("#titulo ").trim()
                l.startsWith("- ") -> lines.add(l.removePrefix("- ").trim())
            }
        }
        flush()
        return out
    }

    private fun body(s: Sec): String = s.lines.joinToString("\n") { "• $it" }

    fun maybeShow(act: Activity, store: Store) {
        val top = load(act).firstOrNull() ?: return
        val last = store.str("novidade")
        if (last == top.id) return
        store.setStr("novidade", top.id)
        if (last == null) return
        AlertDialog.Builder(act)
            .setTitle("O que mudou: " + top.title)
            .setMessage(body(top))
            .setPositiveButton("Legal!", null)
            .show()
    }

    fun showAll(act: Activity) {
        val secs = load(act)
        val msg = if (secs.isEmpty()) "Sem novidades registradas."
        else secs.joinToString("\n\n") { it.title + "\n" + body(it) }
        AlertDialog.Builder(act)
            .setTitle("O que mudou")
            .setMessage(msg)
            .setPositiveButton("Fechar", null)
            .show()
    }
}
