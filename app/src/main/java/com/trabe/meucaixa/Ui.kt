package com.trabe.meucaixa

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object Pal {
    val BG = 0xFF0D1117.toInt()
    val SURFACE = 0xFF161B22.toInt()
    val SURFACE2 = 0xFF1F2733.toInt()
    val LINE = 0xFF2A3340.toInt()
    val TEXT = 0xFFE6EDF3.toInt()
    val MUTED = 0xFF8B97A8.toInt()
    val GREEN = 0xFF3DDC97.toInt()
    val RED = 0xFFFF6B6B.toInt()
    val BLUE = 0xFF5AA9FF.toInt()
    val AMBER = 0xFFFFC857.toInt()
    val VIOLET = 0xFFB28DFF.toInt()
    val PINK = 0xFFFF8FB8.toInt()
    val TEAL = 0xFF2EC4D6.toInt()
}

fun withAlpha(c: Int, a: Float): Int =
    Color.argb((a * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun rounded(color: Int, radius: Float, strokeColor: Int = 0, strokeW: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = radius
    if (strokeW > 0) g.setStroke(strokeW, strokeColor)
    return g
}

fun oval(color: Int): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.OVAL
    g.setColor(color)
    return g
}

fun Context.tv(t: CharSequence, sp: Float = 14f, color: Int = Pal.TEXT, bold: Boolean = false): TextView {
    val v = TextView(this)
    v.text = t
    v.textSize = sp
    v.setTextColor(color)
    if (bold) v.typeface = Typeface.DEFAULT_BOLD
    return v
}

fun Context.vbox(): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.VERTICAL
    return l
}

fun Context.hbox(): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.HORIZONTAL
    l.gravity = Gravity.CENTER_VERTICAL
    return l
}

fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(w, h, weight)

fun Context.iconBadge(kind: String, color: Int, size: Int = 44): FrameLayout {
    val f = FrameLayout(this)
    f.background = rounded(withAlpha(color, 0.18f), dpf(size / 3f))
    val ic = IconView(this, kind, color)
    f.addView(ic, FrameLayout.LayoutParams(dp(size / 2), dp(size / 2), Gravity.CENTER))
    return f
}

class BarView(ctx: Context, private val barColor: Int) : View(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(Pal.TEXT, 0.10f) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = barColor }

    var frac: Float = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        c.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, track)
        if (frac > 0f) {
            val fw = maxOf(h, w * frac)
            c.drawRoundRect(0f, 0f, fw, h, h / 2, h / 2, fill)
        }
    }
}

class IconView(ctx: Context, var kind: String, var tint: Int = Pal.TEXT) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        p.color = tint
        p.strokeWidth = s * 0.08f
        p.style = Paint.Style.STROKE
        c.save()
        c.translate((width - s) / 2f, (height - s) / 2f)
        Icons.draw(c, kind, s, p)
        c.restore()
    }
}

data class Cat(val name: String, val icon: String, val color: Int)

object Cats {
    val expense = listOf(
        Cat("Moradia", "home", Pal.BLUE),
        Cat("Alimentação", "cart", Pal.AMBER),
        Cat("Transporte", "car", Pal.TEAL),
        Cat("Saúde", "heart", Pal.PINK),
        Cat("Lazer", "game", Pal.VIOLET),
        Cat("Educação", "book", Pal.BLUE),
        Cat("Contas", "bolt", Pal.AMBER),
        Cat("Compras", "bag", Pal.PINK),
        Cat("Outros", "dots", Pal.MUTED)
    )
    val income = listOf(
        Cat("Salário", "briefcase", Pal.GREEN),
        Cat("Freelance", "coin", Pal.AMBER),
        Cat("Benefício", "wallet", Pal.TEAL),
        Cat("Investimento", "up", Pal.VIOLET),
        Cat("Outros", "dots", Pal.MUTED)
    )

    fun of(x: Item): Cat {
        val list = if (x.income) income else expense
        return list.firstOrNull { it.name == x.category } ?: list.last()
    }

    fun expenseByName(n: String): Cat = expense.firstOrNull { it.name == n } ?: expense.last()
}

object Icons {
    fun draw(c: Canvas, kind: String, s: Float, p: Paint) {
        fun ln(a: Float, b: Float, x: Float, y: Float) = c.drawLine(a * s, b * s, x * s, y * s, p)
        fun poly(vararg v: Float, close: Boolean = false) {
            val path = Path()
            path.moveTo(v[0] * s, v[1] * s)
            var i = 2
            while (i < v.size) {
                path.lineTo(v[i] * s, v[i + 1] * s)
                i += 2
            }
            if (close) path.close()
            c.drawPath(path, p)
        }
        fun rr(l: Float, t: Float, r: Float, b: Float, rad: Float) =
            c.drawRoundRect(RectF(l * s, t * s, r * s, b * s), rad * s, rad * s, p)
        fun ci(x: Float, y: Float, r: Float) = c.drawCircle(x * s, y * s, r * s, p)
        fun dot(x: Float, y: Float, r: Float) {
            val old = p.style
            p.style = Paint.Style.FILL
            c.drawCircle(x * s, y * s, r * s, p)
            p.style = old
        }

        when (kind) {
            "home" -> {
                poly(0.14f, 0.5f, 0.5f, 0.2f, 0.86f, 0.5f)
                poly(0.25f, 0.44f, 0.25f, 0.82f, 0.75f, 0.82f, 0.75f, 0.44f)
                poly(0.43f, 0.82f, 0.43f, 0.62f, 0.57f, 0.62f, 0.57f, 0.82f)
            }
            "up" -> {
                ln(0.5f, 0.82f, 0.5f, 0.2f)
                poly(0.25f, 0.45f, 0.5f, 0.2f, 0.75f, 0.45f)
            }
            "down" -> {
                ln(0.5f, 0.18f, 0.5f, 0.8f)
                poly(0.25f, 0.55f, 0.5f, 0.8f, 0.75f, 0.55f)
            }
            "gear" -> {
                ci(0.5f, 0.5f, 0.2f)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val cx = Math.cos(a).toFloat()
                    val cy = Math.sin(a).toFloat()
                    ln(0.5f + cx * 0.30f, 0.5f + cy * 0.30f, 0.5f + cx * 0.40f, 0.5f + cy * 0.40f)
                }
                ci(0.5f, 0.5f, 0.28f)
            }
            "plus" -> {
                ln(0.5f, 0.2f, 0.5f, 0.8f)
                ln(0.2f, 0.5f, 0.8f, 0.5f)
            }
            "check" -> poly(0.2f, 0.52f, 0.42f, 0.72f, 0.8f, 0.3f)
            "wallet" -> {
                rr(0.14f, 0.28f, 0.86f, 0.78f, 0.08f)
                ln(0.14f, 0.42f, 0.86f, 0.42f)
                dot(0.7f, 0.6f, 0.05f)
            }
            "cart" -> {
                poly(0.12f, 0.2f, 0.25f, 0.2f, 0.38f, 0.6f, 0.78f, 0.6f, 0.85f, 0.32f, 0.28f, 0.32f)
                ci(0.42f, 0.76f, 0.05f)
                ci(0.72f, 0.76f, 0.05f)
            }
            "car" -> {
                rr(0.12f, 0.46f, 0.88f, 0.72f, 0.07f)
                poly(0.25f, 0.46f, 0.35f, 0.28f, 0.65f, 0.28f, 0.75f, 0.46f)
                ci(0.3f, 0.74f, 0.06f)
                ci(0.7f, 0.74f, 0.06f)
            }
            "heart" -> {
                val path = Path()
                path.moveTo(0.5f * s, 0.8f * s)
                path.cubicTo(0.05f * s, 0.5f * s, 0.2f * s, 0.15f * s, 0.5f * s, 0.36f * s)
                path.cubicTo(0.8f * s, 0.15f * s, 0.95f * s, 0.5f * s, 0.5f * s, 0.8f * s)
                c.drawPath(path, p)
            }
            "game" -> {
                rr(0.12f, 0.33f, 0.88f, 0.7f, 0.16f)
                ln(0.3f, 0.43f, 0.3f, 0.6f)
                ln(0.22f, 0.515f, 0.38f, 0.515f)
                dot(0.64f, 0.47f, 0.035f)
                dot(0.74f, 0.57f, 0.035f)
            }
            "book" -> {
                poly(0.5f, 0.3f, 0.15f, 0.24f, 0.15f, 0.72f, 0.5f, 0.78f, 0.85f, 0.72f, 0.85f, 0.24f, 0.5f, 0.3f)
                ln(0.5f, 0.3f, 0.5f, 0.78f)
            }
            "bolt" -> poly(0.56f, 0.12f, 0.28f, 0.55f, 0.48f, 0.55f, 0.42f, 0.88f, 0.72f, 0.43f, 0.52f, 0.43f, close = true)
            "bag" -> {
                rr(0.2f, 0.35f, 0.8f, 0.84f, 0.06f)
                val path = Path()
                path.moveTo(0.38f * s, 0.35f * s)
                path.cubicTo(0.38f * s, 0.12f * s, 0.62f * s, 0.12f * s, 0.62f * s, 0.35f * s)
                c.drawPath(path, p)
            }
            "dots" -> {
                dot(0.24f, 0.5f, 0.06f)
                dot(0.5f, 0.5f, 0.06f)
                dot(0.76f, 0.5f, 0.06f)
            }
            "briefcase" -> {
                rr(0.12f, 0.32f, 0.88f, 0.8f, 0.07f)
                poly(0.38f, 0.32f, 0.38f, 0.2f, 0.62f, 0.2f, 0.62f, 0.32f)
                ln(0.12f, 0.54f, 0.88f, 0.54f)
            }
            "coin" -> {
                ci(0.5f, 0.5f, 0.34f)
                ci(0.5f, 0.5f, 0.17f)
            }
            "trash" -> {
                ln(0.2f, 0.3f, 0.8f, 0.3f)
                poly(0.4f, 0.3f, 0.4f, 0.2f, 0.6f, 0.2f, 0.6f, 0.3f)
                poly(0.27f, 0.3f, 0.3f, 0.82f, 0.7f, 0.82f, 0.73f, 0.3f)
            }
            "edit" -> poly(0.2f, 0.8f, 0.28f, 0.56f, 0.68f, 0.16f, 0.84f, 0.32f, 0.44f, 0.72f, close = true)
            "repeat" -> {
                c.drawArc(RectF(0.22f * s, 0.22f * s, 0.78f * s, 0.78f * s), 30f, 280f, false, p)
                poly(0.58f, 0.2f, 0.7f, 0.28f, 0.6f, 0.4f)
            }
            "cal" -> {
                rr(0.15f, 0.22f, 0.85f, 0.82f, 0.07f)
                ln(0.15f, 0.42f, 0.85f, 0.42f)
                ln(0.35f, 0.14f, 0.35f, 0.3f)
                ln(0.65f, 0.14f, 0.65f, 0.3f)
            }
            "chevL" -> poly(0.62f, 0.2f, 0.35f, 0.5f, 0.62f, 0.8f)
            "chevR" -> poly(0.38f, 0.2f, 0.65f, 0.5f, 0.38f, 0.8f)
            "sync" -> {
                c.drawArc(RectF(0.2f * s, 0.2f * s, 0.8f * s, 0.8f * s), 200f, 250f, false, p)
                poly(0.62f, 0.14f, 0.74f, 0.24f, 0.6f, 0.34f)
            }
            "info" -> {
                ci(0.5f, 0.5f, 0.34f)
                ln(0.5f, 0.46f, 0.5f, 0.68f)
                dot(0.5f, 0.33f, 0.04f)
            }
            else -> ci(0.5f, 0.5f, 0.3f)
        }
    }
}
