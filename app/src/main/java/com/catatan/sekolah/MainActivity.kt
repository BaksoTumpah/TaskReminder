package com.catatan.sekolah

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.PopupWindow
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.TextView
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private var curTab = -1

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) toast("Izin notifikasi ditolak, pengingat tidak akan muncul.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Notifier.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33) {
            val ok = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!ok) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!Notifier.areEnabled(this)) {
            toast("Notifikasi dimatikan. Aktifkan lewat menu Pengaturan notifikasi.")
        }
        Scheduler.scheduleAll(this)

        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.setOnItemSelectedListener {
            val idx = if (it.itemId == R.id.nav_tugas) 0 else 1
            val f = if (idx == 0) TaskFragment() else ScheduleFragment()
            val ft = supportFragmentManager.beginTransaction()
            if (curTab >= 0 && curTab != idx) {
                val fwd = idx > curTab
                ft.setCustomAnimations(
                    if (fwd) R.anim.slide_in_right else R.anim.slide_in_left,
                    if (fwd) R.anim.slide_out_left else R.anim.slide_out_right
                )
            }
            curTab = idx
            ft.replace(R.id.container, f).commit()
            true
        }
        if (savedInstanceState == null) nav.selectedItemId = R.id.nav_tugas
        findViewById<ImageButton>(R.id.btn_more).setOnClickListener { showMenu(it) }
        Sync.onStatus = { setStatusText(it) }
        setStatusText(Sync.statusText(this))
    }

    private fun setStatusText(text: String) {
        findViewById<TextView>(R.id.header_status)?.text = text
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Menu titik tiga: popover membulat bergaya iOS dengan animasi memantul dari pojok kanan atas. */
    private fun showMenu(anchor: View) {
        val v = layoutInflater.inflate(R.layout.popup_menu, null)
        val pw = PopupWindow(v, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        pw.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pw.animationStyle = R.style.App_PopupAnim
        fun bind(id: Int, action: () -> Unit) {
            v.findViewById<View>(id).setOnClickListener { pw.dismiss(); action() }
        }
        bind(R.id.pm_sync) { syncMenu() }
        bind(R.id.pm_test) { testNotif() }
        bind(R.id.pm_notif) { openNotifSettings() }
        bind(R.id.pm_about) { toast("Tugas v${BuildConfig.VERSION_NAME}") }
        val w = dp(236 + 32)
        pw.showAsDropDown(anchor, -(w - anchor.width) + dp(10), -dp(6))
    }

    override fun onResume() {
        super.onResume()
        setStatusText(Sync.statusText(this))
        Sync.syncNow(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Sync.onStatus = null
    }

    private fun syncMenu() {
        if (!Sync.connected(this)) { connectDialog(); return }
        AlertDialog.Builder(this)
            .setTitle("Kas Kelas")
            .setMessage(Sync.statusText(this))
            .setPositiveButton("Sinkron sekarang") { _, _ -> Sync.syncNow(this) }
            .setNeutralButton("Putuskan") { _, _ ->
                Sync.disconnect(this)
                setStatusText(Sync.statusText(this))
                toast("Terputus. Data tetap tersimpan di HP ini.")
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun connectDialog() {
        val et = EditText(this).apply {
            hint = "Token Kas Kelas"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Sambungkan ke Kas Kelas")
            .setMessage("Masukkan token Kas Kelas. Token Admin dan Admin Kelas bisa mengubah tugas & jadwal; token pengguna hanya melihat.")
            .setView(et)
            .setPositiveButton("Sambungkan") { _, _ ->
                val t = et.text.toString().trim().uppercase()
                if (t.isEmpty()) { toast("Token kosong."); return@setPositiveButton }
                toast("Memeriksa token…")
                Sync.probe(this, t) { role, err ->
                    if (role == null) toast(err)
                    else if (role == "editor" || role == "staff") finishConnect(t, role)
                    else AlertDialog.Builder(this)
                        .setTitle("Token lihat saja")
                        .setMessage("Dengan token ini data tugas & jadwal di HP akan DIGANTI dengan data kelas dan tidak bisa diubah dari aplikasi. Lanjutkan?")
                        .setPositiveButton("Lanjutkan") { _, _ -> finishConnect(t, role) }
                        .setNegativeButton("Batal", null)
                        .show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun finishConnect(token: String, role: String) {
        if (role != "editor" && role != "staff") {
            for (t in Store.tasks(this)) Scheduler.cancelTask(this, t)
            Store.saveTasks(this, mutableListOf(), true)
            Store.saveLessons(this, mutableListOf(), true)
            Store.saveTombs(this, "tasks", emptyMap(), true)
            Store.saveTombs(this, "lessons", emptyMap(), true)
        }
        Sync.connect(this, token, role)
        setStatusText(Sync.statusText(this))
        Sync.syncNow(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_test -> { testNotif(); true }
        R.id.menu_settings -> { openNotifSettings(); true }
        R.id.menu_sync -> { syncMenu(); true }
        R.id.menu_about -> { toast("Tugas v${BuildConfig.VERSION_NAME}"); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun testNotif() {
        if (!Notifier.areEnabled(this)) {
            toast("Notifikasi dimatikan. Aktifkan dulu di pengaturan.")
            openNotifSettings()
            return
        }
        Notifier.show(
            this, 999, "Tes notifikasi",
            "Kalau pesan ini muncul di bar notifikasi, pengingat berfungsi normal."
        )
    }

    private fun openNotifSettings() {
        val i = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
        }
        startActivity(i)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
