package com.idun.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.idun.app.data.CreditInfo
import com.idun.app.data.RecipeSource
import com.idun.app.data.creditInfo
import com.idun.app.databinding.ActivityCreditsBinding

/**
 * Credits page — surfaces the people behind each recipe set Idun ships
 * (Bryan Johnson / Blueprint, Valter Longo / Longevity Diet). Names are
 * attributed by name here (nominative fair use) even though the set *labels*
 * are de-branded; see docs/COMMERCIAL-CLEARANCE.md.
 *
 * Equal-weight per the locked design constraints: one identical card per
 * recipe set that has a creditable figure, none leads. Cards are built from
 * the enum so adding a set never touches this layout — only [creditInfo]
 * grows. Sets organized purely by regime may have no figure and contribute
 * no card (docs/RECIPE-SET-AXIS.md).
 */
class CreditsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreditsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreditsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // One card per recipe set that has a creditable figure. Sets organized
        // purely by regime (no headline author) contribute none — see
        // docs/RECIPE-SET-AXIS.md and RecipeSource.creditInfo().
        val inflater = LayoutInflater.from(this)
        for (source in RecipeSource.values()) {
            val info = source.creditInfo() ?: continue
            binding.creditCards.addView(buildCard(inflater, info))
        }
    }

    private fun buildCard(inflater: LayoutInflater, info: CreditInfo): android.view.View {
        val card = inflater.inflate(R.layout.item_credit_card, binding.creditCards, false)
        card.findViewById<TextView>(R.id.credit_name).setText(info.nameRes)
        card.findViewById<TextView>(R.id.credit_role).setText(info.roleRes)
        card.findViewById<TextView>(R.id.credit_bio).setText(info.bioRes)

        val links = card.findViewById<LinearLayout>(R.id.credit_links)
        info.links.forEachIndexed { index, link ->
            // First link is the primary CTA (tonal); the rest are outlined.
            val layout = if (index == 0) {
                R.layout.item_credit_link_primary
            } else {
                R.layout.item_credit_link_secondary
            }
            val button = inflater.inflate(layout, links, false) as MaterialButton
            button.setText(link.labelRes)
            button.setOnClickListener { open(link.url) }
            links.addView(button)
        }
        return card
    }

    private fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.credits_no_browser, Toast.LENGTH_SHORT).show()
        }
    }
}
