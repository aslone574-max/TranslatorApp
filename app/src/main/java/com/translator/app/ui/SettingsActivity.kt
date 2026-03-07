package com.translator.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.translator.app.R
import com.translator.app.databinding.ActivitySettingsBinding
import com.translator.app.model.Language
import com.translator.app.model.Languages

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val vm: TranslatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "설정"

        binding.etApiKey.setText(vm.getApiKey())

        binding.btnSave.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim() ?: ""
            vm.saveApiKey(key)
            Toast.makeText(this, if (key.isEmpty()) "오프라인 모드" else "✅ 저장! 온라인 모드", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnGetKey.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys")))
        }

        binding.btnGroqInfo.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://groq.com")))
        }

        setupFontSizeControl()
        setupDownloadButtons()
    }

    private fun setupFontSizeControl() {
        val container = (binding.root.getChildAt(0) as? android.widget.LinearLayout) ?: return

        val title = TextView(this).apply {
            text = "🔤 번역 결과 폰트 크기"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 48, 0, 8)
        }
        container.addView(title)

        val sizeLabel = TextView(this).apply {
            text = "현재: ${vm.fontSize.value?.toInt() ?: 20}sp"
            textSize = 14f
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(0, 0, 0, 8)
        }
        container.addView(sizeLabel)

        val seekBar = SeekBar(this).apply {
            min = 14
            max = 36
            progress = vm.fontSize.value?.toInt() ?: 20
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    sizeLabel.text = "현재: ${progress}sp"
                    vm.setFontSize(progress.toFloat())
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        container.addView(seekBar)
    }

    private fun setupDownloadButtons() {
        val container = (binding.root.getChildAt(0) as? android.widget.LinearLayout) ?: return

        val title = TextView(this).apply {
            text = "📦 오프라인 언어팩 (Wi-Fi로 1회 다운로드)"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 32, 0, 16)
        }
        container.addView(title)

        listOf(
            Triple(TranslateLanguage.ENGLISH, "영어", Languages.ENGLISH),
            Triple(TranslateLanguage.JAPANESE, "일본어", Languages.JAPANESE),
            Triple(TranslateLanguage.CHINESE, "북경어/광동어 (중국어)", Languages.CHINESE_MAINLAND),
            Triple(TranslateLanguage.THAI, "태국어", Languages.THAI),
            Triple(TranslateLanguage.VIETNAMESE, "베트남어", Languages.VIETNAMESE),
            Triple(TranslateLanguage.SPANISH, "스페인어", Languages.SPANISH)
        ).forEach { (mlCode, name, lang) -> addDownloadRow(container, lang, mlCode, name) }
    }

    private fun addDownloadRow(container: LinearLayout, lang: Language, mlCode: String, name: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 12)
        }
        val label = TextView(this).apply {
            text = "${lang.flag} $name"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val progress = ProgressBar(this).apply {
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(48, 48).apply { marginEnd = 16 }
        }
        val btn = com.google.android.material.button.MaterialButton(this).apply { text = "다운로드"; textSize = 12f }

        val options = TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.KOREAN).setTargetLanguage(mlCode).build()
        val translator = Translation.getClient(options)
        translator.downloadModelIfNeeded().addOnSuccessListener { btn.text = "✅ 완료"; btn.isEnabled = false }

        btn.setOnClickListener {
            btn.visibility = android.view.View.GONE; progress.visibility = android.view.View.VISIBLE
            translator.downloadModelIfNeeded()
                .addOnSuccessListener { progress.visibility = android.view.View.GONE; btn.visibility = android.view.View.VISIBLE; btn.text = "✅ 완료"; btn.isEnabled = false; Toast.makeText(this, "$name 완료!", Toast.LENGTH_SHORT).show() }
                .addOnFailureListener { progress.visibility = android.view.View.GONE; btn.visibility = android.view.View.VISIBLE; Toast.makeText(this, "Wi-Fi 확인해주세요.", Toast.LENGTH_SHORT).show() }
        }

        row.addView(label); row.addView(progress); row.addView(btn)
        container.addView(row)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
