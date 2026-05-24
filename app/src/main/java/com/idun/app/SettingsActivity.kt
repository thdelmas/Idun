package com.idun.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.idun.app.bios.BiosClient
import com.idun.app.databinding.ActivitySettingsBinding

/**
 * Settings screen. Bios integration toggle is the only non-trivial control
 * for v1. Status reflects [BiosClient.status]; if the last push hit
 * `PENDING_APPROVAL`, surface a banner with a deep-link into Bios's
 * companion-apps screen (the user has to flip the per-app permission
 * there until the paired Bios update lands — see CLAUDE.md).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var biosClient: BiosClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        biosClient = BiosClient(this)

        binding.biosToggle.isChecked = biosClient.isEnabled
        binding.biosToggle.setOnCheckedChangeListener { _, checked ->
            biosClient.setEnabled(checked)
            refresh()
        }

        binding.biosOpenApp.setOnClickListener { openBiosApp() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val statusRes = when (biosClient.status()) {
            BiosClient.Status.NOT_INSTALLED -> R.string.settings_bios_status_not_installed
            BiosClient.Status.NOT_ENABLED -> R.string.settings_bios_status_not_enabled
            BiosClient.Status.CONNECTED -> R.string.settings_bios_status_connected
        }
        binding.biosStatus.setText(statusRes)

        val pending = biosClient.lastPushOutcome == BiosClient.LastPushOutcome.PENDING_APPROVAL
        binding.biosPendingBanner.visibility = if (pending) View.VISIBLE else View.GONE

        val installed = biosClient.status() != BiosClient.Status.NOT_INSTALLED
        binding.biosOpenApp.visibility = if (installed) View.VISIBLE else View.GONE
    }

    private fun openBiosApp() {
        val launch = packageManager.getLaunchIntentForPackage(BiosClient.BIOS_PACKAGE)
        if (launch == null) {
            Toast.makeText(this, R.string.settings_bios_status_not_installed, Toast.LENGTH_SHORT).show()
            return
        }
        launch.putExtra(BiosClient.BIOS_EXTRA_NAVIGATE_TO_COMPANIONS, true)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
    }
}
