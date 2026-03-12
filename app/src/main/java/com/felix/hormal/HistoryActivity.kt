package com.felix.hormal

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.felix.hormal.data.AppDatabase
import com.felix.hormal.data.HearingResult
import com.felix.hormal.databinding.ActivityHistoryBinding
import com.felix.hormal.databinding.ItemResultBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.history_title)

        val adapter = ResultsAdapter { result ->
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra("LEFT_THRESHOLDS", result.leftEarValues())
                putExtra("RIGHT_THRESHOLDS", result.rightEarValues())
                putExtra("RESULT_NAME", result.name)
                putExtra("RESULT_AGE_GROUP", result.ageGroup)
                putExtra("READ_ONLY", true)
            }
            startActivity(intent)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            AppDatabase.getInstance(applicationContext).hearingResultDao().getAllResults().collect { results ->
                adapter.submitList(results)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

class ResultsAdapter(
    private val onClick: (HearingResult) -> Unit
) : RecyclerView.Adapter<ResultsAdapter.ViewHolder>() {

    private var items: List<HearingResult> = emptyList()

    fun submitList(list: List<HearingResult>) {
        items = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(result: HearingResult) {
            binding.tvResultName.text = result.name
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            binding.tvResultDate.text = sdf.format(Date(result.timestamp))
            // Format age group: "YOUNG_ADULT_18_35" → "Young Adult 18-35"
            binding.tvResultAge.text = result.ageGroup
                .split("_")
                .joinToString(" ") { word ->
                    // Keep numeric parts and dashes as-is, capitalize letters
                    if (word.all { it.isDigit() || it == '-' }) word
                    else word.lowercase().replaceFirstChar { it.uppercase() }
                }
                .replace(Regex("(\\d) (\\d)"), "$1-$2")  // join digit groups with dash
            binding.root.setOnClickListener { onClick(result) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}
