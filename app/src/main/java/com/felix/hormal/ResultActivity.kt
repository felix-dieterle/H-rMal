package com.felix.hormal

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.felix.hormal.data.AppDatabase
import com.felix.hormal.data.HearingResult
import com.felix.hormal.databinding.ActivityResultBinding
import com.felix.hormal.model.*
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private var leftThresholds = IntArray(6) { 40 }
    private var rightThresholds = IntArray(6) { 40 }
    private var selectedAgeGroup = AgeGroup.YOUNG_ADULT_18_35
    private var readOnly = false
    private var measurements = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        leftThresholds = intent.getIntArrayExtra("LEFT_THRESHOLDS") ?: IntArray(6) { 40 }
        rightThresholds = intent.getIntArrayExtra("RIGHT_THRESHOLDS") ?: IntArray(6) { 40 }
        readOnly = intent.getBooleanExtra("READ_ONLY", false)
        measurements = intent.getStringArrayListExtra("MEASUREMENTS") ?: ArrayList()

        val resultName = intent.getStringExtra("RESULT_NAME")
        val resultAgeGroupName = intent.getStringExtra("RESULT_AGE_GROUP")

        if (readOnly) {
            binding.saveSection.visibility = View.GONE
            supportActionBar?.title = resultName ?: getString(R.string.results_title)
        }

        setupAgeSpinner(resultAgeGroupName)
        setupChart()
        showMeasurementStats()
        showRecommendation()
        if (!readOnly) {
            setupSaveButton()
            resultName?.let { binding.etName.setText(it) }
        }
    }

    private fun setupAgeSpinner(preselectedAgeGroupName: String?) {
        val ageGroups = AgeGroup.values()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ageGroups.map { it.label })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAgeGroup.adapter = adapter

        // Pre-select from saved result or default to Young Adults
        val preselectedIndex = if (preselectedAgeGroupName != null) {
            ageGroups.indexOfFirst { it.name == preselectedAgeGroupName }.takeIf { it >= 0 } ?: 2
        } else 2
        binding.spinnerAgeGroup.setSelection(preselectedIndex)
        selectedAgeGroup = ageGroups[preselectedIndex]

        binding.spinnerAgeGroup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedAgeGroup = ageGroups[pos]
                updateChart()
                showRecommendation()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupChart() {
        val chart = binding.audiogramChart

        chart.description.isEnabled = false
        chart.legend.isEnabled = true
        chart.setTouchEnabled(true)
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)

        // X-Axis: frequencies
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(true)
        xAxis.labelCount = FREQUENCIES.size
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt().coerceIn(0, FREQUENCIES.size - 1)
                val f = FREQUENCIES[idx]
                return if (f >= 1000) "${f / 1000}k" else "$f"
            }
        }
        xAxis.axisMinimum = -0.5f
        xAxis.axisMaximum = 5.5f

        // Y-Axis: dB HL (inverted — lower values = better hearing = at top)
        val yAxis = chart.axisLeft
        yAxis.axisMinimum = -10f
        yAxis.axisMaximum = 100f
        yAxis.isInverted = true
        yAxis.setDrawGridLines(true)
        yAxis.labelCount = 12
        chart.axisRight.isEnabled = false

        updateChart()
    }

    private fun updateChart() {
        val chart = binding.audiogramChart
        val thresholds = AGE_THRESHOLDS[selectedAgeGroup]!!

        // Left ear entries (blue circles)
        val leftEntries = leftThresholds.toList().mapIndexedNotNull { i, v ->
            if (v <= 90) Entry(i.toFloat(), v.toFloat()) else null
        }
        val leftDataSet = LineDataSet(leftEntries, getString(R.string.left_ear)).apply {
            color = ContextCompat.getColor(this@ResultActivity, android.R.color.holo_blue_dark)
            setCircleColor(ContextCompat.getColor(this@ResultActivity, android.R.color.holo_blue_dark))
            lineWidth = 2f
            circleRadius = 5f
            setDrawValues(true)
            valueTextSize = 10f
        }

        // Right ear entries (red)
        val rightEntries = rightThresholds.toList().mapIndexedNotNull { i, v ->
            if (v <= 90) Entry(i.toFloat(), v.toFloat()) else null
        }
        val rightDataSet = LineDataSet(rightEntries, getString(R.string.right_ear)).apply {
            color = ContextCompat.getColor(this@ResultActivity, android.R.color.holo_red_dark)
            setCircleColor(ContextCompat.getColor(this@ResultActivity, android.R.color.holo_red_dark))
            lineWidth = 2f
            circleRadius = 5f
            setDrawValues(true)
            valueTextSize = 10f
        }

        chart.data = LineData(leftDataSet, rightDataSet)

        // Threshold lines on Y axis
        val yAxis = chart.axisLeft
        yAxis.removeAllLimitLines()

        val vgAvg = thresholds.veryGood.average().toFloat()
        val avgAvg = thresholds.average.average().toFloat()
        val critAvg = thresholds.critical.average().toFloat()

        val vgLine = LimitLine(vgAvg, getString(R.string.threshold_very_good)).apply {
            lineWidth = 2f
            lineColor = ContextCompat.getColor(this@ResultActivity, android.R.color.holo_green_dark)
            textColor = ContextCompat.getColor(this@ResultActivity, android.R.color.holo_green_dark)
            enableDashedLine(10f, 5f, 0f)
            textSize = 10f
        }
        val avgLine = LimitLine(avgAvg, getString(R.string.threshold_average)).apply {
            lineWidth = 2f
            lineColor = ContextCompat.getColor(this@ResultActivity, android.R.color.holo_orange_dark)
            textColor = ContextCompat.getColor(this@ResultActivity, android.R.color.holo_orange_dark)
            enableDashedLine(10f, 5f, 0f)
            textSize = 10f
        }
        val critLine = LimitLine(critAvg, getString(R.string.threshold_critical)).apply {
            lineWidth = 2f
            lineColor = ContextCompat.getColor(this@ResultActivity, android.R.color.holo_red_light)
            textColor = ContextCompat.getColor(this@ResultActivity, android.R.color.holo_red_light)
            enableDashedLine(10f, 5f, 0f)
            textSize = 10f
        }

        yAxis.addLimitLine(vgLine)
        yAxis.addLimitLine(avgLine)
        yAxis.addLimitLine(critLine)
        yAxis.setDrawLimitLinesBehindData(true)

        chart.invalidate()
    }

    private fun showMeasurementStats() {
        if (measurements.isEmpty()) {
            binding.measurementStatsSection.visibility = View.GONE
            return
        }
        binding.measurementStatsSection.visibility = View.VISIBLE

        val total = measurements.size
        val heardCount = measurements.count { it.endsWith(",1") }
        val notHeardCount = total - heardCount
        val heardPct = if (total > 0) (heardCount * 100.0 / total).toInt() else 0

        val sb = StringBuilder()
        sb.append(getString(R.string.total_measurements, total)).append("\n")
        sb.append(getString(R.string.heard_rate, heardCount, heardPct)).append("\n")
        sb.append(getString(R.string.not_heard_rate, notHeardCount, 100 - heardPct)).append("\n\n")
        sb.append(getString(R.string.per_frequency_label)).append("\n")

        FREQUENCIES.forEach { freq ->
            val freqLabel = if (freq >= 1000) "${freq / 1000}k" else "$freq"
            val lEntries = measurements.filter { it.startsWith("L,$freq,") }
            val rEntries = measurements.filter { it.startsWith("R,$freq,") }
            val lHeard = lEntries.count { it.endsWith(",1") }
            val lNotHeard = lEntries.size - lHeard
            val rHeard = rEntries.count { it.endsWith(",1") }
            val rNotHeard = rEntries.size - rHeard
            sb.append(getString(R.string.freq_breakdown, freqLabel, lHeard, lNotHeard, rHeard, rNotHeard)).append("\n")
        }

        binding.tvMeasurementStats.text = sb.toString()
    }

    /**
     * Computes and displays a recommendation based on the measured thresholds
     * compared to the [selectedAgeGroup] norms.
     *
     * Classification logic:
     * - **Specialist recommended**: ≥ 2 frequencies where at least one ear has no
     *   response (threshold == 99) OR exceeds the critical norm for the age group.
     * - **Monitor**: ≥ 2 frequencies where the worse ear exceeds the average norm.
     * - **Normal**: Everything within the average range.
     */
    private fun showRecommendation() {
        val thresholds = AGE_THRESHOLDS[selectedAgeGroup]!!

        var aboveCritical = 0
        var aboveAverage = 0
        var noResponseCount = 0

        for (i in FREQUENCIES.indices) {
            val leftDb = leftThresholds[i]
            val rightDb = rightThresholds[i]

            if (leftDb == 99 || rightDb == 99) {
                noResponseCount++
            } else {
                val worstEar = maxOf(leftDb, rightDb)
                if (worstEar > thresholds.critical[i]) aboveCritical++
                else if (worstEar > thresholds.average[i]) aboveAverage++
            }
        }

        val text = when {
            noResponseCount >= 2 || aboveCritical >= 2 -> getString(R.string.recommendation_specialist)
            aboveCritical >= 1 || aboveAverage >= 2 -> getString(R.string.recommendation_monitor)
            else -> getString(R.string.recommendation_good)
        }
        binding.tvRecommendation.text = text
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            if (name.isEmpty()) {
                binding.etName.error = getString(R.string.name_required)
                return@setOnClickListener
            }

            val result = HearingResult(
                name = name,
                ageGroup = selectedAgeGroup.name,
                leftEar = HearingResult.encodeArray(leftThresholds),
                rightEar = HearingResult.encodeArray(rightThresholds),
                measurements = HearingResult.encodeMeasurements(measurements)
            )

            lifecycleScope.launch {
                AppDatabase.getInstance(applicationContext).hearingResultDao().insert(result)
                runOnUiThread {
                    Toast.makeText(this@ResultActivity, getString(R.string.result_saved), Toast.LENGTH_SHORT).show()
                    binding.btnSave.isEnabled = false
                    binding.btnSave.text = getString(R.string.saved)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
