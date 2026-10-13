package com.trabe.meucaixa

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class Item(
    val id: String,
    val income: Boolean,
    val title: String,
    val cents: Long,
    val y: Int,
    val m: Int,
    val d: Int,
    val done: Boolean,
    val category: String,
    val recurring: Boolean,
    val series: String
) {
    val ym: Int get() = y * 100 + m
    val key: Int get() = ym * 100 + d
}

data class Stats(val got: Long, val toGet: Long, val paid: Long, val toPay: Long)

data class Period(val id: String, val name: String, val start: Int, val end: Int)

object Dates {
    val months = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )

    fun daysIn(ym: Int): Int {
        val c = Calendar.getInstance()
        c.clear()
        c.set(ym / 100, ym % 100 - 1, 1)
        return c.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun add(ym: Int, n: Int): Int {
        var y = ym / 100
        var m = ym % 100 - 1 + n
        y += Math.floorDiv(m, 12)
        m = Math.floorMod(m, 12)
        return y * 100 + m + 1
    }

    fun nowYm(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) + 1
    }

    fun today(): Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

    fun label(ym: Int): String = months[ym % 100 - 1] + " " + (ym / 100)

    fun keyLabel(k: Int): String = "%02d/%02d/%d".format(k % 100, (k / 100) % 100, k / 10000)

    fun todayKey(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }

    private fun toMillis(k: Int): Long {
        val c = Calendar.getInstance()
        c.clear()
        c.set(k / 10000, (k / 100) % 100 - 1, k % 100)
        return c.timeInMillis
    }

    fun daysBetween(a: Int, b: Int): Int = Math.round((toMillis(b) - toMillis(a)) / 86400000.0).toInt()
}

object Fmt {
    private val nf: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    fun money(c: Long): String = nf.format(c / 100.0)

    fun plain(c: Long): String = "%d.%02d".format(c / 100, c % 100)

    fun parse(s: String): Long? {
        var t = s.trim().replace("R$", "").replace(" ", "")
        if (t.isEmpty()) return null
        if (t.contains(',')) {
            t = t.replace(".", "").replace(',', '.')
        } else {
            val first = t.indexOf('.')
            if (first >= 0 && t.length - first - 1 == 3 && first == t.lastIndexOf('.')) {
                t = t.replace(".", "")
            }
        }
        val bd = t.toBigDecimalOrNull() ?: return null
        return bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
    }
}

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("caixa", Context.MODE_PRIVATE)
    val items = mutableListOf<Item>()
    val ends = mutableMapOf<String, Int>()
    val skips = mutableSetOf<String>()
    val periods = mutableListOf<Period>()
    var start = 0L

    init {
        load()
    }

    fun flag(k: String): Boolean = sp.getBoolean("f_$k", false)
    fun setFlag(k: String, v: Boolean) {
        sp.edit().putBoolean("f_$k", v).apply()
    }

    fun str(k: String): String? = sp.getString("s_$k", null)
    fun setStr(k: String, v: String) {
        sp.edit().putString("s_$k", v).apply()
    }

    fun newId(): String = UUID.randomUUID().toString()

    private fun load() {
        val raw = sp.getString("data", null) ?: return
        try {
            val o = JSONObject(raw)
            start = o.optLong("start", 0L)
            val a = o.optJSONArray("items") ?: JSONArray()
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                items.add(
                    Item(
                        j.getString("id"), j.getBoolean("income"), j.getString("title"),
                        j.getLong("cents"), j.getInt("y"), j.getInt("m"), j.getInt("d"),
                        j.getBoolean("done"), j.getString("cat"), j.getBoolean("rec"),
                        j.getString("series")
                    )
                )
            }
            val e = o.optJSONObject("ends")
            if (e != null) {
                for (k in e.keys()) ends[k] = e.getInt(k)
            }
            val s = o.optJSONArray("skips")
            if (s != null) {
                for (i in 0 until s.length()) skips.add(s.getString(i))
            }
            val pa = o.optJSONArray("periods")
            if (pa != null) {
                for (i in 0 until pa.length()) {
                    val j = pa.getJSONObject(i)
                    periods.add(Period(j.getString("id"), j.getString("name"), j.getInt("s"), j.getInt("e")))
                }
            }
        } catch (ex: Exception) {
            // dados corrompidos: começa vazio
        }
    }

    fun save() {
        val o = JSONObject()
        o.put("start", start)
        val a = JSONArray()
        for (x in items) {
            val j = JSONObject()
            j.put("id", x.id)
            j.put("income", x.income)
            j.put("title", x.title)
            j.put("cents", x.cents)
            j.put("y", x.y)
            j.put("m", x.m)
            j.put("d", x.d)
            j.put("done", x.done)
            j.put("cat", x.category)
            j.put("rec", x.recurring)
            j.put("series", x.series)
            a.put(j)
        }
        o.put("items", a)
        val e = JSONObject()
        for ((k, v) in ends) e.put(k, v)
        o.put("ends", e)
        val s = JSONArray()
        for (k in skips) s.put(k)
        o.put("skips", s)
        val pa = JSONArray()
        for (p in periods) {
            val j = JSONObject()
            j.put("id", p.id)
            j.put("name", p.name)
            j.put("s", p.start)
            j.put("e", p.end)
            pa.put(j)
        }
        o.put("periods", pa)
        sp.edit().putString("data", o.toString()).apply()
    }

    fun wipe() {
        items.clear()
        ends.clear()
        skips.clear()
        periods.clear()
        start = 0L
        save()
    }

    fun replace(x: Item) {
        val i = items.indexOfFirst { it.id == x.id }
        if (i >= 0) items[i] = x else items.add(x)
        save()
    }

    fun remove(id: String) {
        items.removeAll { it.id == id }
        save()
    }

    /** Cria as repetições mensais para o mês ym, se ainda não existirem. */
    fun ensure(ym: Int) {
        var changed = false
        val bySeries = items.filter { it.recurring }.groupBy { it.series }
        for ((s, list) in bySeries) {
            if (skips.contains("$s|$ym")) continue
            val end = ends[s]
            if (end != null && ym >= end) continue
            if (list.any { it.ym == ym }) continue
            val prev = list.filter { it.ym < ym }.maxByOrNull { it.key } ?: continue
            val day = minOf(prev.d, Dates.daysIn(ym))
            items.add(prev.copy(id = newId(), y = ym / 100, m = ym % 100, d = day, done = false))
            changed = true
        }
        if (changed) save()
    }

    fun month(ym: Int, income: Boolean): List<Item> =
        items.filter { it.ym == ym && it.income == income }
            .sortedWith(compareBy({ it.key }, { it.title.lowercase() }))

    fun stats(ym: Int): Stats {
        var got = 0L
        var toGet = 0L
        var paid = 0L
        var toPay = 0L
        for (i in items) {
            if (i.ym != ym) continue
            if (i.income) {
                if (i.done) got += i.cents else toGet += i.cents
            } else {
                if (i.done) paid += i.cents else toPay += i.cents
            }
        }
        return Stats(got, toGet, paid, toPay)
    }

    fun balance(): Long {
        var b = start
        for (i in items) {
            if (i.done) b += if (i.income) i.cents else -i.cents
        }
        return b
    }

    fun pendingNet(ym: Int): Long {
        var n = 0L
        for (i in items) {
            if (!i.done && i.ym <= ym) n += if (i.income) i.cents else -i.cents
        }
        return n
    }

    fun overdueCount(ym: Int): Int = items.count { !it.done && it.ym < ym }

    fun periodItems(p: Period): List<Item> =
        items.filter { it.key >= p.start && it.key <= p.end }
            .sortedWith(compareBy({ it.key }, { it.title.lowercase() }))

    /** Saldo atual + tudo que ainda falta receber/pagar até a data final. */
    fun projectedAt(end: Int): Long {
        var n = balance()
        for (i in items) {
            if (!i.done && i.key <= end) n += if (i.income) i.cents else -i.cents
        }
        return n
    }

    fun savePeriod(p: Period) {
        val i = periods.indexOfFirst { it.id == p.id }
        if (i >= 0) periods[i] = p else periods.add(p)
        periods.sortBy { it.start }
        save()
    }

    fun removePeriod(id: String) {
        periods.removeAll { it.id == id }
        save()
    }
}
