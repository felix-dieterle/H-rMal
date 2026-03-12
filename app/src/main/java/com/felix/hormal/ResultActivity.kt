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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        leftThresholds = intent.getIntArrayExtra("LEFT_THRESHOLDS") ?: IntArray(6) { 40 }
        rightThresholds = intent.getIntArrayExtra("RIGHT_THRESHOLDS") ?: IntArray(6) { 40 }
        readOnly = intent.getBooleanExtra("READ_ONLY", false)

        val resultName = intent.getStringExtra("RESULT_NAME")
        val resultAgeGroupName = intent.getStringExtra("RESULT_AGE_GROUP")

        if (readOnly) {
            binding.saveSection.visibility = View.GONE
            supportActionBar?.title = resultName ?: getString(R.string.results_title)
        }

        setupAgeSpinner(resultAgeGroupName)
        setupChart()
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
                rightEar = HearingResult.encodeArray(rightThresholds)
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
