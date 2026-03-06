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
        if (granted) startRecordingFlow()
        else Toast.makeText(this, "마이크 권한이 필요합니다", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 화면 항상 켜짐
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 상태바 투명
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLanguageChips()
        setupMicButton()
        setupTextInput()
        setupResultActions()
        observeViewModel()

        // API 키 미설정 안내
        if (vm.getApiKey().isEmpty()) {
            showApiKeyBanner()
        }
    }

    // ─── 언어 선택 칩 ───
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
                    // 다른 칩 해제
                    for (i in 0 until binding.languageChipGroup.childCount) {
                        val c = binding.languageChipGroup.getChildAt(i) as? Chip
                        if (c != chip) c?.isChecked = false
                    }
                    // 결과 초기화
                    vm.resetState()
                }
            }
            binding.languageChipGroup.addView(chip)
        }

        // 첫 번째 (영어) 기본 선택
        (binding.languageChipGroup.getChildAt(0) as? Chip)?.isChecked = true
    }

    // ─── 마이크 버튼 ───
    private fun setupMicButton() {
        var isPressed = false

        binding.btnMic.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    isPressed = true
                    checkPermissionAndRecord()
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    if (isPressed) {
                        isPressed = false
                        vm.stopRecordingAndTranslate()
                    }
                }
            }
            true
        }

        // 탭 모드도 지원 (접근성)
        binding.btnMicTap.setOnClickListener {
            if (vm.state.value is TranslatorState.Recording) {
                vm.stopRecordingAndTranslate()
            } else {
                checkPermissionAndRecord()
            }
        }
    }

    private fun checkPermissionAndRecord() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED -> startRecordingFlow()
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecordingFlow() {
        vm.startRecording()
        vibrate(50)
    }

    // ─── 텍스트 입력 ───
    private fun setupTextInput() {
        binding.btnTextMode.setOnClickListener {
            val isVisible = binding.textInputLayout.visibility == View.VISIBLE
            binding.textInputLayout.visibility = if (isVisible) View.GONE else View.VISIBLE
            if (!isVisible) binding.etTextInput.requestFocus()
        }

        binding.btnTranslateText.setOnClickListener {
            val text = binding.etTextInput.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                hideKeyboard()
                vm.translateText(text)
            }
        }

        binding.etTextInput.setOnEditorActionListener { _, _, _ ->
            binding.btnTranslateText.performClick()
            true
        }
    }

    // ─── 결과 버튼들 ───
    private fun setupResultActions() {
        // 원본 TTS
        binding.btnPlayOriginal.setOnClickListener {
            val result = (vm.state.value as? TranslatorState.Success)?.result ?: return@setOnClickListener
            val lang = result.detectedLanguage ?: if (result.direction == TranslationDirection.KOREAN_TO_FOREIGN)
                Languages.KOREAN else result.targetLanguage
            vm.speakText(result.originalText, lang)
        }

        // 번역본 TTS
        binding.btnPlayTranslated.setOnClickListener {
            val result = (vm.state.value as? TranslatorState.Success)?.result ?: return@setOnClickListener
            val lang = if (result.direction == TranslationDirection.KOREAN_TO_FOREIGN)
                result.targetLanguage else Languages.KOREAN
            vm.speakText(result.translatedText, lang)
        }

        // 복사
        binding.btnCopy.setOnClickListener {
            val result = (vm.state.value as? TranslatorState.Success)?.result ?: return@setOnClickListener
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("translation", result.translatedText))
            Toast.makeText(this, "복사되었습니다", Toast.LENGTH_SHORT).show()
        }

        // 설정
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // ─── ViewModel 관찰 ───
    private fun observeViewModel() {
        vm.state.observe(this) { state ->
            when (state) {
                is TranslatorState.Idle -> showIdle()
                is TranslatorState.Recording -> showRecording()
                is TranslatorState.Transcribing -> showProcessing("🎯 음성 인식 중...")
                is TranslatorState.Translating -> showProcessing("🌐 번역 중...")
                is TranslatorState.Success -> showResult(state.result)
                is TranslatorState.Error -> showError(state.message)
            }
        }

        vm.selectedLanguage.observe(this) { lang ->
            binding.tvTargetLang.text = "${lang.flag} ${lang.displayName}"
        }

        vm.currentAmplitude.observe(this) { amplitude ->
            binding.waveformView.setAmplitude(amplitude)
        }
    }

    private fun showIdle() {
        binding.waveformView.stopAnimation()
        binding.statusCard.visibility = View.GONE
        binding.btnMic.setImageResource(R.drawable.ic_mic)
        binding.btnMic.backgroundTintList = resources.getColorStateList(R.color.mic_normal, theme)
        binding.tvHint.text = "꾹 누르고 말하세요"
        binding.tvHint.visibility = View.VISIBLE
        binding.resultCard.visibility = View.GONE
    }

    private fun showRecording() {
        binding.waveformView.startAnimation()
        binding.statusCard.visibility = View.GONE
        binding.btnMic.setImageResource(R.drawable.ic_mic_active)
        binding.btnMic.backgroundTintList = resources.getColorStateList(R.color.mic_recording, theme)
        binding.tvHint.text = "손을 떼면 번역됩니다"
        binding.tvHint.visibility = View.VISIBLE
        binding.resultCard.visibility = View.GONE

        // 펄스 애니메이션
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        binding.btnMic.startAnimation(pulse)
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
        binding.tvHint.text = "꾹 누르고 말하세요"
        binding.resultCard.visibility = View.VISIBLE

        // 방향 표시
        val fromFlag = result.detectedLanguage?.flag ?: "🌐"
        val fromName = result.detectedLanguage?.displayName ?: "자동감지"
        val toFlag = if (result.direction == TranslationDirection.KOREAN_TO_FOREIGN)
            result.targetLanguage.flag else Languages.KOREAN.flag
        val toName = if (result.direction == TranslationDirection.KOREAN_TO_FOREIGN)
            result.targetLanguage.displayName else Languages.KOREAN.displayName

        binding.tvDirectionFrom.text = "$fromFlag $fromName"
        binding.tvDirectionTo.text = "$toFlag $toName"

        binding.tvOriginal.text = result.originalText
        binding.tvTranslated.text = result.translatedText

        // 슬라이드 인 애니메이션
        val slide = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        binding.resultCard.startAnimation(slide)

        vibrate(30)
    }

    private fun showError(message: String) {
        binding.waveformView.stopAnimation()
        binding.btnMic.clearAnimation()
        binding.statusCard.visibility = View.GONE
        binding.tvHint.visibility = View.VISIBLE
        binding.tvHint.text = "다시 시도해주세요"

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showApiKeyBanner() {
        binding.apiBanner.visibility = View.VISIBLE
        binding.apiBanner.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun vibrate(ms: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}
