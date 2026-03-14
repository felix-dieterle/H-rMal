package com.felix.hormal

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
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
import com.felix.hormal.model.scoreEmoji
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

        val db = AppDatabase.getInstance(applicationContext)

        adapter = ResultsAdapter(
            onClick = { result ->
                val intent = Intent(this, ResultActivity::class.java).apply {
                    putExtra("LEFT_THRESHOLDS", result.leftEarValues())
                    putExtra("RIGHT_THRESHOLDS", result.rightEarValues())
                    putExtra("RESULT_NAME", result.name)
                    putExtra("RESULT_AGE_GROUP", result.ageGroup)
                    putStringArrayListExtra("MEASUREMENTS", result.measurementList())
                    putExtra("READ_ONLY", true)
                }
                startActivity(intent)
            },
            onDelete = { result ->
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete_confirm_title))
                    .setMessage(getString(R.string.delete_confirm_message, result.name))
                    .setPositiveButton(getString(R.string.delete_confirm_yes)) { _, _ ->
                        lifecycleScope.launch {
                            db.hearingResultDao().delete(result)
                        }
                    }
                    .setNegativeButton(getString(R.string.delete_confirm_no), null)
                    .show()
            }
        )

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
            db.hearingResultDao().getAllResults().collect { results ->
                allResults = results
                submitCurrentList()
            }
        }
    }

    /** Submits the correctly sorted list to the adapter. */
    private fun submitCurrentList() {
        // Pre-compute scores once so sorting and binding both reuse the same values.
        val scores: Map<Long, Int> = allResults.associate { result ->
            val ageGroup = resolveAgeGroup(result.ageGroup)
            result.id to calculateHearingScore(result.leftEarValues(), result.rightEarValues(), ageGroup)
        }
        val sorted = if (sortByScore) {
            allResults.sortedByDescending { scores[it.id] ?: 0 }
        } else {
            allResults  // already ordered by timestamp DESC from the DAO query
        }
        adapter.submitList(sorted, showRanks = sortByScore, scores = scores)
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
    private val onClick: (HearingResult) -> Unit,
    private val onDelete: (HearingResult) -> Unit
) : ListAdapter<HearingResult, ResultsAdapter.ViewHolder>(DIFF_CALLBACK) {

    private var showRanks = false

    /** Cached scores keyed by result ID, computed once during sort in the activity. */
    private var scoreCache: Map<Long, Int> = emptyMap()

    /**
     * Submits [list] to the adapter.
     *
     * @param showRanks When true, rank badges (🥇/🥈/🥉/#N) are shown.
     * @param scores    Pre-computed hearing scores keyed by [HearingResult.id].
     *                  Should always be supplied by the activity so scores are
     *                  computed only once.  The default empty map triggers a
     *                  lazy per-item fallback computation in [ViewHolder.bind].
     */
    fun submitList(list: List<HearingResult>, showRanks: Boolean, scores: Map<Long, Int> = emptyMap()) {
        this.showRanks = showRanks
        this.scoreCache = scores
        submitList(list)
    }

    inner class ViewHolder(private val binding: ItemResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(result: HearingResult, rank: Int?) {
            // Use pre-computed score when available, otherwise compute on demand (e.g. initial load)
            val score = scoreCache[result.id] ?: run {
                val ageGroup = resolveAgeGroup(result.ageGroup)
                calculateHearingScore(result.leftEarValues(), result.rightEarValues(), ageGroup)
            }

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

            binding.tvResultScore.text = "${scoreEmoji(score)}  ${binding.root.context.getString(R.string.score_display, score)}"

            binding.root.setOnClickListener { onClick(result) }
            binding.btnDeleteResult.setOnClickListener { onDelete(result) }
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
