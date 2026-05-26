package com.idun.app.ui

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.idun.app.LearnActivity
import com.idun.app.MainActivity
import com.idun.app.PlanningActivity
import com.idun.app.R
import com.idun.app.ShoppingListActivity
import java.time.LocalDate

/**
 * Wire the bottom-nav bar on a top-level activity. The three peer destinations
 * are Recipes / Plan / Shopping. Reuses existing instances via
 * `FLAG_ACTIVITY_REORDER_TO_FRONT | SINGLE_TOP` so tab switches don't pile up
 * the back stack with duplicates.
 *
 * The Shopping tab opens a 7-day plan-window aggregate, which is the most
 * common "what should I buy this week" intent. The Recipes-screen FAB still
 * covers the ad-hoc multi-select path.
 */
fun Activity.setupBottomNav(bottomNav: BottomNavigationView, currentItemId: Int) {
    bottomNav.selectedItemId = currentItemId
    bottomNav.setOnItemSelectedListener { item ->
        if (item.itemId == currentItemId) return@setOnItemSelectedListener true
        val intent = when (item.itemId) {
            R.id.nav_recipes -> Intent(this, MainActivity::class.java)
            R.id.nav_plan -> Intent(this, PlanningActivity::class.java)
            R.id.nav_shopping -> shoppingListIntent()
            R.id.nav_learn -> Intent(this, LearnActivity::class.java)
            else -> return@setOnItemSelectedListener false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        true
    }
    bottomNav.setOnItemReselectedListener { /* no-op: stay put */ }
}

private fun Activity.shoppingListIntent(): Intent {
    val today = LocalDate.now()
    return Intent(this, ShoppingListActivity::class.java)
        .putExtra(ShoppingListActivity.EXTRA_FROM_ISO, today.toString())
        .putExtra(ShoppingListActivity.EXTRA_TO_ISO, today.plusDays(6L).toString())
}
