package com.example.aivoiceassistant

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.example.aivoiceassistant.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: android.content.SharedPreferences

    companion object {
        const val PREFS_NAME = "ai_voice_assistant_prefs"
        const val KEY_SPEED = "voice_speed" // disimpan sebagai progress 0..20, default 10 (=1.0x)
        const val KEY_PITCH = "voice_pitch"

        fun speedProgressToRate(progress: Int): Float = (progress / 10f).coerceIn(0.1f, 2.0f)
        fun pitchProgressToValue(progress: Int): Float = (progress / 10f).coerceIn(0.1f, 2.0f)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        binding.seekSpeed.progress = prefs.getInt(KEY_SPEED, 10)
        binding.seekPitch.progress = prefs.getInt(KEY_PITCH, 10)

        binding.seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                prefs.edit().putInt(KEY_SPEED, progress.coerceAtLeast(1)).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                prefs.edit().putInt(KEY_PITCH, progress.coerceAtLeast(1)).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}
