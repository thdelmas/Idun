package com.idun.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.idun.app.bios.BiosClient
import com.idun.app.databinding.ActivitySettingsBinding
import com.idun.app.reminders.ReminderScheduler
import com.idun.app.reminders.ReminderSettings
import kotlinx.coroutines.launch

/**
 * Settings screen. Two sections:
 * - Language: lets the user override the system locale for Idun via
 *   [AppCompatDelegate.setApplicationLocales]. Empty list = follow system.
 *   Backed by the app's `locales_config.xml` whitelist.
 * - Bios integration: toggles meal-event writes. Status reflects
 *   [BiosClient.status]; if the last push hit `PENDING_APPROVAL`, surface
 *   a banner with a deep-link into Bios's companion-apps screen (the user
 *   has to flip the per-app permission there until the paired Bios update
 *   lands — see CLAUDE.md).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var biosClient: BiosClient
    private lateinit var reminderSettings: ReminderSettings

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) enableReminders() else {
                Toast.makeText(this, R.string.settings_reminders_permission_denied, Toast.LENGTH_SHORT).show()
                binding.remindersToggle.isChecked = false
            }
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        biosClient = BiosClient(this)
        reminderSettings = ReminderSettings(this)

        binding.languageRow.setOnClickListener { showLanguagePicker() }

        binding.remindersToggle.isChecked = reminderSettings.enabled
        binding.remindersToggle.setOnCheckedChangeListener { _, checked ->
            if (checked) requestEnableReminders() else disableReminders()
        }
        binding.remindersLeadRow.setOnClickListener { showLeadPicker() }

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

    private fun showLanguagePicker() {
        val current = AppCompatDelegate.getApplicationLocales()
            .takeIf { !it.isEmpty }
            ?.get(0)
            ?.language
        val labels = LANGUAGE_OPTIONS.map { (_, labelRes) -> getString(labelRes) }.toTypedArray()
        val checked = LANGUAGE_OPTIONS.indexOfFirst { it.first == current }.coerceAtLeast(0)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings_language_dialog_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val tag = LANGUAGE_OPTIONS[which].first
                val locales = if (tag == null) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(tag)
                }
                AppCompatDelegate.setApplicationLocales(locales)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refresh() {
        val active = AppCompatDelegate.getApplicationLocales()
            .takeIf { !it.isEmpty }
            ?.get(0)
            ?.language
        val labelRes = LANGUAGE_OPTIONS.firstOrNull { it.first == active }?.second
            ?: R.string.settings_language_system_default
        binding.languageValue.setText(labelRes)

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

        binding.remindersLeadRow.visibility = if (reminderSettings.enabled) View.VISIBLE else View.GONE
        binding.remindersLeadValue.text = leadLabel(reminderSettings.leadMinutes)
        // Enabled in-app but blocked at the OS level — tell the user why nothing fires.
        val blocked = reminderSettings.enabled && !NotificationManagerCompat.from(this).areNotificationsEnabled()
        binding.remindersHint.visibility = if (blocked) View.VISIBLE else View.GONE
    }

    private fun requestEnableReminders() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableReminders()
            refresh()
        }
    }

    private fun enableReminders() {
        reminderSettings.enabled = true
        reschedule()
    }

    private fun disableReminders() {
        reminderSettings.enabled = false
        reschedule()
        refresh()
    }

    private fun showLeadPicker() {
        val labels = ReminderSettings.LEAD_OPTIONS.map { leadLabel(it) }.toTypedArray()
        val checked = ReminderSettings.LEAD_OPTIONS.indexOf(reminderSettings.leadMinutes).coerceAtLeast(0)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings_reminders_lead_label)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                reminderSettings.leadMinutes = ReminderSettings.LEAD_OPTIONS[which]
                dialog.dismiss()
                reschedule()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun leadLabel(minutes: Int): String = when {
        minutes == 0 -> getString(R.string.settings_reminders_lead_at_time)
        minutes % 60 == 0 -> resources.getQuantityString(R.plurals.reminders_lead_hours, minutes / 60, minutes / 60)
        else -> resources.getQuantityString(R.plurals.reminders_lead_minutes, minutes, minutes)
    }

    private fun reschedule() {
        lifecycleScope.launch { ReminderScheduler(applicationContext).reschedule() }
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

    private companion object {
        // null tag = follow system. Order is the order shown in the picker.
        // Must stay in sync with res/xml/locales_config.xml.
        val LANGUAGE_OPTIONS: List<Pair<String?, Int>> = listOf(
            null to R.string.settings_language_system_default,
            "en" to R.string.settings_language_english,
            "es" to R.string.settings_language_spanish,
            "ca" to R.string.settings_language_catalan,
            "fr" to R.string.settings_language_french,
        )
    }
}
