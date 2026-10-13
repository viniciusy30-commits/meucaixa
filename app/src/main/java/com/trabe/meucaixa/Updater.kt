package com.trabe.meucaixa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Updater {
    const val REPO = "viniciusy30-commits/meucaixa"
    private const val SIX_HOURS = 6L * 60L * 60L * 1000L

    private class Info(val version: Int, val name: String, val url: String)

    private fun toast(act: Activity, msg: String) {
        Toast.makeText(act, msg, Toast.LENGTH_SHORT).show()
    }

    fun autoCheck(act: Activity) {
        val sp = act.getSharedPreferences("updater", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("last", 0L) < SIX_HOURS) return
        sp.edit().putLong("last", now).apply()
        check(act, false)
    }

    fun check(act: Activity, manual: Boolean) {
        if (manual) toast(act, "Procurando atualização…")
        Thread {
            var info: Info? = null
            var failed = false
            try {
                info = fetch()
            } catch (e: Exception) {
                failed = true
            }
            val found = info
            val err = failed
            act.runOnUiThread {
                if (!act.isFinishing && !act.isDestroyed) {
                    if (err) {
                        if (manual) toast(act, "Sem internet ou não foi possível verificar.")
                    } else if (found == null) {
                        if (manual) toast(act, "Nenhuma versão publicada ainda.")
                    } else if (found.version > BuildConfig.VERSION_CODE) {
                        showDialog(act, found)
                    } else if (manual) {
                        toast(act, "Você já está na versão mais nova.")
                    }
                }
            }
        }.start()
    }

    private fun fetch(): Info? {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "MeuCaixa")
        try {
            val code = c.responseCode
            if (code == 404) return null
            if (code != 200) throw IOException("HTTP $code")
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(txt)
            val tag = o.getString("tag_name").trim().removePrefix("v")
            val ver = tag.toIntOrNull() ?: return null
            val assets = o.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return Info(ver, o.optString("name", tag), a.getString("browser_download_url"))
                }
            }
            return null
        } finally {
            c.disconnect()
        }
    }

    private fun showDialog(act: Activity, info: Info) {
        AlertDialog.Builder(act)
            .setTitle("Nova versão disponível")
            .setMessage("A versão ${info.version} está pronta (você está na ${BuildConfig.VERSION_CODE}). Quer atualizar agora?")
            .setPositiveButton("Atualizar") { _, _ -> startDownload(act, info) }
            .setNegativeButton("Depois", null)
            .show()
    }

    private fun startDownload(act: Activity, info: Info) {
        if (Build.VERSION.SDK_INT >= 26 && !act.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(act)
                .setTitle("Permissão necessária")
                .setMessage("Para instalar a atualização, o Android precisa que você permita \"Instalar apps desconhecidos\" para o Meu Caixa. Toque em Abrir configuração, ative a opção, volte aqui e toque em Verificar atualização de novo.")
                .setPositiveButton("Abrir configuração") { _, _ ->
                    val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + act.packageName))
                    act.startActivity(i)
                }
                .setNegativeButton("Agora não", null)
                .show()
            return
        }

        val pad = (20 * act.resources.displayMetrics.density).toInt()
        val label = TextView(act)
        label.text = "Baixando… 0%"
        label.gravity = Gravity.CENTER
        val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, pad, pad, pad)
        box.addView(label)
        box.addView(bar)
        val dlg = AlertDialog.Builder(act)
            .setTitle("Baixando atualização")
            .setView(box)
            .setCancelable(false)
            .create()
        dlg.show()

        Thread {
            try {
                val dir = File(act.cacheDir, "updates")
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val f = File(dir, "MeuCaixa.apk")
                val c = URL(info.url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.setRequestProperty("User-Agent", "MeuCaixa")
                val total = c.contentLength.toLong()
                c.inputStream.use { ins ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(16384)
                        var got = 0L
                        var last = -1
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            got += n
                            if (total > 0) {
                                val p = (got * 100 / total).toInt()
                                if (p != last) {
                                    last = p
                                    act.runOnUiThread {
                                        bar.progress = p
                                        label.text = "Baixando… $p%"
                                    }
                                }
                            }
                        }
                    }
                }
                c.disconnect()
                act.runOnUiThread {
                    dlg.dismiss()
                    install(act, f)
                }
            } catch (e: Exception) {
                act.runOnUiThread {
                    dlg.dismiss()
                    toast(act, "Falha no download. Tente de novo.")
                }
            }
        }.start()
    }

    private fun install(act: Activity, f: File) {
        try {
            val uri = FileProvider.getUriForFile(act, act.packageName + ".fileprovider", f)
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri, "application/vnd.android.package-archive")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            act.startActivity(i)
        } catch (e: Exception) {
            toast(act, "Não consegui abrir o instalador.")
        }
    }
}
