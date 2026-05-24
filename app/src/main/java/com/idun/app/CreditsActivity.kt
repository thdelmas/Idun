package com.idun.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.idun.app.databinding.ActivityCreditsBinding

/**
 * Credits page — surfaces Bryan Johnson (Blueprint) and Valter Longo
 * (Longevity Diet) as the sources behind the two recipe sets Idun ships.
 *
 * Equal-weight per the locked design constraints: same card treatment for
 * both, neither leads. Per-card primary CTA points at the work Idun draws
 * from directly (Blueprint protocol for Johnson, valterlongo.com for Longo).
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

        binding.johnsonLinkProtocol.setOnClickListener { open(URL_JOHNSON_PROTOCOL) }
        binding.johnsonLinkWebsite.setOnClickListener { open(URL_JOHNSON_WEBSITE) }
        binding.johnsonLinkYoutube.setOnClickListener { open(URL_JOHNSON_YOUTUBE) }
        binding.johnsonLinkX.setOnClickListener { open(URL_JOHNSON_X) }

        binding.longoLinkWebsite.setOnClickListener { open(URL_LONGO_WEBSITE) }
        binding.longoLinkFoundation.setOnClickListener { open(URL_LONGO_FOUNDATION) }
        binding.longoLinkInstitute.setOnClickListener { open(URL_LONGO_INSTITUTE) }
    }

    private fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.credits_no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val URL_JOHNSON_PROTOCOL = "https://blueprint.bryanjohnson.com/"
        const val URL_JOHNSON_WEBSITE = "https://www.bryanjohnson.com/"
        const val URL_JOHNSON_YOUTUBE = "https://www.youtube.com/@BryanJohnson"
        const val URL_JOHNSON_X = "https://x.com/bryan_johnson"

        const val URL_LONGO_WEBSITE = "https://www.valterlongo.com/"
        const val URL_LONGO_FOUNDATION = "https://www.createcuresfoundation.org/"
        const val URL_LONGO_INSTITUTE = "https://gero.usc.edu/longevityinstitute/"
    }
}
