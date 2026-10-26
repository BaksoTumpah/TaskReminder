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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) toast("Izin notifikasi ditolak, pengingat tidak akan muncul.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "Catatan Sekolah v${BuildConfig.VERSION_NAME}"

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
            val f = if (it.itemId == R.id.nav_tugas) TaskFragment() else ScheduleFragment()
            supportFragmentManager.beginTransaction().replace(R.id.container, f).commit()
            true
        }
        if (savedInstanceState == null) nav.selectedItemId = R.id.nav_tugas
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_test -> { testNotif(); true }
        R.id.menu_settings -> { openNotifSettings(); true }
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
