package com.catatan.sekolah

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ScheduleFragment : Fragment(R.layout.fragment_list) {

    private lateinit var adapter: RowAdapter<Lesson>
    private lateinit var empty: TextView
    private var data = mutableListOf<Lesson>()
    private val listener: () -> Unit = { if (isAdded && view != null) refresh() }

    override fun onStart() {
        super.onStart()
        Sync.onChanged = listener
        refresh()
    }

    override fun onStop() {
        super.onStop()
        if (Sync.onChanged === listener) Sync.onChanged = null
    }

    private fun blocked(): Boolean {
        if (Sync.readOnly(requireContext())) {
            Toast.makeText(requireContext(), "Token lihat saja: jadwal hanya bisa dilihat. Pakai token Admin untuk mengubah.", Toast.LENGTH_LONG).show()
            return true
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.list)
        empty = view.findViewById(R.id.empty)
        empty.text = "Belum ada jadwal.\nKetuk + untuk menambah."
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = RowAdapter(emptyList(), ::bindRow, { showDialog(it) }, ::confirmDelete)
        rv.adapter = adapter
        view.findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { showDialog(null) }
        refresh()
    }

    private fun refresh() {
        data = Store.lessons(requireContext())
        val prevCount = adapter.itemCount
        adapter.items = data.sortedWith(compareBy({ DAY_ORDER.indexOf(it.day) }, { it.start }))
        val wasEmpty = prevCount == 0
        adapter.notifyDataSetChanged()
        if (wasEmpty && data.isNotEmpty()) {
            view?.findViewById<RecyclerView>(R.id.list)?.scheduleLayoutAnimation()
        }
        empty.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
        view?.findViewById<FloatingActionButton>(R.id.fab)?.let {
            if (Sync.readOnly(requireContext())) it.hide() else it.show()
        }
    }

    private fun bindRow(v: View, l: Lesson) {
        v.findViewById<View>(R.id.check).visibility = View.GONE
        v.findViewById<View>(R.id.delete).setOnClickListener { confirmDelete(l) }
        v.findViewById<TextView>(R.id.title).text = l.subject
        val room = if (l.room.isNotBlank()) " • ${l.room}" else ""
        v.findViewById<TextView>(R.id.sub).text =
            "${dayName(l.day)} • ${l.start}–${l.end}$room"
    }

    private fun confirmDelete(l: Lesson) {
        if (blocked()) return
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus jadwal?")
            .setMessage("“${l.subject}” akan dihapus dari jadwal.")
            .setPositiveButton("Hapus") { _, _ ->
                data.removeAll { it.id == l.id }
                Store.saveLessons(requireContext(), data)
                Store.addTomb(requireContext(), "lessons", l.id)
                Sync.scheduleSync(requireContext())
                refresh()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showDialog(existing: Lesson?) {
        if (blocked()) return
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_lesson, null)
        val spDay = v.findViewById<Spinner>(R.id.spDay)
        val etSubject = v.findViewById<EditText>(R.id.etSubject)
        val etRoom = v.findViewById<EditText>(R.id.etRoom)
        val btnStart = v.findViewById<Button>(R.id.btnStart)
        val btnEnd = v.findViewById<Button>(R.id.btnEnd)

        var start = existing?.start ?: "07:00"
        var end = existing?.end ?: "08:30"
        existing?.let {
            spDay.setSelection(DAY_ORDER.indexOf(it.day).coerceAtLeast(0))
            etSubject.setText(it.subject); etRoom.setText(it.room)
        }
        btnStart.text = "Mulai $start"
        btnEnd.text = "Selesai $end"

        fun pick(current: String, onPicked: (String) -> Unit) {
            val h = current.substringBefore(":").toInt()
            val m = current.substringAfter(":").toInt()
            TimePickerDialog(requireContext(), { _, hh, mm ->
                onPicked("%02d:%02d".format(hh, mm))
            }, h, m, true).show()
        }
        btnStart.setOnClickListener { pick(start) { start = it; btnStart.text = "Mulai $it" } }
        btnEnd.setOnClickListener { pick(end) { end = it; btnEnd.text = "Selesai $it" } }

        AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) "Jadwal baru" else "Ubah jadwal")
            .setView(v)
            .setPositiveButton("Simpan") { _, _ ->
                val subject = etSubject.text.toString().trim()
                if (subject.isEmpty()) return@setPositiveButton
                val l = existing ?: Lesson(System.currentTimeMillis(), 0, "", start, end, "")
                l.day = DAY_ORDER[spDay.selectedItemPosition]
                l.subject = subject
                l.room = etRoom.text.toString().trim()
                l.start = start
                l.end = end
                l.u = System.currentTimeMillis()
                if (existing == null) data.add(l)
                Store.saveLessons(requireContext(), data)
                Sync.scheduleSync(requireContext())
                refresh()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
