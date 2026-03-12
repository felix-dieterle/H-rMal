package com.felix.hormal

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.felix.hormal.audio.Ear
import com.felix.hormal.audio.ToneGenerator
import com.felix.hormal.databinding.ActivityTestBinding
import com.felix.hormal.model.FREQUENCIES

class TestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestBinding
    private val toneGen = ToneGenerator()
    private val handler = Handler(Looper.getMainLooper())

    // State
    private var freqIndex = 0         // current index into FREQUENCIES
    private var testingLeftEar = true
    private var currentDb = 40
    private var correctResponses = 0
    private val leftThresholds = IntArray(FREQUENCIES.size) { 40 }
    private val rightThresholds = IntArray(FREQUENCIES.size) { 40 }
    private var testRunning = false
    private var waitingForResponse = false

    // Measurement series: each entry is "ear,freq,dB,heard" (e.g. "L,250,40,1")
    private val measurements = ArrayList<String>()

    private val TONE_DURATION_MS = 1500L
    private val RESPONSE_TIMEOUT_MS = 3000L
    private val INTER_TONE_DELAY_MS = 500L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnHeard.setOnClickListener {
            if (waitingForResponse) {
                onHeardResponse()
            }
        }

        binding.btnStartTest.setOnClickListener {
            startTest()
        }
    }

    private fun startTest() {
        freqIndex = 0
        testingLeftEar = true
        currentDb = 40
        correctResponses = 0
        testRunning = true
        leftThresholds.fill(40)
        rightThresholds.fill(40)
        measurements.clear()

        binding.btnStartTest.visibility = View.GONE
        binding.btnHeard.visibility = View.VISIBLE
        binding.tvInstructions.text = getString(R.string.test_instructions)

        updateStatus()
        handler.postDelayed({ playNextTone() }, 1000)
    }

    private fun playNextTone() {
        if (!testRunning) return
        waitingForResponse = false
        toneGen.stop()

        val freq = FREQUENCIES[freqIndex]
        val ear = if (testingLeftEar) Ear.LEFT else Ear.RIGHT

        updateStatus()

        toneGen.playTone(freq, currentDb, TONE_DURATION_MS.toInt(), ear)
        waitingForResponse = true

        // Schedule no-response timeout
        handler.postDelayed(noResponseRunnable, RESPONSE_TIMEOUT_MS)
    }

    private val noResponseRunnable = Runnable {
        if (waitingForResponse) {
            onNoResponse()
        }
    }

    private fun onHeardResponse() {
        handler.removeCallbacks(noResponseRunnable)
        waitingForResponse = false
        toneGen.stop()

        val earCode = if (testingLeftEar) "L" else "R"
        measurements.add("$earCode,${FREQUENCIES[freqIndex]},$currentDb,1")

        correctResponses++

        val nextDb = (currentDb - 10).coerceAtLeast(-10)
        if (nextDb == currentDb) {
            // Already at the minimum level (-10 dB HL); record threshold here
            recordThreshold()
        } else {
            // Keep descending to find the true minimum hearing threshold
            currentDb = nextDb
            handler.postDelayed({ playNextTone() }, INTER_TONE_DELAY_MS)
        }
    }

    private fun onNoResponse() {
        waitingForResponse = false
        toneGen.stop()

        val earCode = if (testingLeftEar) "L" else "R"
        measurements.add("$earCode,${FREQUENCIES[freqIndex]},$currentDb,0")

        val hadPriorResponse = correctResponses > 0
        correctResponses = 0

        when {
            hadPriorResponse -> {
                // User heard at the level above (currentDb + 10) but not at this lower level.
                // Restore to the confirmed level and record it as the threshold.
                currentDb = (currentDb + 10).coerceAtMost(90)
                recordThreshold()
            }
            currentDb >= 90 -> {
                // No hearing found at max level; mark as 99 (no response)
                recordThreshold(noResponse = true)
            }
            else -> {
                currentDb = (currentDb + 10).coerceAtMost(90)
                handler.postDelayed({ playNextTone() }, INTER_TONE_DELAY_MS)
            }
        }
    }

    private fun recordThreshold(noResponse: Boolean = false) {
        val threshold = if (noResponse) 99 else currentDb
        if (testingLeftEar) {
            leftThresholds[freqIndex] = threshold
        } else {
            rightThresholds[freqIndex] = threshold
        }

        moveToNext()
    }

    private fun moveToNext() {
        freqIndex++
        correctResponses = 0
        currentDb = 40

        if (freqIndex >= FREQUENCIES.size) {
            if (testingLeftEar) {
                // Switch to right ear
                testingLeftEar = false
                freqIndex = 0
                handler.postDelayed({ playNextTone() }, 1500)
            } else {
                // Test complete
                finishTest()
            }
        } else {
            handler.postDelayed({ playNextTone() }, 1500)
        }
    }

    private fun finishTest() {
        testRunning = false
        waitingForResponse = false
        toneGen.stop()

        binding.btnHeard.visibility = View.GONE
        binding.tvStatus.text = getString(R.string.test_complete)

        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra("LEFT_THRESHOLDS", leftThresholds)
            putExtra("RIGHT_THRESHOLDS", rightThresholds)
            putStringArrayListExtra("MEASUREMENTS", measurements)
        }
        startActivity(intent)
        finish()
    }

    private fun updateStatus() {
        val freq = FREQUENCIES[freqIndex]
        val earLabel = if (testingLeftEar) getString(R.string.left_ear) else getString(R.string.right_ear)
        binding.tvStatus.text = getString(R.string.test_status, earLabel, freq, currentDb)
        binding.tvProgress.text = getString(
            R.string.test_progress,
            freqIndex + 1,
            FREQUENCIES.size,
            if (testingLeftEar) 1 else 2
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        toneGen.stop()
    }
}
