package com.catatan.sekolah

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sinkron tugas + jadwal dengan Kas Kelas (website + bot) lewat Cloudflare Worker.
 * Data digabung di server per item: versi dengan stempel `u` terbaru menang; penghapusan memakai tanda hapus.
 * Token Admin (editor) dan Admin Kelas (staff) boleh mengubah; token lihat-saja hanya membaca (data lokal mengikuti data kelas).
 */
object Sync {
    private const val RELAY_URL = "https://icy-truth-bbad.fadilataufan14.workers.dev/"
    private const val RELAY_KEY = "rk_9fQ3vLx7TnB2wYc8Ud5HsKe1MzA6JpRg"

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false

    @Volatile
    private var again = false

    /** Dipanggil di thread utama setelah data lokal berubah karena sinkron. */
    var onChanged: (() -> Unit)? = null

    /** Dipanggil di thread utama bila teks status berubah. */
    var onStatus: ((String) -> Unit)? = null

    @Volatile
    var lastStatus: String = ""

    private fun prefs(c: Context) = c.getSharedPreferences("sync", Context.MODE_PRIVATE)

    fun token(c: Context): String = prefs(c).getString("token", "") ?: ""
    fun role(c: Context): String = prefs(c).getString("role", "") ?: ""
    fun connected(c: Context): Boolean = token(c).isNotEmpty()

    /** Terhubung dengan token lihat-saja: tugas dan jadwal hanya bisa dilihat. */
    fun readOnly(c: Context): Boolean = connected(c) && role(c) != "editor" && role(c) != "staff"

    fun statusText(c: Context): String {
        if (!connected(c)) return "Belum tersambung ke Kas Kelas"
        val r = when (role(c)) { "editor" -> "Admin"; "staff" -> "Admin Kelas"; else -> "Lihat saja" }
        return if (lastStatus.isNotEmpty()) "Kas Kelas ($r) • $lastStatus" else "Kas Kelas ($r)"
    }

    fun connect(c: Context, token: String, role: String) {
        prefs(c).edit().putString("token", token.trim().uppercase()).putString("role", role).apply()
        setStatus(c, "menyambung…")
    }

    fun disconnect(c: Context) {
        prefs(c).edit().clear().apply()
        lastStatus = ""
        setStatus(c, "")
    }

    private fun setStatus(c: Context, text: String) {
        lastStatus = text
        val msg = statusText(c)
        main.post { onStatus?.invoke(msg) }
    }

    // ---------- format tanggal ----------
    private fun dueStr(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

    private fun dueMs(s: String): Long = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }

    // ---------- jaringan ----------
    private fun buildBody(
        token: String, tasks: List<Task>, lessons: List<Lesson>,
        dt: Map<Long, Long>, dl: Map<Long, Long>
    ): String {
        val ta = JSONArray()
        for (t in tasks) {
            ta.put(
                JSONObject().put("id", t.id).put("title", t.title).put("subject", t.subject)
                    .put("due", dueStr(t.dueDate)).put("notes", t.notes).put("done", t.done)
                    .put("u", t.u)
            )
        }
        val la = JSONArray()
        for (l in lessons) {
            la.put(
                JSONObject().put("id", l.id).put("d", dayName(l.day)).put("n", l.subject)
                    .put("s", l.start).put("e", l.end).put("r", l.room).put("u", l.u)
            )
        }
        val dtj = JSONObject()
        for ((k, v) in dt) dtj.put(k.toString(), v)
        val dlj = JSONObject()
        for ((k, v) in dl) dlj.put(k.toString(), v)
        val ns = JSONObject().put("t", token).put("tasks", ta).put("lessons", la)
            .put("del", JSONObject().put("tasks", dtj).put("lessons", dlj))
        return JSONObject().put("k", RELAY_KEY).put("ns", ns).toString()
    }

    private fun post(body: String): JSONObject {
        val conn = URL(RELAY_URL).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 25000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    /** Memeriksa token tanpa mengirim data. cb(role, pesanError) dipanggil di thread utama. */
    fun probe(c: Context, token: String, cb: (String?, String) -> Unit) {
        Thread {
            var role: String? = null
            var err = ""
            try {
                val j = post(buildBody(token.trim().uppercase(), emptyList(), emptyList(), emptyMap(), emptyMap()))
                if (j.optBoolean("ok")) role = j.optString("role") else err = j.optString("error", "Gagal.")
            } catch (e: Exception) {
                err = "Tidak bisa terhubung ke server. Periksa internet."
            }
            main.post { cb(role, err) }
        }.start()
    }

    private var pendingRunnable: Runnable? = null

    /** Jadwalkan sinkron sebentar lagi (dipanggil setelah perubahan lokal). */
    fun scheduleSync(c: Context) {
        if (!connected(c)) return
        val app = c.applicationContext
        pendingRunnable?.let { main.removeCallbacks(it) }
        val r = Runnable { syncNow(app) }
        pendingRunnable = r
        main.postDelayed(r, 1200)
    }

    fun syncNow(c: Context) {
        val app = c.applicationContext
        if (!connected(app)) return
        if (running) {
            again = true
            return
        }
        running = true
        val token = token(app)
        val tasks = Store.tasks(app)
        val lessons = Store.lessons(app)
        val dt = Store.tombs(app, "tasks")
        val dl = Store.tombs(app, "lessons")
        val rev = Store.rev
        setStatus(app, "menyinkronkan…")
        Thread {
            var result: JSONObject? = null
            var err = ""
            try {
                val j = post(buildBody(token, tasks, lessons, dt, dl))
                if (j.optBoolean("ok")) result = j else err = j.optString("error", "Gagal.")
            } catch (e: Exception) {
                err = "Tidak bisa terhubung. Periksa internet."
            }
            main.post { finish(app, result, err, rev) }
        }.start()
    }

    private fun finish(app: Context, result: JSONObject?, err: String, rev: Long) {
        running = false
        if (result == null) {
            setStatus(app, "gagal: $err")
        } else if (Store.rev != rev) {
            again = true // ada perubahan lokal saat menunggu: kirim ulang, jangan timpa
        } else {
            try {
                apply(app, result)
                val hm = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                setStatus(app, "tersinkron $hm")
                onChanged?.invoke()
            } catch (e: Exception) {
                setStatus(app, "gagal membaca data server")
            }
        }
        if (again) {
            again = false
            syncNow(app)
        }
    }

    private fun apply(app: Context, j: JSONObject) {
        val oldTasks = Store.tasks(app)
        val newTasks = mutableListOf<Task>()
        val ta = j.optJSONArray("tasks") ?: JSONArray()
        for (i in 0 until ta.length()) {
            val o = ta.getJSONObject(i)
            newTasks.add(
                Task(
                    o.getLong("id"), o.getString("title"), o.optString("subject"),
                    dueMs(o.optString("due")), o.optString("notes"), o.optBoolean("done"),
                    o.optLong("u", 0)
                )
            )
        }
        val newLessons = mutableListOf<Lesson>()
        val la = j.optJSONArray("lessons") ?: JSONArray()
        for (i in 0 until la.length()) {
            val o = la.getJSONObject(i)
            val idx = DAY_NAMES.indexOf(o.optString("d"))
            val s = o.optString("s")
            val e = o.optString("e")
            if (idx < 0 || s.isEmpty() || e.isEmpty()) continue // jadwal tanpa jam yang bisa dibaca hanya tampil di website
            newLessons.add(
                Lesson(
                    o.getLong("id"), DAY_ORDER[idx], o.getString("n"), s, e,
                    o.optString("r"), o.optLong("u", 0)
                )
            )
        }
        val del = j.optJSONObject("del")
        val tombT = readTombs(del?.optJSONObject("tasks"))
        val tombL = readTombs(del?.optJSONObject("lessons"))

        for (old in oldTasks) {
            if (newTasks.none { it.id == old.id }) Scheduler.cancelTask(app, old)
        }
        Store.saveTasks(app, newTasks, true)
        Store.saveLessons(app, newLessons, true)
        Store.saveTombs(app, "tasks", tombT, true)
        Store.saveTombs(app, "lessons", tombL, true)
        val role = j.optString("role")
        if (role.isNotEmpty()) prefs(app).edit().putString("role", role).apply()
        Scheduler.scheduleAll(app)
    }

    private fun readTombs(o: JSONObject?): Map<Long, Long> {
        val out = mutableMapOf<Long, Long>()
        if (o == null) return out
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val id = k.toLongOrNull() ?: continue
            out[id] = o.optLong(k, 0)
        }
        return out
    }
}
