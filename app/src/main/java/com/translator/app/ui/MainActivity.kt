package com.translator.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.view.View
import android.view.WindowManager
import android.view.animation.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.translator.app.R
import com.translator.app.databinding.ActivityMainBinding
import com.translator.app.model.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: TranslatorViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startRecording()
        else Toast.makeText(this, "마이크 권한이 필요합니다", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLanguageChips()
        setupMicButton()
        setupTextInput()
        setupResultActions()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        vm.updateModeStatus()
    }

    private fun setupLanguageChips() {
        Languages.TARGET_LANGUAGES.forEach { lang ->
            val chip = Chip(this).apply {
                text = "${lang.flag} ${lang.displayName}"
                isCheckable = true
                chipBackgroundColor = resources.getColorStateList(R.color.chip_selector, theme)
                setTextColor(resources.getColorStateList(R.color.chip_text_selector, theme))
                chipStrokeWidth = 2f
                setChipStrokeColorResource(R.color.chip_stroke_selector)
                textSize = 13f
                chipCornerRadius = 50f
            }
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    vm.selectLanguage(lang)
                    for (i in 0 until binding.languageChipGroup.childCount) {
                        val c = binding.languageChipGroup.getChildAt(i) as? Chip
                        if (c != chip) c?.isChecked = false
                    }
                    vm.resetState()
                }
            }
            binding.languageChipGroup.addView(chip)
        }
        (binding.languageChipGroup.getChildAt(0) as? Chip)?.isChecked = true
    }

    private fun setupMicButton() {
        // 탭 한 번 → 말하기 → 자동 번역
        binding.btnMic.setOnClickListener { checkPermissionAndRecord() }
        binding.btnMicTap.setOnClickListener { checkPermissionAndRecord() }
    }

    private fun checkPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            vm.startRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun setupTextInput() {
        binding.btnTextMode.setOnClickListener {
            val isVisible = binding.textInputLayout.visibility == View.VISIBLE
            binding.textInputLayout.visibility = if (isVisible) View.GONE else View.VISIBLE
            if (!isVisible) binding.etTextInput.requestFocus()
        }
        binding.btnTranslateText.setOnClickListener {
            val text = binding.etTextInput.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) { hideKeyboard(); vm.translateText(text) }
        }
        binding.etTextInput.setOnEditorActionListener { _, _, _ -> binding.btnTranslateText.performClick(); true }
    }

    private fun setupResultActions() {
        binding.btnPlayOriginal.setOnClickListener {
            val result = (vm.state.value as? TranslatorState.Success)?.result ?: return@setOnClickListener
            val lang = result.detectedLanguage ?: if (result.direction == TranslationDirection.KOREAN_TO_FOREIGN) Languages.KOREAN else result.targetLanguage
            vm.speakText(result.originalText, lang)
        }
        binding.btnPlayTranslated.setOnClickListener {
            val result = (vm.state.value as? TranslatorState.Success)?.result ?: return@setOnClickListener
            val lang = if (result.direction == TranslationDirection.KOREAN_TO_FOREIGN) result.targetLanguage else Languages.KOREAN
            vm.speakText(result.translatedText, lang)
        }
        binding.btnCopy.setOnClickListener {
            val result = (vm.state.value as? TranslatorState.Success)?.result ?: return@setOnClickListener
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("translation", result.translatedText))
            Toast.makeText(this, "복사되었습니다", Toast.LENGTH_SHORT).show()
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun observeViewModel() {
        vm.state.observe(this) { state ->
            when (state) {
                is TranslatorState.Idle -> showIdle()
                is TranslatorState.Recording -> showRecording()
                is TranslatorState.Transcribing -> showProcessing("🎯 인식 중...")
                is TranslatorState.Translating -> showProcessing("🌐 번역 중...")
                is TranslatorState.Success -> showResult(state.result)
                is TranslatorState.Error -> showError(state.message)
            }
        }
        vm.selectedLanguage.observe(this) { lang -> binding.tvTargetLang.text = "${lang.flag} ${lang.displayName}" }
        vm.currentAmplitude.observe(this) { binding.waveformView.setAmplitude(it) }
        vm.modeStatus.observe(this) { binding.tvHint.text = it }
        vm.fontSize.observe(this) { size ->
            binding.tvOriginal.textSize = size
            binding.tvTranslated.textSize = size + 2f
        }
    }

    private fun showIdle() {
        binding.waveformView.stopAnimation()
        binding.statusCard.visibility = View.GONE
        binding.btnMic.setImageResource(R.drawable.ic_mic)
        binding.btnMic.backgroundTintList = resources.getColorStateList(R.color.mic_normal, theme)
        binding.tvHint.visibility = View.VISIBLE
        binding.resultCard.visibility = View.GONE
    }

    private fun showRecording() {
        binding.waveformView.startAnimation()
        binding.statusCard.visibility = View.GONE
        binding.btnMic.setImageResource(R.drawable.ic_mic_active)
        binding.btnMic.backgroundTintList = resources.getColorStateList(R.color.mic_recording, theme)
        binding.tvHint.text = "말씀하세요..."
        binding.tvHint.visibility = View.VISIBLE
        binding.resultCard.visibility = View.GONE
        binding.btnMic.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse))
    }

    private fun showProcessing(message: String) {
        binding.waveformView.stopAnimation()
        binding.btnMic.clearAnimation()
        binding.statusCard.visibility = View.VISIBLE
        binding.tvStatus.text = message
        binding.tvHint.visibility = View.GONE
        binding.resultCard.visibility = View.GONE
    }

    private fun showResult(result: TranslationResult) {
        binding.statusCard.visibility = View.GONE
        binding.tvHint.visibility = View.VISIBLE
        binding.resultCard.visibility = View.VISIBLE

        val fromFlag = result.detectedLanguage?.flag ?: "🌐"
        val fromName = result.detectedLanguage?.displayName ?: "자동감지"
        val toFlag = if (result.direction == TranslationDirection.KOREAN_TO_FOREIGN) result.targetLanguage.flag else Languages.KOREAN.flag
        val toName = if (result.direction == TranslationDirection.KOREAN_TO_FOREIGN) result.targetLanguage.displayName else Languages.KOREAN.displayName

        binding.tvDirectionFrom.text = "$fromFlag $fromName"
        binding.tvDirectionTo.text = "$toFlag $toName"
        binding.tvOriginal.text = result.originalText
        binding.tvTranslated.text = result.translatedText
        binding.resultCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))
        vibrate(30)
    }

    private fun showError(message: String) {
        binding.waveformView.stopAnimation()
        binding.btnMic.clearAnimation()
        binding.statusCard.visibility = View.GONE
        binding.tvHint.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun vibrate(ms: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator.vibrate(ms)
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}
