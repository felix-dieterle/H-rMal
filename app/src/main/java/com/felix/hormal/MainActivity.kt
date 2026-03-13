package com.felix.hormal

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.felix.hormal.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UpdateChecker.checkForUpdate(this)

        binding.btnStartTest.setOnClickListener {
            showDisclaimerAndStart()
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun showDisclaimerAndStart() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.disclaimer_title))
            .setMessage(getString(R.string.disclaimer_message))
            .setPositiveButton(getString(R.string.disclaimer_accept)) { _, _ ->
                startActivity(Intent(this, TestActivity::class.java))
            }
            .setNegativeButton(getString(R.string.disclaimer_decline)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
}
