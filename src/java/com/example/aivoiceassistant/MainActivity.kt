package com.example.aivoiceassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aivoiceassistant.core.CommandProcessor
import com.example.aivoiceassistant.core.CommandResult
import com.example.aivoiceassistant.core.TtsManager
import com.example.aivoiceassistant.core.VoiceRecognizerManager
import com.example.aivoiceassistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var commandProcessor: CommandProcessor
    private lateinit var voiceRecognizer: VoiceRecognizerManager
    private lateinit var ttsManager: TtsManager
    private lateinit var historyAdapter: HistoryAdapter

    private var isListening = false

    // Menyimpan teks perintah yang tertunda karena menunggu izin runtime disetujui.
    private var pendingCommandText: String? = null
    private var pendingCallContact: Pair<String, String>? = null // name to number, menunggu izin CALL_PHONE

    // --- Permission launchers ---
    private val requestRecordAudio = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListeningFlow()
        } else {
            toast(getString(R.string.perm_mic_denied))
        }
    }

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCommandText?.let { text ->
                pendingCommandText = null
                handleRecognizedText(text)
            }
        } else {
            toast(getString(R.string.perm_camera_denied))
            speakAndShow(getString(R.string.resp_flashlight_no_permission), pendingCommandText ?: "")
            pendingCommandText = null
        }
    }

    private val requestContacts = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCommandText?.let { text ->
                pendingCommandText = null
                handleRecognizedText(text)
            }
        } else {
            toast(getString(R.string.perm_contacts_denied))
            pendingCommandText = null
        }
    }

    private val requestCallPhone = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Baik granted maupun tidak, ContactCaller.placeCall() punya fallback ke ACTION_DIAL.
        pendingCallContact?.let { (name, number) ->
            pendingCallContact = null
            commandProcessor.contactCaller.placeCall(number)
            speakAndShow(getString(R.string.resp_calling, name), "Telepon $name")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        commandProcessor = CommandProcessor(this)
        historyAdapter = HistoryAdapter()
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = historyAdapter
        updateHistoryEmptyState()

        ttsManager = TtsManager(
            context = this,
            onReady = { ok ->
                if (ok) applySavedVoiceSettings()
            }
        )

        voiceRecognizer = VoiceRecognizerManager(
            context = this,
            onListeningStarted = {
                isListening = true
                setStatus(getString(R.string.status_listening))
                binding.soundWaveView.setListening(true)
                binding.btnMic.setBackgroundResource(R.drawable.bg_mic_button_listening)
            },
            onPartialOrFinalResult = { text ->
                onSpeechRecognized(text)
            },
            onNoSpeech = {
                resetListeningUi()
                setStatus(getString(R.string.status_ready))
                speakAndShow(getString(R.string.resp_no_speech), "")
            },
            onError = { message ->
                resetListeningUi()
                setStatus(getString(R.string.status_error))
                toast(message)
            }
        )

        binding.btnMic.setOnClickListener { onMicClicked() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setStatus(getString(R.string.status_ready))
    }

    override fun onResume() {
        super.onResume()
        // Terapkan ulang preferensi suara jika pengguna baru saja mengubahnya di Settings.
        applySavedVoiceSettings()
    }

    // ---------------- Mic flow ----------------

    private fun onMicClicked() {
        if (isListening) return
        if (!voiceRecognizer.isAvailable()) {
            toast("Pengenalan suara tidak tersedia di perangkat ini.")
            return
        }
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            startListeningFlow()
        } else {
            toast(getString(R.string.perm_mic_rationale))
            requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListeningFlow() {
        binding.tvRecognizedText.text = getString(R.string.hint_recognized_text)
        voiceRecognizer.startListening()
    }

    private fun onSpeechRecognized(text: String) {
        isListening = false
        binding.soundWaveView.setListening(false)
        binding.btnMic.setBackgroundResource(R.drawable.bg_mic_button)
        binding.tvRecognizedText.text = text
        setStatus(getString(R.string.status_processing))
        handleRecognizedText(text)
    }

    private fun resetListeningUi() {
        isListening = false
        binding.soundWaveView.setListening(false)
        binding.btnMic.setBackgroundResource(R.drawable.bg_mic_button)
    }

    // ---------------- Command handling ----------------

    private fun handleRecognizedText(text: String) {
        val lower = text.lowercase()

        // Cek izin kamera lebih dulu jika ini perintah senter, agar pesan permission
        // dijelaskan sebelum sistem menampilkan dialog izin.
        val isFlashlightCommand = lower.contains("senter") || lower.contains("flash")
        if (isFlashlightCommand && !hasPermission(Manifest.permission.CAMERA)) {
            pendingCommandText = text
            toast(getString(R.string.perm_camera_rationale))
            requestCamera.launch(Manifest.permission.CAMERA)
            return
        }

        // Cek izin kontak lebih dulu jika ini perintah telepon/hubungi.
        val callKeywords = listOf("telepon ", "hubungi ", "panggil ")
        val isCallCommand = callKeywords.any { lower.contains(it) }
        if (isCallCommand && !hasPermission(Manifest.permission.READ_CONTACTS)) {
            pendingCommandText = text
            toast(getString(R.string.perm_contacts_rationale))
            requestContacts.launch(Manifest.permission.READ_CONTACTS)
            return
        }

        when (val result = commandProcessor.process(text)) {
            is CommandResult.Done -> {
                speakAndShow(result.responseText, text)
            }
            is CommandResult.NeedContactName -> {
                speakAndShow(result.responseText, text)
            }
            is CommandResult.NeedCallConfirmation -> {
                showCallConfirmationDialog(result.contactName, result.phoneNumber, text)
            }
            is CommandResult.Unknown -> {
                speakAndShow(result.responseText, text)
            }
        }
    }

    private fun showCallConfirmationDialog(contactName: String, phoneNumber: String, originalCommand: String) {
        val message = getString(R.string.resp_confirm_call, contactName)
        setStatus(getString(R.string.status_processing))
        ttsManager.speak(message)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_call_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.btn_call)) { dialog, _ ->
                dialog.dismiss()
                proceedWithCall(contactName, phoneNumber)
            }
            .setNegativeButton(getString(R.string.btn_cancel)) { dialog, _ ->
                dialog.dismiss()
                speakAndShow(getString(R.string.resp_call_cancelled), originalCommand)
            }
            .setCancelable(true)
            .show()
    }

    private fun proceedWithCall(contactName: String, phoneNumber: String) {
        if (hasPermission(Manifest.permission.CALL_PHONE)) {
            commandProcessor.contactCaller.placeCall(phoneNumber)
            speakAndShow(getString(R.string.resp_calling, contactName), "Telepon $contactName")
        } else {
            pendingCallContact = contactName to phoneNumber
            toast(getString(R.string.perm_call_rationale))
            requestCallPhone.launch(Manifest.permission.CALL_PHONE)
        }
    }

    // ---------------- Output helpers ----------------

    private fun speakAndShow(responseText: String, commandText: String) {
        setStatus(getString(R.string.status_done))
        ttsManager.speak(responseText)
        if (commandText.isNotBlank()) {
            historyAdapter.addItem(HistoryItem(commandText, responseText))
            binding.rvHistory.scrollToPosition(0)
            updateHistoryEmptyState()
        }
    }

    private fun updateHistoryEmptyState() {
        binding.tvHistoryEmpty.visibility =
            if (historyAdapter.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.rvHistory.visibility =
            if (historyAdapter.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun setStatus(text: String) {
        binding.tvStatus.text = text
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun applySavedVoiceSettings() {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val speedProgress = prefs.getInt(SettingsActivity.KEY_SPEED, 10)
        val pitchProgress = prefs.getInt(SettingsActivity.KEY_PITCH, 10)
        ttsManager.setSpeechRate(SettingsActivity.speedProgressToRate(speedProgress))
        ttsManager.setPitch(SettingsActivity.pitchProgressToValue(pitchProgress))
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceRecognizer.stopListening()
        ttsManager.shutdown()
    }
}
