package com.catatan.sekolah

import android.app.DatePickerDialog
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TaskFragment : Fragment(R.layout.fragment_list) {

    private lateinit var adapter: RowAdapter<Task>
    private lateinit var empty: TextView
    private var data = mutableListOf<Task>()
    private val fmt = SimpleDateFormat("EEE, d MMM yyyy", Locale("id", "ID"))
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
            Toast.makeText(requireContext(), "Token lihat saja: tugas hanya bisa dilihat. Pakai token Admin untuk mengubah.", Toast.LENGTH_LONG).show()
            return true
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.list)
        empty = view.findViewById(R.id.empty)
        empty.text = "Belum ada tugas.\nKetuk + untuk menambah."
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = RowAdapter(emptyList(), ::bindRow, { showDialog(it) }, ::confirmDelete)
        rv.adapter = adapter
        view.findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { showDialog(null) }
        refresh()
    }

    private fun refresh() {
        data = Store.tasks(requireContext())
        val prevCount = adapter.itemCount
        adapter.items = data.sortedWith(compareBy({ it.done }, { it.dueDate }))
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

    private fun bindRow(v: View, t: Task) {
        val title = v.findViewById<TextView>(R.id.title)
        val sub = v.findViewById<TextView>(R.id.sub)
        val check = v.findViewById<CheckBox>(R.id.check)
        title.text = t.title
        v.findViewById<View>(R.id.delete).setOnClickListener { confirmDelete(t) }
        val mapel = if (t.subject.isNotBlank()) "${t.subject} • " else ""
        sub.text = "$mapel" + "Deadline: ${fmt.format(t.dueDate)}"
        title.paintFlags =
            if (t.done) title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        check.setOnCheckedChangeListener(null)
        check.isChecked = t.done
        check.isEnabled = !Sync.readOnly(requireContext())
        check.setOnCheckedChangeListener { _, checked ->
            t.done = checked
            t.u = System.currentTimeMillis()
            Store.saveTasks(requireContext(), data)
            Scheduler.scheduleTask(requireContext(), t)
            Sync.scheduleSync(requireContext())
            view?.post { refresh() }
        }
    }

    private fun confirmDelete(t: Task) {
        if (blocked()) return
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus tugas?")
            .setMessage("“${t.title}” akan dihapus dan pengingatnya dibatalkan.")
            .setPositiveButton("Hapus") { _, _ ->
                Scheduler.cancelTask(requireContext(), t)
                data.removeAll { it.id == t.id }
                Store.saveTasks(requireContext(), data)
                Store.addTomb(requireContext(), "tasks", t.id)
                Sync.scheduleSync(requireContext())
                refresh()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showDialog(existing: Task?) {
        if (blocked()) return
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_task, null)
        val etTitle = v.findViewById<EditText>(R.id.etTitle)
        val etSubject = v.findViewById<EditText>(R.id.etSubject)
        val etNotes = v.findViewById<EditText>(R.id.etNotes)
        val btnDate = v.findViewById<Button>(R.id.btnDate)

        var due: Long = existing?.dueDate ?: Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 2)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        existing?.let {
            etTitle.setText(it.title); etSubject.setText(it.subject); etNotes.setText(it.notes)
        }
        btnDate.text = "Deadline: ${fmt.format(due)}"
        btnDate.setOnClickListener {
            val c = Calendar.getInstance().apply { timeInMillis = due }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                due = Calendar.getInstance().apply {
                    set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                btnDate.text = "Deadline: ${fmt.format(due)}"
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) "Tugas baru" else "Ubah tugas")
            .setView(v)
            .setPositiveButton("Simpan") { _, _ ->
                val title = etTitle.text.toString().trim()
                if (title.isEmpty()) return@setPositiveButton
                val t = existing ?: Task(System.currentTimeMillis(), "", "", due, "", false)
                t.title = title
                t.subject = etSubject.text.toString().trim()
                t.notes = etNotes.text.toString().trim()
                t.dueDate = due
                t.u = System.currentTimeMillis()
                if (existing == null) data.add(t)
                Store.saveTasks(requireContext(), data)
                Scheduler.scheduleTask(requireContext(), t)
                Sync.scheduleSync(requireContext())
                refresh()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
