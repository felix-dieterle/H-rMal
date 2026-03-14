package com.felix.hormal

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.felix.hormal.audio.Ear
import com.felix.hormal.audio.ToneGenerator
import com.felix.hormal.databinding.ActivityTestBinding
import com.felix.hormal.model.FREQUENCIES

class TestActivity : AppCompatActivity(), VolumeWarningDialogFragment.Listener {

    private lateinit var binding: ActivityTestBinding
    private val toneGen = ToneGenerator()
    private val handler = Handler(Looper.getMainLooper())

    // Privacy mode: when true, frequency and dB info are hidden during the test
    private var hideTestInfo = true

    // Shortened test: only the three most clinically relevant frequencies
    private var isShortVersion = false
    private val shortFreqIndices = intArrayOf(1, 3, 4) // 500 Hz, 2000 Hz, 4000 Hz

    /** Active frequency indices into [FREQUENCIES] for the current test session. */
    private lateinit var activeFreqIndices: IntArray

    // State
    private var freqIndex = 0         // current index into activeFreqIndices
    private var testingLeftEar = true
    private var currentDb = 40
    private var correctResponses = 0
    private val leftThresholds = IntArray(FREQUENCIES.size) { 40 }
    private val rightThresholds = IntArray(FREQUENCIES.size) { 40 }
    private var testRunning = false
    private var waitingForResponse = false

    // Confirmation-check state: re-test the threshold with a random pause first
    private var isConfirmationActive = false   // true while confirmation tone is playing
    private var pendingConfirmationDb = 0      // dB level to re-verify

    // Counter-check state: detect false clicks during silent intervals
    private var isCounterCheckActive = false
    private var isPreConfirmationPause = false  // true during the silent pause before the confirmation tone
    private var isInterToneDelay = false        // true while waiting between tones (no tone playing)
    private var falseClickCount = 0
    private var completedSinceLastCheck = 0

    // Child motivation counters
    private var motivationCorrectCount = 0

    // Measurement series: each entry is "ear,freq,dB,heard" (e.g. "L,250,40,1")
    private val measurements = ArrayList<String>()

    private val TONE_DURATION_MS = 1500L
    private val RESPONSE_TIMEOUT_MS = 3000L

    /** Insert a silent counter-check after every this many completed frequencies. */
    private val CHECK_INTERVAL = 1

    /** Returns a random inter-tone delay in ms (1 000–4 000 ms). */
    private fun randomInterToneDelayMs(): Long = (1000L..4000L).random()

    /** Returns a random silent counter-check duration in ms (3 000–9 000 ms). */
    private fun randomCounterCheckDurationMs(): Long = (3000L..9000L).random()

    /** Abort the test after this many false clicks during silent intervals. */
    private val MAX_FALSE_CLICKS = 3

    /** Maximum number of ⭐ icons shown in the counter row; overflow shows '+'. */
    private val MAX_VISIBLE_STARS = 10

    /** Estimated seconds per frequency step for the countdown display. */
    private val ESTIMATED_SECONDS_PER_FREQUENCY = 15

    /** Step size for the staircase algorithm in dB HL. */
    private val STEP_DB = 10

    /** Maximum measurable level in dB HL. */
    private val MAX_DB = 90

    // ---------------------------------------------------------------------------
    // Helpers to resolve the active frequency and its storage index
    // ---------------------------------------------------------------------------

    /** The actual frequency in Hz for the current test step. */
    private val currentFreqHz: Int get() = FREQUENCIES[activeFreqIndices[freqIndex]]

    /** Index into the threshold arrays for the current test step. */
    private val currentFreqStoreIndex: Int get() = activeFreqIndices[freqIndex]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnHeard.setOnClickListener {
            if (isCounterCheckActive) {
                onFalseClick()
            } else if (isPreConfirmationPause) {
                onFalseClickDuringPause()
            } else if (waitingForResponse) {
                if (isConfirmationActive) {
                    onConfirmationHeard()
                } else {
                    onHeardResponse()
                }
            } else if (testRunning && isInterToneDelay) {
                onFalseClickDuringInterToneDelay()
            }
        }

        binding.btnStartTest.setOnClickListener {
            hideTestInfo = binding.cbHideInfo.isChecked
            isShortVersion = binding.cbShortVersion.isChecked
            checkVolumeAndStart()
        }

        binding.btnDbInfo.setOnClickListener {
            showDbCalibrationInfo()
        }
    }

    /** Shows a dialog explaining how the dB HL values are scaled and what affects accuracy. */
    private fun showDbCalibrationInfo() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.db_info_title))
            .setMessage(getString(R.string.db_info_message))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    /**
     * Checks whether the device media volume is at maximum before starting the test.
     * If not, the user is prompted to set it to the maximum (for consistent results)
     * or to continue anyway.  This directly addresses the fairness concern raised in
     * the issue: a lower-than-maximum device volume artificially shifts perceived
     * loudness and would skew the measured hearing thresholds.
     */
    private fun checkVolumeAndStart() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            startTest()
            return
        }
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        if (currentVolume < maxVolume) {
            VolumeWarningDialogFragment.newInstance(currentVolume, maxVolume)
                .show(supportFragmentManager, VolumeWarningDialogFragment.TAG)
        } else {
            startTest()
        }
    }

    // VolumeWarningDialogFragment.Listener
    override fun onSetMax(maxVolume: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
        startTest()
    }

    override fun onContinueAnyway() {
        startTest()
    }

    private fun startTest() {
        activeFreqIndices = if (isShortVersion) shortFreqIndices else IntArray(FREQUENCIES.size) { it }

        freqIndex = 0
        testingLeftEar = true
        currentDb = 40
        correctResponses = 0
        falseClickCount = 0
        motivationCorrectCount = 0
        completedSinceLastCheck = 0
        isCounterCheckActive = false
        isPreConfirmationPause = false
        isInterToneDelay = false
        interToneAction = null
        isConfirmationActive = false
        pendingConfirmationDb = 0
        testRunning = true

        // Initialise all thresholds; mark frequencies skipped in short mode with -1
        leftThresholds.fill(40)
        rightThresholds.fill(40)
        if (isShortVersion) {
            for (i in FREQUENCIES.indices) {
                if (i !in shortFreqIndices) {
                    leftThresholds[i] = -1
                    rightThresholds[i] = -1
                }
            }
        }

        measurements.clear()

        binding.btnStartTest.visibility = View.GONE
        binding.cbHideInfo.visibility = View.GONE
        binding.cbShortVersion.visibility = View.GONE
        binding.btnDbInfo.visibility = View.GONE
        binding.btnHeard.visibility = View.VISIBLE
        binding.layoutCounters.visibility = View.VISIBLE
        binding.tvCountdown.visibility = View.VISIBLE
        binding.liveAudiogram.visibility = View.VISIBLE
        binding.tvAudiogramLegend.visibility = View.VISIBLE
        binding.liveAudiogram.reset()
        binding.stickFigure.setCorrectCount(0)
        binding.tvInstructions.text = getString(R.string.test_instructions)

        updateCounters()
        updateCountdown()
        updateStatus()
        scheduleNextTone(1000L)
    }

    private fun playNextTone() {
        if (!testRunning) return
        waitingForResponse = false
        toneGen.stop()

        val ear = if (testingLeftEar) Ear.LEFT else Ear.RIGHT

        updateStatus()
        updateCountdown()

        toneGen.playTone(currentFreqHz, currentDb, TONE_DURATION_MS.toInt(), ear)
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
        measurements.add("$earCode,$currentFreqHz,$currentDb,1")

        correctResponses++
        motivationCorrectCount++
        showFeedback(positive = true)
        updateCounters()

        val nextDb = (currentDb - 10).coerceAtLeast(-10)
        if (nextDb == currentDb) {
            // Already at the minimum level (-10 dB HL); record threshold here
            recordThreshold()
        } else {
            // Keep descending to find the true minimum hearing threshold
            currentDb = nextDb
            scheduleNextTone(randomInterToneDelayMs())
        }
    }

    private fun onNoResponse() {
        waitingForResponse = false
        toneGen.stop()

        val earCode = if (testingLeftEar) "L" else "R"
        measurements.add("$earCode,$currentFreqHz,$currentDb,0")

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
                scheduleNextTone(randomInterToneDelayMs())
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

        showFeedback(positive = false)
        updateCounters()

        if (falseClickCount >= MAX_FALSE_CLICKS) {
            showTooManyFalseClicksDialog()
        } else {
            showFalseClickWarning { scheduleNextTone(randomInterToneDelayMs()) }
        }
    }

    /**
     * Called when the user presses the button during the silent pre-confirmation pause
     * (the random delay before the confirmation tone is played).
     * The pending confirmation is cancelled and the test advances to the next frequency.
     */
    private fun onFalseClickDuringPause() {
        if (!testRunning) return
        handler.removeCallbacks(confirmationPrePauseRunnable)
        isPreConfirmationPause = false
        falseClickCount++

        showFeedback(positive = false)
        updateCounters()

        if (falseClickCount >= MAX_FALSE_CLICKS) {
            showTooManyFalseClicksDialog()
        } else {
            showFalseClickWarning { moveToNext() }
        }
    }

    /**
     * Called when the user presses the button during the inter-tone delay (the silent
     * pause between tones when no tone is playing and no counter-check is active).
     * Rapidly clicking at regular intervals during these gaps is treated as cheating.
     */
    private fun onFalseClickDuringInterToneDelay() {
        if (!testRunning) return
        handler.removeCallbacks(interToneRunnable)
        isInterToneDelay = false
        interToneAction = null
        falseClickCount++

        showFeedback(positive = false)
        updateCounters()

        if (falseClickCount >= MAX_FALSE_CLICKS) {
            showTooManyFalseClicksDialog()
        } else {
            showFalseClickWarning { scheduleNextTone(randomInterToneDelayMs()) }
        }
    }

    // ---------------------------------------------------------------------------
    // Inter-tone delay scheduling helpers
    // ---------------------------------------------------------------------------

    /** The action to invoke when the inter-tone delay expires. */
    private var interToneAction: (() -> Unit)? = null

    private val interToneRunnable = Runnable {
        isInterToneDelay = false
        interToneAction?.invoke()
        interToneAction = null
    }

    /**
     * Schedules [playNextTone] after [delayMs] ms, setting [isInterToneDelay] = true
     * so that button clicks during the pause are detected as false clicks.
     */
    private fun scheduleNextTone(delayMs: Long) {
        isInterToneDelay = true
        interToneAction = ::playNextTone
        handler.postDelayed(interToneRunnable, delayMs)
    }

    /**
     * Schedules [playCounterCheck] after [delayMs] ms, setting [isInterToneDelay] = true
     * so that button clicks during the pause are detected as false clicks.
     */
    private fun scheduleCounterCheck(delayMs: Long) {
        isInterToneDelay = true
        interToneAction = ::playCounterCheck
        handler.postDelayed(interToneRunnable, delayMs)
    }

    /**
     * Starts a silent counter-check: the button remains visible and active for
     * a random duration (see [randomCounterCheckDurationMs]) but no tone is played.
     * A click during this window is counted as a false response.
     */
    private fun playCounterCheck() {
        if (!testRunning) return
        isCounterCheckActive = true
        waitingForResponse = true
        handler.postDelayed(counterCheckTimeoutRunnable, randomCounterCheckDurationMs())
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
            leftThresholds[currentFreqStoreIndex] = threshold
        } else {
            rightThresholds[currentFreqStoreIndex] = threshold
        }

        // Update the live audiogram with the newly recorded threshold
        binding.liveAudiogram.setThreshold(currentFreqStoreIndex, threshold, testingLeftEar)

        if (!noResponse) {
            // Re-verify the boundary threshold: play the tone once more after a
            // random pause to ensure the first response was genuine.
            pendingConfirmationDb = threshold
            scheduleConfirmationCheck()
        } else {
            moveToNext()
        }
    }

    /**
     * Schedules a boundary re-verification: a random silent pause (2–4 s) followed
     * by playing the tone at [pendingConfirmationDb] once more.  Button clicks during
     * the pause are now detected as false clicks via [isPreConfirmationPause], which
     * also prevents a reflex press from inadvertently confirming the threshold.
     */
    private fun scheduleConfirmationCheck() {
        waitingForResponse = false
        isCounterCheckActive = false
        isPreConfirmationPause = true
        val pauseMs = (2000L..4000L).random()
        handler.postDelayed(confirmationPrePauseRunnable, pauseMs)
    }

    private val confirmationPrePauseRunnable = Runnable {
        isPreConfirmationPause = false
        if (testRunning) playConfirmationTone()
    }

    private fun playConfirmationTone() {
        if (!testRunning) return
        isConfirmationActive = true
        waitingForResponse = true
        val ear = if (testingLeftEar) Ear.LEFT else Ear.RIGHT
        updateStatus()
        toneGen.playTone(currentFreqHz, pendingConfirmationDb, TONE_DURATION_MS.toInt(), ear)
        handler.postDelayed(confirmationNoResponseRunnable, RESPONSE_TIMEOUT_MS)
    }

    private val confirmationNoResponseRunnable = Runnable {
        if (waitingForResponse && isConfirmationActive) {
            onConfirmationNoResponse()
        }
    }

    /** User confirmed the threshold during the re-verification tone. */
    private fun onConfirmationHeard() {
        handler.removeCallbacks(confirmationNoResponseRunnable)
        waitingForResponse = false
        isConfirmationActive = false
        toneGen.stop()
        val earCode = if (testingLeftEar) "L" else "R"
        measurements.add("$earCode,$currentFreqHz,$pendingConfirmationDb,1")

        motivationCorrectCount++
        showFeedback(positive = true)
        updateCounters()

        // Threshold confirmed – keep the previously stored value
        moveToNext()
    }

    /**
     * User did not respond during the re-verification tone.  Raise the stored
     * threshold by one step (10 dB) to reflect that the first response was not
     * reproducible.
     */
    private fun onConfirmationNoResponse() {
        waitingForResponse = false
        isConfirmationActive = false
        toneGen.stop()
        val earCode = if (testingLeftEar) "L" else "R"
        measurements.add("$earCode,$currentFreqHz,$pendingConfirmationDb,0")
        // Threshold not confirmed → raise by one step
        val adjusted = (pendingConfirmationDb + STEP_DB).coerceAtMost(MAX_DB)
        if (testingLeftEar) leftThresholds[currentFreqStoreIndex] = adjusted
        else rightThresholds[currentFreqStoreIndex] = adjusted
        moveToNext()
    }

    private fun moveToNext() {
        freqIndex++
        correctResponses = 0
        currentDb = 40
        completedSinceLastCheck++

        if (freqIndex >= activeFreqIndices.size) {
            if (testingLeftEar) {
                // Switch to right ear
                testingLeftEar = false
                freqIndex = 0
                completedSinceLastCheck = 0
                scheduleNextTone(randomInterToneDelayMs())
            } else {
                // Test complete
                finishTest()
            }
        } else if (completedSinceLastCheck >= CHECK_INTERVAL) {
            // Insert a silent counter-check before the next frequency
            completedSinceLastCheck = 0
            scheduleCounterCheck(randomInterToneDelayMs())
        } else {
            scheduleNextTone(randomInterToneDelayMs())
        }
    }

    /** Shows a warning dialog after a false click; [afterDismiss] is invoked when the user confirms. */
    private fun showFalseClickWarning(afterDismiss: () -> Unit) {
        val remaining = MAX_FALSE_CLICKS - falseClickCount
        val message = resources.getQuantityString(
            R.plurals.counter_check_warning_message, remaining, remaining
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.counter_check_warning_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                dialog.dismiss()
                afterDismiss()
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
        binding.layoutCounters.visibility = View.GONE
        binding.tvCountdown.visibility = View.GONE
        binding.liveAudiogram.visibility = View.GONE
        binding.tvAudiogramLegend.visibility = View.GONE
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
            val earLabel = if (testingLeftEar) getString(R.string.left_ear) else getString(R.string.right_ear)
            binding.tvStatus.text = getString(R.string.test_status, earLabel, currentFreqHz, currentDb)
            binding.tvProgress.text = getString(
                R.string.test_progress,
                freqIndex + 1,
                activeFreqIndices.size,
                if (testingLeftEar) 1 else 2
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Child-motivation UI helpers
    // ---------------------------------------------------------------------------

    /**
     * Briefly shows a large emoji feedback label — green star for a correct
     * response, red cross for a false click.  The label auto-hides after 1.5 s.
     */
    private fun showFeedback(positive: Boolean) {
        binding.tvFeedback.text =
            if (positive) getString(R.string.feedback_correct) else getString(R.string.feedback_false_click)
        binding.tvFeedback.setTextColor(
            ContextCompat.getColor(
                this,
                if (positive) R.color.feedback_correct_text else R.color.feedback_incorrect_text
            )
        )
        binding.tvFeedback.visibility = View.VISIBLE
        handler.removeCallbacks(clearFeedbackRunnable)
        handler.postDelayed(clearFeedbackRunnable, 1500)
    }

    private val clearFeedbackRunnable = Runnable {
        binding.tvFeedback.visibility = View.INVISIBLE
    }

    /** Updates the ⭐/❌ counters and the stick figure shown to the child during the test. */
    private fun updateCounters() {
        val stars = if (motivationCorrectCount <= MAX_VISIBLE_STARS) {
            "⭐".repeat(motivationCorrectCount)
        } else {
            "⭐".repeat(MAX_VISIBLE_STARS) + "+"
        }
        binding.tvCorrectCount.text = stars
        binding.tvIncorrectCount.text = "❌".repeat(falseClickCount)
        binding.stickFigure.setCorrectCount(motivationCorrectCount)
    }

    /**
     * Updates the approximate time-remaining label.
     * Assumes [ESTIMATED_SECONDS_PER_FREQUENCY] seconds per remaining frequency step
     * (tone + confirmation + pauses). Rounds up so the display never shows 0 min.
     */
    private fun updateCountdown() {
        val remainingThisEar = activeFreqIndices.size - freqIndex
        val remainingTotal = remainingThisEar + if (testingLeftEar) activeFreqIndices.size else 0
        val remainingSeconds = remainingTotal * ESTIMATED_SECONDS_PER_FREQUENCY
        // Round up: (seconds + 59) / 60 ensures any partial minute shows as a full minute
        val remainingMinutes = maxOf(1, (remainingSeconds + 59) / 60)
        binding.tvCountdown.text = getString(R.string.countdown_label, remainingMinutes)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        toneGen.stop()
    }
}

/**
 * DialogFragment that prompts the user to raise the device media volume to maximum
 * before starting the hearing test, ensuring comparable results across sessions.
 *
 * Using a DialogFragment instead of a plain AlertDialog prevents window-leaked-dialog
 * crashes when the activity is destroyed while the dialog is still visible (e.g. on
 * screen rotation).
 */
class VolumeWarningDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "VolumeWarningDialog"
        private const val ARG_CURRENT = "currentVolume"
        private const val ARG_MAX = "maxVolume"

        fun newInstance(currentVolume: Int, maxVolume: Int) = VolumeWarningDialogFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_CURRENT, currentVolume)
                putInt(ARG_MAX, maxVolume)
            }
        }
    }

    /** Implemented by the host activity to react to the user's choice. */
    interface Listener {
        fun onSetMax(maxVolume: Int)
        fun onContinueAnyway()
    }

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        check(context is Listener) {
            "${context.javaClass.simpleName} must implement VolumeWarningDialogFragment.Listener"
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val current = requireArguments().getInt(ARG_CURRENT)
        val max = requireArguments().getInt(ARG_MAX)
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.volume_warning_title)
            .setMessage(getString(R.string.volume_warning_message, current, max))
            .setPositiveButton(R.string.volume_set_max) { _, _ ->
                (activity as? Listener)?.onSetMax(max)
            }
            .setNegativeButton(R.string.volume_continue_anyway) { _, _ ->
                (activity as? Listener)?.onContinueAnyway()
            }
            .setCancelable(false)
            .create()
    }
}
