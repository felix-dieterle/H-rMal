package com.felix.hormal

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.felix.hormal.data.AppDatabase
import com.felix.hormal.data.HearingResult
import com.felix.hormal.databinding.ActivityHistoryBinding
import com.felix.hormal.databinding.ItemResultBinding
import com.felix.hormal.model.AgeGroup
import com.felix.hormal.model.calculateHearingScore
import com.felix.hormal.model.resolveAgeGroup
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    /** When true the list is sorted by score descending (Ranking mode). */
    private var sortByScore = false

    /** All results currently loaded from the database. */
    private var allResults: List<HearingResult> = emptyList()

    private lateinit var adapter: ResultsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.history_title)

        adapter = ResultsAdapter { result ->
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra("LEFT_THRESHOLDS", result.leftEarValues())
                putExtra("RIGHT_THRESHOLDS", result.rightEarValues())
                putExtra("RESULT_NAME", result.name)
                putExtra("RESULT_AGE_GROUP", result.ageGroup)
                putStringArrayListExtra("MEASUREMENTS", result.measurementList())
                putExtra("READ_ONLY", true)
            }
            startActivity(intent)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnToggleSort.setOnClickListener {
            sortByScore = !sortByScore
            binding.btnToggleSort.text = if (sortByScore) {
                getString(R.string.sort_by_date)
            } else {
                getString(R.string.sort_by_score)
            }
            supportActionBar?.title = if (sortByScore) {
                getString(R.string.leaderboard_title)
            } else {
                getString(R.string.history_title)
            }
            submitCurrentList()
        }

        lifecycleScope.launch {
            AppDatabase.getInstance(applicationContext).hearingResultDao().getAllResults().collect { results ->
                allResults = results
                submitCurrentList()
            }
        }
    }

    /** Submits the correctly sorted list to the adapter. */
    private fun submitCurrentList() {
        val sorted = if (sortByScore) {
            allResults.sortedByDescending { result ->
                val ageGroup = resolveAgeGroup(result.ageGroup)
                calculateHearingScore(result.leftEarValues(), result.rightEarValues(), ageGroup)
            }
        } else {
            allResults  // already ordered by timestamp DESC from the DAO query
        }
        adapter.submitList(sorted, showRanks = sortByScore)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<HearingResult>() {
    override fun areItemsTheSame(oldItem: HearingResult, newItem: HearingResult) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: HearingResult, newItem: HearingResult) = oldItem == newItem
}

class ResultsAdapter(
    private val onClick: (HearingResult) -> Unit
) : ListAdapter<HearingResult, ResultsAdapter.ViewHolder>(DIFF_CALLBACK) {

    private var showRanks = false

    fun submitList(list: List<HearingResult>, showRanks: Boolean) {
        this.showRanks = showRanks
        submitList(list)
    }

    inner class ViewHolder(private val binding: ItemResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(result: HearingResult, rank: Int?) {
            val ageGroup = resolveAgeGroup(result.ageGroup)
            val score = calculateHearingScore(result.leftEarValues(), result.rightEarValues(), ageGroup)

            val namePrefix = when (rank) {
                1 -> "🥇 "
                2 -> "🥈 "
                3 -> "🥉 "
                null -> ""
                else -> "#$rank  "
            }
            binding.tvResultName.text = "$namePrefix${result.name}"

            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            binding.tvResultDate.text = sdf.format(Date(result.timestamp))
            // Format age group: "YOUNG_ADULT_18_35" → "Young Adult 18-35"
            binding.tvResultAge.text = result.ageGroup
                .split("_")
                .joinToString(" ") { word ->
                    if (word.all { it.isDigit() || it == '-' }) word
                    else word.lowercase().replaceFirstChar { it.uppercase() }
                }
                .replace(Regex("(\\d) (\\d)"), "$1-$2")

            val emoji = when {
                score >= 80 -> "🏆"
                score >= 60 -> "✅"
                score >= 40 -> "⚠️"
                else        -> "🔴"
            }
            binding.tvResultScore.text = "$emoji  ${binding.root.context.getString(R.string.score_display, score)}"

            binding.root.setOnClickListener { onClick(result) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rank = if (showRanks) position + 1 else null
        holder.bind(getItem(position), rank)
    }
}
