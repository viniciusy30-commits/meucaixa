package com.trabe.meucaixa

import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var body: FrameLayout
    private lateinit var fab: FrameLayout
    private lateinit var monthBar: LinearLayout
    private lateinit var monthLabel: TextView
    private val navViews = mutableListOf<Pair<IconView, TextView>>()
    private var tab = 0
    private var ym = Dates.nowYm()
    private var savedScroll = 0
    private var currentScroll: ScrollView? = null
    private var anim = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        buildShell()
        render(true)
        Novidades.maybeShow(this, store)
        if (!store.flag("welcomed")) {
            store.setFlag("welcomed", true)
            balanceDialog(true)
        }
        Updater.autoCheck(this)
    }

    // ---------------------------------------------------------------- casca

    private fun buildShell() {
        val root = vbox()
        root.setBackgroundColor(Pal.BG)

        monthBar = hbox()
        monthBar.setPadding(dp(8), dp(10), dp(8), dp(4))
        monthBar.gravity = Gravity.CENTER
        val prev = navBtn("chevL") { changeMonth(-1) }
        val next = navBtn("chevR") { changeMonth(1) }
        monthLabel = tv("", 18f, Pal.TEXT, true)
        monthLabel.gravity = Gravity.CENTER
        monthLabel.setOnClickListener {
            ym = Dates.nowYm()
            savedScroll = 0
            render(true)
        }
        monthBar.addView(prev, lp(dp(44), dp(44)))
        monthBar.addView(monthLabel, lp(0, WRAP, 1f))
        monthBar.addView(next, lp(dp(44), dp(44)))
        root.addView(monthBar, lp(MATCH, WRAP))

        val host = FrameLayout(this)
        body = FrameLayout(this)
        host.addView(body, FrameLayout.LayoutParams(MATCH, MATCH))

        fab = FrameLayout(this)
        fab.background = oval(Pal.GREEN)
        fab.elevation = dpf(8f)
        fab.isClickable = true
        fab.addView(IconView(this, "plus", Pal.BG), FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        fab.setOnClickListener { onFab() }
        val fabLp = FrameLayout.LayoutParams(dp(58), dp(58), Gravity.END or Gravity.BOTTOM)
        fabLp.setMargins(0, 0, dp(18), dp(16))
        host.addView(fab, fabLp)
        root.addView(host, lp(MATCH, 0, 1f))

        val nav = hbox()
        nav.setBackgroundColor(Pal.SURFACE)
        nav.setPadding(0, dp(8), 0, dp(8))
        val defs = listOf(
            Triple("home", "Resumo", 0),
            Triple("up", "Rendas", 1),
            Triple("down", "Gastos", 2),
            Triple("cal", "Períodos", 3),
            Triple("gear", "Ajustes", 4)
        )
        for ((kind, label, idx) in defs) {
            val c = vbox()
            c.gravity = Gravity.CENTER
            c.isClickable = true
            c.setOnClickListener { go(idx) }
            val ic = IconView(this, kind, Pal.MUTED)
            val t = tv(label, 11f, Pal.MUTED)
            c.addView(ic, lp(dp(24), dp(24)))
            val tl = lp(WRAP, WRAP)
            tl.topMargin = dp(2)
            c.addView(t, tl)
            nav.addView(c, lp(0, WRAP, 1f))
            navViews.add(Pair(ic, t))
        }
        root.addView(nav, lp(MATCH, WRAP))
        setContentView(root)
    }

    private fun navBtn(kind: String, action: () -> Unit): FrameLayout {
        val f = FrameLayout(this)
        f.isClickable = true
        f.setOnClickListener { action() }
        f.addView(IconView(this, kind, Pal.TEXT), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        return f
    }

    private fun go(t: Int) {
        if (t == tab) return
        tab = t
        savedScroll = 0
        render(true)
    }

    private fun changeMonth(n: Int) {
        val now = Dates.nowYm()
        val target = Dates.add(ym, n)
        if (target < Dates.add(now, -24) || target > Dates.add(now, 36)) return
        ym = target
        savedScroll = 0
        render(true)
    }

    private fun onFab() {
        if (tab == 3) periodDialog(null)
        else if (tab == 1) showItemDialog(true, null)
        else if (tab == 2) showItemDialog(false, null)
        else {
            AlertDialog.Builder(this)
                .setTitle("O que você quer adicionar?")
                .setItems(arrayOf("Nova renda", "Novo gasto")) { _, w -> showItemDialog(w == 0, null) }
                .show()
        }
    }

    private fun updateNav() {
        for (i in navViews.indices) {
            val on = i == tab
            val col = if (on) Pal.GREEN else Pal.MUTED
            navViews[i].first.tint = col
            navViews[i].first.invalidate()
            navViews[i].second.setTextColor(col)
        }
    }

    private fun render(animate: Boolean) {
        anim = animate
        if (tab < 3) store.ensure(ym)
        if (tab == 3) ensurePeriods()
        body.removeAllViews()
        monthLabel.text = Dates.label(ym)
        monthBar.visibility = if (tab >= 3) View.GONE else View.VISIBLE
        fab.visibility = if (tab == 4) View.GONE else View.VISIBLE

        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.clipToPadding = false
        sv.setPadding(0, 0, 0, dp(96))
        val col = vbox()
        col.setPadding(dp(16), dp(8), dp(16), dp(8))
        when (tab) {
            0 -> home(col)
            1 -> list(col, true)
            2 -> list(col, false)
            3 -> periods(col)
            else -> settings(col)
        }
        sv.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        body.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
        currentScroll = sv
        val target = savedScroll
        sv.post { sv.scrollTo(0, target) }
        if (animate) {
            col.alpha = 0f
            col.translationY = dpf(14f)
            col.animate().alpha(1f).translationY(0f).setDuration(260).start()
        }
        updateNav()
    }

    private fun rerender() {
        savedScroll = currentScroll?.scrollY ?: 0
        render(false)
    }

    // ---------------------------------------------------------------- helpers

    private fun cardBox(): LinearLayout {
        val c = vbox()
        c.setPadding(dp(18), dp(16), dp(18), dp(16))
        c.background = rounded(Pal.SURFACE, dpf(22f), Pal.LINE, 1)
        return c
    }

    private fun gap(b: Int): LinearLayout.LayoutParams {
        val l = lp(MATCH, WRAP)
        l.bottomMargin = dp(b)
        return l
    }

    private fun divider(): View {
        val v = View(this)
        v.setBackgroundColor(withAlpha(Pal.TEXT, 0.12f))
        return v
    }

    private fun sectionTitle(t: String): TextView {
        val v = tv(t, 13f, Pal.MUTED, true)
        v.setPadding(dp(4), dp(6), 0, dp(8))
        return v
    }

    private fun animMoney(t: TextView, to: Long) {
        t.text = Fmt.money(to)
        if (!anim) return
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 700
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            val f = it.animatedValue as Float
            t.text = Fmt.money((to * f).toLong())
        }
        a.start()
    }

    private fun animBar(b: BarView, frac: Float) {
        if (!anim) {
            b.frac = frac
            return
        }
        val a = ValueAnimator.ofFloat(0f, frac)
        a.duration = 700
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener { b.frac = it.animatedValue as Float }
        a.start()
    }

    private fun enter(v: View, i: Int) {
        if (!anim) return
        v.alpha = 0f
        v.translationY = dpf(18f)
        v.animate().alpha(1f).translationY(0f).setStartDelay(minOf(i, 10) * 40L).setDuration(260).start()
    }

    private fun isOverdue(x: Item): Boolean {
        if (x.done) return false
        val now = Dates.nowYm()
        return x.ym < now || (x.ym == now && x.d < Dates.today())
    }

    private fun statCol(label: String, cents: Long, color: Int): LinearLayout {
        val c = vbox()
        c.addView(tv(label, 12f, withAlpha(Pal.TEXT, 0.65f)))
        val v = tv(Fmt.money(cents), 18f, color, true)
        c.addView(v)
        return c
    }

    private fun progressRow(label: String, a: Long, total: Long, color: Int): LinearLayout {
        val b = vbox()
        val h = hbox()
        h.addView(tv(label, 13f, Pal.MUTED), lp(0, WRAP, 1f))
        h.addView(tv(Fmt.money(a) + " / " + Fmt.money(total), 12f, Pal.TEXT))
        b.addView(h, lp(MATCH, WRAP))
        val bar = BarView(this, color)
        val bl = lp(MATCH, dp(8))
        bl.topMargin = dp(6)
        b.addView(bar, bl)
        animBar(bar, if (total > 0) a.toFloat() / total else 0f)
        return b
    }

    // ---------------------------------------------------------------- resumo

    private fun home(col: LinearLayout) {
        val st = store.stats(ym)
        val bal = store.balance()
        val proj = bal + store.pendingNet(ym)
        val cur = ym == Dates.nowYm()

        val hero = vbox()
        hero.setPadding(dp(20), dp(20), dp(20), dp(20))
        val g = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF1D5A46.toInt(), 0xFF152330.toInt())
        )
        g.cornerRadius = dpf(26f)
        hero.background = g

        hero.addView(tv("Saldo atual", 13f, withAlpha(Pal.TEXT, 0.7f)))
        val balTv = tv(Fmt.money(bal), 34f, Pal.TEXT, true)
        animMoney(balTv, bal)
        balTv.setOnClickListener { balanceDialog(false) }
        hero.addView(balTv)
        hero.addView(tv("Toque no valor para ajustar", 11f, withAlpha(Pal.TEXT, 0.5f)))
        val d1 = lp(MATCH, 1)
        d1.setMargins(0, dp(14), 0, dp(14))
        hero.addView(divider(), d1)
        val row = hbox()
        row.addView(statCol("A receber", st.toGet, Pal.GREEN), lp(0, WRAP, 1f))
        row.addView(statCol("A pagar", st.toPay, Pal.RED), lp(0, WRAP, 1f))
        hero.addView(row, lp(MATCH, WRAP))
        val d2 = lp(MATCH, 1)
        d2.setMargins(0, dp(14), 0, dp(14))
        hero.addView(divider(), d2)
        val projLabel = if (cur) "Vai sobrar até o fim do mês" else "Saldo previsto no fim de " + Dates.months[ym % 100 - 1]
        hero.addView(tv(projLabel, 13f, withAlpha(Pal.TEXT, 0.7f)))
        val pt = tv(Fmt.money(proj), 28f, if (proj >= 0) Pal.GREEN else Pal.RED, true)
        animMoney(pt, proj)
        hero.addView(pt)
        val od = store.overdueCount(ym)
        if (od > 0) {
            val n = tv("Inclui $od pendência(s) de meses anteriores", 12f, Pal.AMBER)
            n.setPadding(0, dp(6), 0, 0)
            hero.addView(n)
        }
        col.addView(hero, gap(14))
        enter(hero, 0)

        if (cur) {
            val days = Dates.daysIn(ym) - Dates.today() + 1
            val c = cardBox()
            if (proj > 0) {
                c.addView(tv("Livre para gastar por dia", 13f, Pal.MUTED))
                c.addView(tv(Fmt.money(proj / days), 24f, Pal.TEXT, true))
                c.addView(tv("$days dia(s) até o fim do mês, além do que já está cadastrado", 12f, Pal.MUTED))
            } else {
                c.addView(tv("Atenção", 13f, Pal.RED, true))
                val m = tv("A previsão fecha no vermelho. Revise seus gastos ou confira se falta lançar alguma renda.", 13f, Pal.TEXT)
                m.setPadding(0, dp(4), 0, 0)
                c.addView(m)
            }
            col.addView(c, gap(14))
            enter(c, 1)
        }

        val prog = cardBox()
        prog.addView(tv("Progresso do mês", 13f, Pal.MUTED, true))
        val pr1 = progressRow("Rendas recebidas", st.got, st.got + st.toGet, Pal.GREEN)
        val pl1 = lp(MATCH, WRAP)
        pl1.topMargin = dp(12)
        prog.addView(pr1, pl1)
        val pr2 = progressRow("Gastos pagos", st.paid, st.paid + st.toPay, Pal.RED)
        val pl2 = lp(MATCH, WRAP)
        pl2.topMargin = dp(14)
        prog.addView(pr2, pl2)
        col.addView(prog, gap(14))
        enter(prog, 2)

        val exp = store.month(ym, false)
        if (exp.isNotEmpty()) {
            val byCat = exp.groupBy { it.category }
                .map { Pair(it.key, it.value.sumOf { x -> x.cents }) }
                .sortedByDescending { it.second }
            val total = byCat.sumOf { it.second }
            val cc = cardBox()
            cc.addView(tv("Gastos por categoria", 13f, Pal.MUTED, true))
            for (p in byCat.take(6)) {
                val cat = Cats.expenseByName(p.first)
                val r = vbox()
                val h = hbox()
                h.addView(iconBadge(cat.icon, cat.color, 32), lp(dp(32), dp(32)))
                val nl = lp(0, WRAP, 1f)
                nl.leftMargin = dp(10)
                h.addView(tv(cat.name, 14f, Pal.TEXT), nl)
                h.addView(tv(Fmt.money(p.second), 14f, Pal.TEXT, true))
                r.addView(h, lp(MATCH, WRAP))
                val bar = BarView(this, cat.color)
                val bl = lp(MATCH, dp(6))
                bl.topMargin = dp(8)
                r.addView(bar, bl)
                animBar(bar, if (total > 0) p.second.toFloat() / total else 0f)
                val rl = lp(MATCH, WRAP)
                rl.topMargin = dp(14)
                cc.addView(r, rl)
            }
            col.addView(cc, gap(14))
            enter(cc, 3)
        }

        val pending = (store.month(ym, true) + store.month(ym, false))
            .filter { !it.done }
            .sortedBy { it.key }
            .take(6)
        col.addView(sectionTitle("Próximos lançamentos"))
        if (pending.isEmpty()) {
            val c = cardBox()
            val has = store.month(ym, true).isNotEmpty() || store.month(ym, false).isNotEmpty()
            c.addView(tv(if (has) "Tudo em dia neste mês." else "Nada cadastrado neste mês ainda. Toque no + para começar.", 14f, Pal.MUTED))
            col.addView(c, gap(10))
        } else {
            pending.forEachIndexed { i, x ->
                val r = itemRow(x)
                col.addView(r, gap(10))
                enter(r, i + 4)
            }
        }
    }

    // ---------------------------------------------------------------- listas

    private fun list(col: LinearLayout, income: Boolean) {
        val st = store.stats(ym)
        val a = if (income) st.got else st.paid
        val rest = if (income) st.toGet else st.toPay
        val total = a + rest

        val card = cardBox()
        card.addView(tv(if (income) "Rendas do mês" else "Gastos do mês", 13f, Pal.MUTED))
        card.addView(tv(Fmt.money(total), 26f, Pal.TEXT, true))
        val bar = BarView(this, if (income) Pal.GREEN else Pal.RED)
        val bl = lp(MATCH, dp(8))
        bl.topMargin = dp(10)
        card.addView(bar, bl)
        animBar(bar, if (total > 0) a.toFloat() / total else 0f)
        val h = hbox()
        h.addView(tv((if (income) "Recebido " else "Pago ") + Fmt.money(a), 12f, Pal.GREEN), lp(0, WRAP, 1f))
        h.addView(tv((if (income) "Falta receber " else "Falta pagar ") + Fmt.money(rest), 12f, Pal.MUTED))
        val hl = lp(MATCH, WRAP)
        hl.topMargin = dp(8)
        card.addView(h, hl)
        col.addView(card, gap(14))
        enter(card, 0)

        val items = store.month(ym, income)
        if (items.isEmpty()) {
            val e = vbox()
            e.gravity = Gravity.CENTER
            e.setPadding(0, dp(40), 0, dp(20))
            e.addView(iconBadge(if (income) "up" else "down", if (income) Pal.GREEN else Pal.RED, 72), lp(dp(72), dp(72)))
            val t = tv(if (income) "Nenhuma renda neste mês" else "Nenhum gasto neste mês", 16f, Pal.TEXT, true)
            t.setPadding(0, dp(14), 0, dp(4))
            e.addView(t)
            val s = tv("Toque no botão + para adicionar", 13f, Pal.MUTED)
            e.addView(s)
            col.addView(e, lp(MATCH, WRAP))
            enter(e, 1)
        } else {
            items.forEachIndexed { i, x ->
                val r = itemRow(x)
                col.addView(r, gap(10))
                enter(r, i + 1)
            }
        }
    }

    private fun itemRow(x: Item): View {
        val cat = Cats.of(x)
        val row = hbox()
        row.setPadding(dp(12), dp(12), dp(12), dp(12))
        row.background = rounded(Pal.SURFACE, dpf(18f), Pal.LINE, 1)
        row.isClickable = true
        row.setOnClickListener { showItemDialog(x.income, x) }

        row.addView(iconBadge(cat.icon, cat.color, 44), lp(dp(44), dp(44)))

        val mid = vbox()
        val title = tv(x.title, 15f, Pal.TEXT, true)
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        mid.addView(title)
        var sub = "%02d/%02d".format(x.d, x.m) + " • " + cat.name
        if (x.recurring) sub += " • mensal"
        val subTv = tv(sub, 12f, Pal.MUTED)
        subTv.maxLines = 1
        subTv.ellipsize = TextUtils.TruncateAt.END
        mid.addView(subTv)
        val ml = lp(0, WRAP, 1f)
        ml.leftMargin = dp(12)
        row.addView(mid, ml)

        val right = vbox()
        right.gravity = Gravity.END
        val amountColor = if (x.income) Pal.GREEN else Pal.TEXT
        val amount = tv((if (x.income) "+ " else "- ") + Fmt.money(x.cents), 15f, amountColor, true)
        amount.gravity = Gravity.END
        amount.isSingleLine = true
        right.addView(amount)

        val od = isOverdue(x)
        val chipText = when {
            x.income && x.done -> "Recebido"
            x.income -> "Receber"
            x.done -> "Pago"
            od -> "Atrasado"
            else -> "Pagar"
        }
        val chipColor = when {
            x.done -> Pal.GREEN
            od -> Pal.RED
            else -> Pal.MUTED
        }
        val chip = tv(chipText, 12f, chipColor, true)
        chip.setPadding(dp(12), dp(5), dp(12), dp(5))
        chip.background = if (x.done) rounded(withAlpha(Pal.GREEN, 0.16f), dpf(20f))
        else rounded(0, dpf(20f), chipColor, 2)
        chip.isClickable = true
        chip.setOnClickListener {
            chip.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            store.replace(x.copy(done = !x.done))
            rerender()
        }
        val cl = lp(WRAP, WRAP)
        cl.topMargin = dp(6)
        cl.gravity = Gravity.END
        right.addView(chip, cl)
        row.addView(right, lp(WRAP, WRAP))
        return row
    }


    // ---------------------------------------------------------------- períodos

    private fun ensurePeriods() {
        for (p in store.periods) {
            var m = p.start / 100
            val last = p.end / 100
            while (m <= last) {
                store.ensure(m)
                m = Dates.add(m, 1)
            }
        }
    }

    private fun periods(col: LinearLayout) {
        val title = tv("Períodos", 24f, Pal.TEXT, true)
        title.setPadding(dp(4), dp(14), 0, dp(4))
        col.addView(title)
        val sub = tv("Escolha de que data até que data você quer acompanhar. A previsão atualiza sozinha conforme você marca o que recebeu e pagou.", 13f, Pal.MUTED)
        sub.setPadding(dp(4), 0, dp(4), dp(14))
        col.addView(sub)
        if (store.periods.isEmpty()) {
            val e = vbox()
            e.gravity = Gravity.CENTER
            e.setPadding(0, dp(40), 0, dp(20))
            e.addView(iconBadge("cal", Pal.BLUE, 72), lp(dp(72), dp(72)))
            val t = tv("Nenhum período criado", 16f, Pal.TEXT, true)
            t.setPadding(0, dp(14), 0, dp(4))
            e.addView(t)
            e.addView(tv("Toque no + para criar um (ex.: de hoje até o próximo salário)", 13f, Pal.MUTED))
            col.addView(e, lp(MATCH, WRAP))
            enter(e, 0)
        } else {
            store.periods.forEachIndexed { i, p ->
                val v = periodCard(p)
                col.addView(v, gap(16))
                enter(v, i)
            }
        }
    }

    private fun periodCard(p: Period): View {
        val items = store.periodItems(p)
        var got = 0L
        var toGet = 0L
        var paid = 0L
        var toPay = 0L
        for (i in items) {
            if (i.income) {
                if (i.done) got += i.cents else toGet += i.cents
            } else {
                if (i.done) paid += i.cents else toPay += i.cents
            }
        }
        val proj = store.projectedAt(p.end)

        val wrap = vbox()
        val card = cardBox()
        val head = hbox()
        head.isClickable = true
        head.setOnClickListener { periodDialog(p) }
        head.addView(tv(p.name, 17f, Pal.TEXT, true), lp(0, WRAP, 1f))
        head.addView(IconView(this, "edit", Pal.MUTED), lp(dp(20), dp(20)))
        card.addView(head, lp(MATCH, WRAP))
        card.addView(tv(Dates.keyLabel(p.start) + " até " + Dates.keyLabel(p.end), 12f, Pal.MUTED))
        val d1 = lp(MATCH, 1)
        d1.setMargins(0, dp(12), 0, dp(12))
        card.addView(divider(), d1)
        card.addView(tv("Vai sobrar até " + "%02d/%02d".format(p.end % 100, (p.end / 100) % 100), 13f, Pal.MUTED))
        val pt = tv(Fmt.money(proj), 28f, if (proj >= 0) Pal.GREEN else Pal.RED, true)
        animMoney(pt, proj)
        card.addView(pt)

        val today = Dates.todayKey()
        val status: String
        if (today > p.end) {
            status = "Período encerrado"
        } else {
            val from = if (today < p.start) p.start else today
            val n = Dates.daysBetween(from, p.end) + 1
            status = if (proj > 0) Fmt.money(proj / n) + " por dia (" + n + " dia(s))" else "A previsão fecha no vermelho"
        }
        val st = tv(status, 12f, Pal.MUTED)
        st.setPadding(0, dp(2), 0, 0)
        card.addView(st)

        val r1 = progressRow("Rendas recebidas", got, got + toGet, Pal.GREEN)
        val l1 = lp(MATCH, WRAP)
        l1.topMargin = dp(16)
        card.addView(r1, l1)
        val r2 = progressRow("Gastos pagos", paid, paid + toPay, Pal.RED)
        val l2 = lp(MATCH, WRAP)
        l2.topMargin = dp(14)
        card.addView(r2, l2)
        wrap.addView(card, gap(10))

        if (items.isEmpty()) {
            wrap.addView(tv("Nenhum lançamento nesse período.", 13f, Pal.MUTED))
        } else {
            for (x in items) wrap.addView(itemRow(x), gap(8))
        }
        return wrap
    }

    private fun dateBox(t: String): TextView {
        val d = tv(t, 16f, Pal.TEXT)
        d.setPadding(dp(14), dp(12), dp(14), dp(12))
        d.background = rounded(Pal.SURFACE2, dpf(14f))
        d.isClickable = true
        return d
    }

    private fun pickDate(initial: Int, onPick: (Int) -> Unit) {
        DatePickerDialog(this, { _, y, m, d -> onPick(y * 10000 + (m + 1) * 100 + d) },
            initial / 10000, (initial / 100) % 100 - 1, initial % 100).show()
    }

    private fun periodDialog(ex: Period?) {
        val v = vbox()
        v.setPadding(dp(22), dp(4), dp(22), dp(4))
        val etName = field("Ex.: Até o próximo salário", ex?.name ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        var s = ex?.start ?: Dates.todayKey()
        var e = ex?.end ?: (Dates.nowYm() * 100 + Dates.daysIn(Dates.nowYm()))
        v.addView(label("NOME"))
        v.addView(etName, lp(MATCH, WRAP))
        v.addView(label("DE"))
        val sTv = dateBox(Dates.keyLabel(s))
        sTv.setOnClickListener { pickDate(s) { k -> s = k; sTv.text = Dates.keyLabel(k) } }
        v.addView(sTv, lp(MATCH, WRAP))
        v.addView(label("ATÉ"))
        val eTv = dateBox(Dates.keyLabel(e))
        eTv.setOnClickListener { pickDate(e) { k -> e = k; eTv.text = Dates.keyLabel(k) } }
        v.addView(eTv, lp(MATCH, WRAP))

        val sv = ScrollView(this)
        sv.addView(v)
        val b = AlertDialog.Builder(this)
            .setTitle(if (ex == null) "Novo período" else "Editar período")
            .setView(sv)
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
        if (ex != null) b.setNeutralButton("Excluir", null)
        val dlg = b.create()
        styleDialog(dlg)
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (e < s) {
                Toast.makeText(this, "A data final precisa ser depois da inicial", Toast.LENGTH_SHORT).show()
            } else if (Dates.daysBetween(s, e) > 400) {
                Toast.makeText(this, "O período pode ter no máximo 400 dias", Toast.LENGTH_SHORT).show()
            } else {
                val name = etName.text.toString().trim().ifEmpty { "Período" }
                store.savePeriod(Period(ex?.id ?: store.newId(), name, s, e))
                dlg.dismiss()
                savedScroll = 0
                render(false)
            }
        }
        if (ex != null) {
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dlg.dismiss()
                val d2 = AlertDialog.Builder(this)
                    .setTitle("Excluir \"" + ex.name + "\"?")
                    .setMessage("Os lançamentos não são apagados, só o período.")
                    .setPositiveButton("Excluir") { _, _ ->
                        store.removePeriod(ex.id)
                        rerender()
                    }
                    .setNegativeButton("Cancelar", null)
                    .create()
                styleDialog(d2)
                d2.show()
            }
        }
    }

    // ---------------------------------------------------------------- ajustes

    private fun settings(col: LinearLayout) {
        val title = tv("Ajustes", 24f, Pal.TEXT, true)
        title.setPadding(dp(4), dp(14), 0, dp(14))
        col.addView(title)

        val rows = listOf(
            actionRow("wallet", Pal.GREEN, "Definir saldo atual", "Informe quanto dinheiro você tem hoje") { balanceDialog(false) },
            actionRow("sync", Pal.BLUE, "Verificar atualização", "Busca a versão mais nova do app") { Updater.check(this, true) },
            actionRow("info", Pal.VIOLET, "O que mudou", "Veja as novidades de cada versão") { Novidades.showAll(this) },
            actionRow("trash", Pal.RED, "Apagar todos os dados", "Remove rendas, gastos e saldo deste aparelho") { confirmWipe() }
        )
        rows.forEachIndexed { i, r ->
            col.addView(r, gap(10))
            enter(r, i)
        }
        val v = tv("Meu Caixa • versão " + BuildConfig.VERSION_NAME, 12f, Pal.MUTED)
        v.gravity = Gravity.CENTER
        v.setPadding(0, dp(16), 0, 0)
        col.addView(v, lp(MATCH, WRAP))
        val s = tv("Seus dados ficam salvos só neste celular.", 12f, Pal.MUTED)
        s.gravity = Gravity.CENTER
        col.addView(s, lp(MATCH, WRAP))
    }

    private fun actionRow(kind: String, color: Int, title: String, sub: String, onClick: () -> Unit): View {
        val r = hbox()
        r.setPadding(dp(14), dp(14), dp(14), dp(14))
        r.background = rounded(Pal.SURFACE, dpf(18f), Pal.LINE, 1)
        r.isClickable = true
        r.setOnClickListener { onClick() }
        r.addView(iconBadge(kind, color, 44), lp(dp(44), dp(44)))
        val m = vbox()
        m.addView(tv(title, 15f, Pal.TEXT, true))
        m.addView(tv(sub, 12f, Pal.MUTED))
        val ml = lp(0, WRAP, 1f)
        ml.leftMargin = dp(12)
        r.addView(m, ml)
        r.addView(IconView(this, "chevR", Pal.MUTED), lp(dp(18), dp(18)))
        return r
    }

    private fun confirmWipe() {
        AlertDialog.Builder(this)
            .setTitle("Apagar tudo?")
            .setMessage("Isso remove todas as rendas, gastos e o saldo salvos neste aparelho. Não dá para desfazer.")
            .setPositiveButton("Apagar") { _, _ ->
                store.wipe()
                savedScroll = 0
                render(true)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------------------------------------------------------------- diálogos

    private fun styleDialog(d: AlertDialog) {
        d.window?.setBackgroundDrawable(rounded(Pal.SURFACE, dpf(24f)))
    }

    private fun field(hint: String, text: String, type: Int): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(text)
        e.inputType = type
        e.setTextColor(Pal.TEXT)
        e.setHintTextColor(Pal.MUTED)
        e.textSize = 16f
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        e.background = rounded(Pal.SURFACE2, dpf(14f))
        return e
    }

    private fun label(t: String): TextView {
        val v = tv(t, 12f, Pal.MUTED, true)
        v.setPadding(dp(2), dp(12), 0, dp(6))
        return v
    }

    private fun balanceDialog(first: Boolean) {
        val box = vbox()
        box.setPadding(dp(22), dp(8), dp(22), 0)
        val et = field(
            "Ex.: 1250,00",
            "",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        )
        box.addView(et, lp(MATCH, WRAP))
        val dlg = AlertDialog.Builder(this)
            .setTitle(if (first) "Bem-vindo! Quanto você tem agora?" else "Quanto você tem agora?")
            .setMessage("Informe o dinheiro que você tem hoje (conta + carteira). A partir daí, o app soma e subtrai o que você marcar como recebido ou pago.")
            .setView(box)
            .setPositiveButton("Salvar", null)
            .setNegativeButton(if (first) "Depois" else "Cancelar", null)
            .create()
        styleDialog(dlg)
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val cents = Fmt.parse(et.text.toString())
            if (cents == null) {
                et.error = "Valor inválido"
            } else {
                store.start = cents - (store.balance() - store.start)
                store.save()
                dlg.dismiss()
                savedScroll = 0
                render(true)
            }
        }
    }

    private fun showItemDialog(income: Boolean, ex: Item?) {
        val cats = if (income) Cats.income else Cats.expense
        val v = vbox()
        v.setPadding(dp(22), dp(4), dp(22), dp(4))

        val etTitle = field("Ex.: " + (if (income) "Salário" else "Aluguel"), ex?.title ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val etAmount = field("0,00", if (ex != null) Fmt.plain(ex.cents) else "", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        var yy = ex?.y ?: (ym / 100)
        var mm = ex?.m ?: (ym % 100)
        var dd = ex?.d ?: (if (ym == Dates.nowYm()) Dates.today() else 1)
        fun dtxt(): String = "%02d/%02d/%d".format(dd, mm, yy)

        v.addView(label("DESCRIÇÃO"))
        v.addView(etTitle, lp(MATCH, WRAP))
        v.addView(label("VALOR (R$)"))
        v.addView(etAmount, lp(MATCH, WRAP))

        v.addView(label("DATA"))
        val dateTv = tv(dtxt(), 16f, Pal.TEXT)
        dateTv.setPadding(dp(14), dp(12), dp(14), dp(12))
        dateTv.background = rounded(Pal.SURFACE2, dpf(14f))
        dateTv.isClickable = true
        dateTv.setOnClickListener {
            DatePickerDialog(this, { _, y2, m2, d2 ->
                yy = y2
                mm = m2 + 1
                dd = d2
                dateTv.text = dtxt()
            }, yy, mm - 1, dd).show()
        }
        v.addView(dateTv, lp(MATCH, WRAP))

        v.addView(label("CATEGORIA"))
        var sel = cats.indexOfFirst { it.name == ex?.category }
        if (sel < 0) sel = 0
        val chipViews = mutableListOf<TextView>()
        fun paint() {
            chipViews.forEachIndexed { i, t ->
                val on = i == sel
                t.setTextColor(if (on) Pal.BG else Pal.TEXT)
                t.background = rounded(if (on) cats[i].color else Pal.SURFACE2, dpf(20f))
            }
        }
        val chipRow = hbox()
        cats.forEachIndexed { i, c ->
            val t = tv(c.name, 13f, Pal.TEXT, true)
            t.setPadding(dp(14), dp(8), dp(14), dp(8))
            t.isClickable = true
            t.setOnClickListener {
                sel = i
                paint()
            }
            chipViews.add(t)
            val cl = lp(WRAP, WRAP)
            cl.rightMargin = dp(8)
            chipRow.addView(t, cl)
        }
        paint()
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(chipRow)
        v.addView(hs, lp(MATCH, WRAP))

        val swRec = SwitchCompat(this)
        swRec.text = "Repetir todo mês"
        swRec.setTextColor(Pal.TEXT)
        swRec.textSize = 15f
        swRec.isChecked = ex?.recurring ?: false
        val swDone = SwitchCompat(this)
        swDone.text = if (income) "Já recebi" else "Já paguei"
        swDone.setTextColor(Pal.TEXT)
        swDone.textSize = 15f
        swDone.isChecked = ex?.done ?: false
        val sl1 = lp(MATCH, WRAP)
        sl1.topMargin = dp(16)
        v.addView(swDone, sl1)
        val sl2 = lp(MATCH, WRAP)
        sl2.topMargin = dp(10)
        v.addView(swRec, sl2)

        val sv = ScrollView(this)
        sv.addView(v)
        val b = AlertDialog.Builder(this)
            .setTitle(if (ex == null) (if (income) "Nova renda" else "Novo gasto") else (if (income) "Editar renda" else "Editar gasto"))
            .setView(sv)
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
        if (ex != null) b.setNeutralButton("Excluir", null)
        val dlg = b.create()
        styleDialog(dlg)
        dlg.show()

        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val t = etTitle.text.toString().trim()
            val cents = Fmt.parse(etAmount.text.toString())
            if (t.isEmpty()) {
                etTitle.error = "Dê um nome"
            } else if (cents == null || cents <= 0L) {
                etAmount.error = "Valor inválido"
            } else {
                val id = ex?.id ?: store.newId()
                val series = ex?.series ?: id
                val item = Item(id, income, t, cents, yy, mm, dd, swDone.isChecked, cats[sel].name, swRec.isChecked, series)
                if (ex != null && ex.recurring && !item.recurring) {
                    store.ends[series] = Dates.add(item.ym, 1)
                    store.items.removeAll { it.series == series && it.ym > item.ym && !it.done && it.id != item.id }
                }
                if (item.recurring) store.ends.remove(series)
                store.replace(item)
                ym = item.ym
                dlg.dismiss()
                savedScroll = 0
                render(false)
            }
        }
        if (ex != null) {
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dlg.dismiss()
                confirmDelete(ex)
            }
        }
    }

    private fun confirmDelete(x: Item) {
        if (x.recurring) {
            val dlg = AlertDialog.Builder(this)
                .setTitle("Excluir \"" + x.title + "\"")
                .setMessage("Este lançamento se repete todo mês. O que você quer apagar?")
                .setPositiveButton("Só este mês") { _, _ ->
                    store.skips.add(x.series + "|" + x.ym)
                    store.remove(x.id)
                    rerender()
                }
                .setNegativeButton("Este e os próximos") { _, _ ->
                    store.ends[x.series] = x.ym
                    store.items.removeAll { it.series == x.series && it.ym >= x.ym }
                    store.save()
                    rerender()
                }
                .setNeutralButton("Cancelar", null)
                .create()
            styleDialog(dlg)
            dlg.show()
        } else {
            val dlg = AlertDialog.Builder(this)
                .setTitle("Excluir \"" + x.title + "\"?")
                .setPositiveButton("Excluir") { _, _ ->
                    store.remove(x.id)
                    rerender()
                }
                .setNegativeButton("Cancelar", null)
                .create()
            styleDialog(dlg)
            dlg.show()
        }
    }
}
