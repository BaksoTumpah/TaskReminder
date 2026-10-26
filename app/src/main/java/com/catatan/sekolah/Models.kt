package com.catatan.sekolah

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Task(
    val id: Long,
    var title: String,
    var subject: String,
    var dueDate: Long, // milidetik, awal hari deadline
    var notes: String,
    var done: Boolean
)

data class Lesson(
    val id: Long,
    var day: Int, // Calendar.MONDAY .. Calendar.SUNDAY
    var subject: String,
    var start: String, // "HH:mm"
    var end: String,
    var room: String
)

val DAY_ORDER = intArrayOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
    Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
)
val DAY_NAMES = arrayOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

fun dayName(day: Int): String = DAY_NAMES[DAY_ORDER.indexOf(day).coerceAtLeast(0)]

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("data", Context.MODE_PRIVATE)

    fun tasks(c: Context): MutableList<Task> {
        val out = mutableListOf<Task>()
        try {
            val arr = JSONArray(prefs(c).getString("tasks", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Task(
                        o.getLong("id"), o.getString("title"), o.optString("subject"),
                        o.getLong("dueDate"), o.optString("notes"), o.optBoolean("done")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun saveTasks(c: Context, list: List<Task>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("title", it.title).put("subject", it.subject)
                    .put("dueDate", it.dueDate).put("notes", it.notes).put("done", it.done)
            )
        }
        prefs(c).edit().putString("tasks", arr.toString()).apply()
    }

    fun lessons(c: Context): MutableList<Lesson> {
        val out = mutableListOf<Lesson>()
        try {
            val arr = JSONArray(prefs(c).getString("lessons", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Lesson(
                        o.getLong("id"), o.getInt("day"), o.getString("subject"),
                        o.getString("start"), o.getString("end"), o.optString("room")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun saveLessons(c: Context, list: List<Lesson>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("day", it.day).put("subject", it.subject)
                    .put("start", it.start).put("end", it.end).put("room", it.room)
            )
        }
        prefs(c).edit().putString("lessons", arr.toString()).apply()
    }
}
