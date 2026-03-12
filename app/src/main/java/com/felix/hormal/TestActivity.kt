package com.felix.hormal

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.felix.hormal.audio.Ear
import com.felix.hormal.audio.ToneGenerator
import com.felix.hormal.databinding.ActivityTestBinding
import com.felix.hormal.model.FREQUENCIES

class TestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestBinding
    private val toneGen = ToneGenerator()
    private val handler = Handler(Looper.getMainLooper())

    // Privacy mode: when true, frequency and dB info are hidden during the test
    private var hideTestInfo = true

    // State
    private var freqIndex = 0         // current index into FREQUENCIES
    private var testingLeftEar = true
    private var currentDb = 40
    private var correctResponses = 0
    private val leftThresholds = IntArray(FREQUENCIES.size) { 40 }
    private val rightThresholds = IntArray(FREQUENCIES.size) { 40 }
    private var testRunning = false
    private var waitingForResponse = false

    // Counter-check state: detect false clicks during silent intervals
    private var isCounterCheckActive = false
    private var falseClickCount = 0
    private var completedSinceLastCheck = 0

    // Measurement series: each entry is "ear,freq,dB,heard" (e.g. "L,250,40,1")
    private val measurements = ArrayList<String>()

    private val TONE_DURATION_MS = 1500L
    private val RESPONSE_TIMEOUT_MS = 3000L
    private val INTER_TONE_DELAY_MS = 500L

    /** Duration of the silent counter-check window (longer pause with no tone). */
    private val COUNTER_CHECK_DURATION_MS = 5000L

    /** Insert a silent counter-check after every this many completed frequencies. */
    private val CHECK_INTERVAL = 2

    /** Abort the test after this many false clicks during silent intervals. */
    private val MAX_FALSE_CLICKS = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnHeard.setOnClickListener {
            if (isCounterCheckActive) {
                onFalseClick()
            } else if (waitingForResponse) {
                onHeardResponse()
            }
        }

        binding.btnStartTest.setOnClickListener {
            hideTestInfo = binding.cbHideInfo.isChecked
            startTest()
        }
    }

    private fun startTest() {
        freqIndex = 0
        testingLeftEar = true
        currentDb = 40
        correctResponses = 0
        falseClickCount = 0
        completedSinceLastCheck = 0
        isCounterCheckActive = false
        testRunning = true
        leftThresholds.fill(40)
        rightThresholds.fill(40)
        measurements.clear()

        binding.btnStartTest.visibility = View.GONE
        binding.cbHideInfo.visibility = View.GONE
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

    /**
     * Called when the user presses the button during a silent counter-check interval.
     * This is a false click — no tone was playing.
     */
    private fun onFalseClick() {
        if (!testRunning) return
        handler.removeCallbacks(counterCheckTimeoutRunnable)
        isCounterCheckActive = false
        waitingForResponse = false
        falseClickCount++

        if (falseClickCount >= MAX_FALSE_CLICKS) {
            showTooManyFalseClicksDialog()
        } else {
            showFalseClickWarning()
        }
    }

    /**
     * Starts a silent counter-check: the button remains visible and active for
     * [COUNTER_CHECK_DURATION_MS] but no tone is played. A click during this
     * window is counted as a false response.
     */
    private fun playCounterCheck() {
        if (!testRunning) return
        isCounterCheckActive = true
        waitingForResponse = true
        handler.postDelayed(counterCheckTimeoutRunnable, COUNTER_CHECK_DURATION_MS)
    }

    private val counterCheckTimeoutRunnable = Runnable {
        if (isCounterCheckActive) {
            // No false click occurred — continue with the next tone
            isCounterCheckActive = false
            waitingForResponse = false
            playNextTone()
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
        completedSinceLastCheck++

        if (freqIndex >= FREQUENCIES.size) {
            if (testingLeftEar) {
                // Switch to right ear
                testingLeftEar = false
                freqIndex = 0
                completedSinceLastCheck = 0
                handler.postDelayed({ playNextTone() }, 1500)
            } else {
                // Test complete
                finishTest()
            }
        } else if (completedSinceLastCheck >= CHECK_INTERVAL) {
            // Insert a silent counter-check before the next frequency
            completedSinceLastCheck = 0
            handler.postDelayed({ playCounterCheck() }, 1500)
        } else {
            handler.postDelayed({ playNextTone() }, 1500)
        }
    }

    /** Shows a warning dialog after a false click; test resumes when user dismisses it. */
    private fun showFalseClickWarning() {
        val remaining = MAX_FALSE_CLICKS - falseClickCount
        val message = resources.getQuantityString(
            R.plurals.counter_check_warning_message, remaining, remaining
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.counter_check_warning_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                dialog.dismiss()
                handler.postDelayed({ playNextTone() }, INTER_TONE_DELAY_MS)
            }
            .setCancelable(false)
            .show()
    }

    /** Shows an abort dialog after too many false clicks; pressing OK exits the activity. */
    private fun showTooManyFalseClicksDialog() {
        testRunning = false
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.counter_check_abort_title))
            .setMessage(getString(R.string.counter_check_abort_message))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun finishTest() {
        testRunning = false
        waitingForResponse = false
        toneGen.stop()

        binding.btnHeard.visibility = View.GONE
        binding.tvStatus.text = getString(R.string.test_complete_with_jingle)

        playEndJingle {
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra("LEFT_THRESHOLDS", leftThresholds)
                putExtra("RIGHT_THRESHOLDS", rightThresholds)
                putStringArrayListExtra("MEASUREMENTS", measurements)
            }
            startActivity(intent)
            finish()
        }
    }

    /**
     * Plays a short ascending three-note jingle on both ears to signal that the
     * test has finished, then invokes [onFinished] after all notes have played.
     */
    private fun playEndJingle(onFinished: () -> Unit) {
        // Three ascending tones: 800 Hz → 1000 Hz → 1200 Hz
        val notes = listOf(
            Triple(800, 60, 250),
            Triple(1000, 60, 250),
            Triple(1200, 60, 400)
        )
        var delay = 0L
        for ((freq, db, duration) in notes) {
            handler.postDelayed({
                toneGen.playTone(freq, db, duration, Ear.BOTH)
            }, delay)
            delay += (duration + 100).toLong()
        }
        handler.postDelayed({ onFinished() }, delay)
    }

    private fun updateStatus() {
        if (hideTestInfo) {
            binding.tvStatus.text = getString(R.string.test_info_hidden)
            binding.tvProgress.text = ""
        } else {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        toneGen.stop()
    }
}
