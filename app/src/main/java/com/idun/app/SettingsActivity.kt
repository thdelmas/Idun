package com.idun.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.idun.app.bios.BiosClient

/**
 * Settings screen — Bios integration toggle is the only non-trivial control
 * for v1. Mirrors Smokeless's settings pattern.
 *
 * Scaffold-only.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var biosClient: BiosClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        biosClient = BiosClient(this)
        // TODO: render Bios integration toggle + status (NOT_INSTALLED / NOT_ENABLED / CONNECTED)
        // TODO: surface lastPushOutcome — particularly PENDING_APPROVAL with deep-link
        //       into Bios → Settings → Companion Apps.
    }
}
