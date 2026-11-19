package com.eksim.track

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class F(val key: String, val label: String, val type: Char = 'T', val opts: List<String> = emptyList())

val STATUS = listOf("", "DRAFT", "COPY", "TELEX RELEASE", "SURRENDERED", "ORIGINAL BL", "ORIGINAL BANK")
val KET = listOf("", "NOM", "FREE")
val EXP_CHECK = listOf(
    "Shipping Instruction (SI)", "Booking Confirmation", "Stuffing Cargo", "Final SI", "PEB / NPE / VGM",
    "Draft B/L", "Draft B/L Approved", "MBL Issued", "HBL Issued", "Request Invoice Carrier",
    "Local Charge", "D/N Agent", "Pre Alert Sent", "Telex Release / Original B/L"
)
val NAME = mapOf("efcl" to "Ekspor FCL", "elcl" to "Ekspor LCL", "ifcl" to "Impor FCL")
val SCHEMA = mapOf(
    "efcl" to listOf(
        F("bl", "BL No"), F("mbl", "MBL"), F("vessel", "Vessel"), F("shipper", "Shipper"),
        F("etd", "ETD", 'D'), F("eta", "ETA", 'D'), F("kgs", "KGS", 'N'), F("cbm", "CBM", 'N'),
        F("ket", "KET (NOM/FREE)", 'S', KET), F("pod", "POD"), F("party", "Party (mis. 1X20)"),
        F("carrier", "Carrier"), F("agen", "Agen"), F("cnee", "CNEE PU DO"), F("status", "Status BL", 'S', STATUS)
    ),
    "elcl" to listOf(
        F("bl", "BL No"), F("mbl", "MBL"), F("vessel", "Vessel"), F("shipper", "Shipper"),
        F("etd", "ETD", 'D'), F("eta", "ETA", 'D'), F("consignee", "Consignee"), F("kgs", "KGS", 'N'),
        F("cbm", "CBM", 'N'), F("ket", "KET (NOM/FREE)", 'S', KET), F("pod", "POD"), F("agen", "Agen"),
        F("coloader", "Coloader"), F("cnee", "CNEE PU DO"), F("coo", "COO", 'S', listOf("", "V", "X")),
        F("status", "Status BL", 'S', STATUS)
    ),
    "ifcl" to listOf(
        F("consignee", "Consignee"), F("bl", "BL No"), F("shipper", "Shipper"), F("vessel", "Mother Vessel"),
        F("pol", "POL"), F("etd", "ETD", 'D'), F("eta1", "ETA Transit", 'D'), F("eta", "ETA Tiba", 'D'),
        F("feeder", "Feeder Vessel"), F("carrier", "Carrier"), F("kgs", "KGS", 'N'), F("cbm", "CBM", 'N'),
        F("pkg", "Packages"), F("party", "Party (mis. 3X40)"), F("cont", "No. Container"), F("pic", "PIC / Agen"),
        F("status", "Status BL", 'S', STATUS)
    )
)
val CHECK = mapOf("efcl" to EXP_CHECK, "elcl" to EXP_CHECK, "ifcl" to listOf("S", "SJ", "TC", "NOA", "SM", "BC"))

class MainActivity : Activity() {
    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val idLoc = Locale("id", "ID")
    private var back: (() -> Unit)? = null
    private val blue = 0xFF1565C0.toInt()
    private val green = 0xFF2E7D32.toInt()
    private val red = 0xFFC62828.toInt()

    override fun onCreate(b: Bundle?) { super.onCreate(b); home() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { val f = back; if (f != null) f() else super.onBackPressed() }

    // ---------- helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun col() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(24)) }
    private fun LinearLayout.add(v: View, top: Int = 6): View {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(top); addView(v, lp); return v
    }
    private fun btn(t: String, c: Int = blue, f: () -> Unit) = Button(this).apply {
        text = t; setTextColor(Color.WHITE); setBackgroundColor(c); textSize = 16f; setOnClickListener { f() }
    }
    private fun tv(t: String, sz: Float = 14f, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sz; setTextColor(Color.BLACK); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun show(v: View) = setContentView(ScrollView(this).apply { addView(v) })
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun parse(s: String): Date? = try { if (s.isEmpty()) null else iso.parse(s) } catch (e: Exception) { null }
    private fun fmt(s: String) = parse(s)?.let { SimpleDateFormat("dd-MMM", idLoc).format(it) } ?: "-"
    private fun fmtFull(s: String) = parse(s)?.let { SimpleDateFormat("dd-MMM-yyyy", idLoc).format(it) } ?: ""
    private fun month(s: String) = parse(s)?.let { SimpleDateFormat("MMMM yyyy", idLoc).format(it).uppercase(idLoc) } ?: "TANPA TANGGAL"

    /** Sisa hari ke ETA & prioritas (impor) */
    private fun prio(eta: String): Pair<String, String> {
        val d = parse(eta) ?: return Pair("INPUT ETA", "INPUT ETA")
        val t = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val days = Math.round((d.time - t.timeInMillis) / 86400000.0).toInt()
        return when {
            days <= 0 -> Pair("DONE", "DONE")
            days <= 3 -> Pair("$days", "HIGH")
            days <= 7 -> Pair("$days", "MEDIUM")
            else -> Pair("$days", "LOW")
        }
    }

    // ---------- storage ----------
    private fun file(k: String) = File(filesDir, "$k.json")
    private fun load(k: String): MutableList<JSONObject> {
        val f = file(k); if (!f.exists()) return mutableListOf()
        val a = JSONArray(f.readText()); return MutableList(a.length()) { a.getJSONObject(it) }
    }
    private fun save(k: String, l: List<JSONObject>) = file(k).writeText(JSONArray(l).toString())
    private fun dateKey(k: String) = if (k == "ifcl") "eta" else "etd"
    private fun sorted(k: String) = load(k).sortedWith(compareBy<JSONObject>({ it.optString(dateKey(k)).ifEmpty { "9999" } }, { it.optLong("id") }))

    // ---------- screens ----------
    private fun home() {
        back = null; title = "Ekspor Impor"
        val c = col(); c.gravity = Gravity.CENTER
        c.add(tv("Pilih menu", 18f, true).apply { gravity = Gravity.CENTER }, 40)
        c.add(btn("EKSPOR") { expMenu() }, 24).minimumHeight = dp(90)
        c.add(btn("IMPOR", green) { list("ifcl") }, 16).minimumHeight = dp(90)
        show(c)
    }

    private fun expMenu() {
        back = { home() }; title = "Ekspor"
        val c = col()
        c.add(btn("EKSPOR FCL") { list("efcl") }, 24).minimumHeight = dp(80)
        c.add(btn("EKSPOR LCL", 0xFF6A1B9A.toInt()) { list("elcl") }, 16).minimumHeight = dp(80)
        show(c)
    }

    private fun list(k: String) {
        back = { if (k == "ifcl") home() else expMenu() }; title = NAME[k]
        val c = col()
        val bar = LinearLayout(this)
        bar.addView(btn("+ TAMBAH") { form(k, null) }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        bar.addView(btn("SIMPAN CSV", green) { exportCsv(k) }, LinearLayout.LayoutParams(0, -2, 1f))
        c.add(bar, 0)
        var last = "#"; var no = 0
        val data = sorted(k)
        for (r in data) {
            val m = month(r.optString(dateKey(k)))
            if (m != last) { c.add(tv(m, 17f, true).apply { setTextColor(blue) }, 16); last = m; no = 0 }
            no++; c.add(card(k, r, no))
        }
        if (data.isEmpty()) c.add(tv("Belum ada data. Tekan + TAMBAH."), 24)
        show(c)
    }

    private fun card(k: String, r: JSONObject, no: Int): View {
        val ch = CHECK[k]!!
        val doneIdx = ch.indices.filter { r.optBoolean("c$it") }
        val sb = StringBuilder("$no. ${r.optString("bl").ifEmpty { "(tanpa BL)" }}  •  ${r.optString("vessel")}\n")
        var bg = 0xFFEEEEEE.toInt()
        if (k == "ifcl") {
            val (d, p) = prio(r.optString("eta"))
            sb.append("${r.optString("consignee")} | ${r.optString("party")} | ETA ${fmt(r.optString("eta"))}\n")
            sb.append("Sisa hari: $d  |  $p\n")
            sb.append("Proses: ${if (doneIdx.isEmpty()) "-" else doneIdx.joinToString(" ") { ch[it] + "✓" }}\n")
            if (p == "HIGH") bg = 0xFFFFCDD2.toInt() else if (p == "MEDIUM") bg = 0xFFFFF9C4.toInt()
        } else {
            sb.append("${r.optString("shipper")} → ${r.optString("pod")}\nETD ${fmt(r.optString("etd"))}  ETA ${fmt(r.optString("eta"))}  |  ${r.optString("party")}\n")
            val next = ch.indices.firstOrNull { !r.optBoolean("c$it") }
            sb.append("Proses ${doneIdx.size}/${ch.size}" + (if (next != null) "  •  Berikutnya: ${ch[next]}" else "  •  SELESAI") + "\n")
        }
        sb.append("Status BL: ${r.optString("status").ifEmpty { "-" }}")
        if (doneIdx.size == ch.size) bg = 0xFFC8E6C9.toInt()
        return tv(sb.toString()).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundColor(bg)
            setOnClickListener { form(k, r) }
        }
    }

    private fun form(k: String, r0: JSONObject?) {
        back = { list(k) }; title = (if (r0 == null) "Tambah " else "Edit ") + NAME[k]
        val r = r0 ?: JSONObject().put("id", System.currentTimeMillis())
        val c = col(); val ops = mutableListOf<() -> Unit>()
        for (f in SCHEMA[k]!!) {
            c.add(tv(f.label, 12f, true), 10)
            when (f.type) {
                'S' -> {
                    val sp = Spinner(this)
                    sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, f.opts.map { it.ifEmpty { "-" } })
                    sp.setSelection(maxOf(0, f.opts.indexOf(r.optString(f.key))))
                    c.add(sp, 0); ops.add { r.put(f.key, f.opts[sp.selectedItemPosition]) }
                }
                'D' -> {
                    var v = r.optString(f.key)
                    val b = Button(this); b.text = if (v.isEmpty()) "Pilih tanggal" else fmtFull(v)
                    b.setOnClickListener {
                        val cal = Calendar.getInstance(); parse(v)?.let { cal.time = it }
                        DatePickerDialog(this, { _, y, m, d ->
                            v = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d); b.text = fmtFull(v)
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }
                    b.setOnLongClickListener { v = ""; b.text = "Pilih tanggal"; toast("Tanggal dikosongkan"); true }
                    c.add(b, 0); ops.add { r.put(f.key, v) }
                }
                else -> {
                    val e = EditText(this); e.setText(r.optString(f.key))
                    if (f.type == 'N') {
                        e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        e.keyListener = DigitsKeyListener.getInstance("0123456789.,")
                    } else e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                    c.add(e, 0); ops.add { r.put(f.key, e.text.toString().trim()) }
                }
            }
        }
        c.add(tv(if (k == "ifcl") "CHECKLIST PROSES" else "EXPORT CHECK SHEET", 16f, true).apply { setTextColor(blue) }, 20)
        CHECK[k]!!.forEachIndexed { i, n ->
            val cb = CheckBox(this); cb.text = n; cb.textSize = 15f; cb.isChecked = r.optBoolean("c$i")
            c.add(cb, 0); ops.add { r.put("c$i", cb.isChecked) }
        }
        c.add(btn("SIMPAN", green) {
            ops.forEach { it() }
            val l = load(k); l.removeAll { it.optLong("id") == r.optLong("id") }; l.add(r); save(k, l)
            toast("Tersimpan"); list(k)
        }, 20)
        if (r0 != null) c.add(btn("HAPUS", red) {
            AlertDialog.Builder(this).setMessage("Hapus data ini?")
                .setPositiveButton("Hapus") { _, _ ->
                    val l = load(k); l.removeAll { it.optLong("id") == r.optLong("id") }; save(k, l); list(k)
                }.setNegativeButton("Batal", null).show()
        }, 10)
        show(c)
    }

    // ---------- CSV (bisa dibuka di Excel) ----------
    private fun exportCsv(k: String) {
        val fs = SCHEMA[k]!!; val ch = CHECK[k]!!; val imp = k == "ifcl"
        val q = { s: String -> "\"" + s.replace("\"", "\"\"") + "\"" }
        val head = listOf("NO", "TYPE") + fs.map { it.label } + (if (imp) listOf("SISA HARI", "PRIORITAS") else emptyList()) + ch
        val sb = StringBuilder("﻿" + head.joinToString(";") { q(it) } + "\n")
        sorted(k).forEachIndexed { i, r ->
            val p = prio(r.optString("eta"))
            val row = listOf("${i + 1}", if (k == "elcl") "LCL" else "FCL") +
                fs.map { if (it.type == 'D') fmtFull(r.optString(it.key)) else r.optString(it.key) } +
                (if (imp) listOf(p.first, p.second) else emptyList()) +
                ch.indices.map { if (r.optBoolean("c$it")) "V" else "" }
            sb.append(row.joinToString(";") { q(it) }).append("\n")
        }
        val name = "${NAME[k]!!.replace(" ", "_")}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.csv"
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
                contentResolver.openOutputStream(uri)!!.use { it.write(sb.toString().toByteArray()) }
                toast("Disimpan di folder Download: $name")
            } else {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, name).putExtra(Intent.EXTRA_TEXT, sb.toString()), "Bagikan CSV"))
            }
        } catch (e: Exception) { toast("Gagal: ${e.message}") }
    }
}
