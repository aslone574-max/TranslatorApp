package com.translator.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.translator.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val vm: TranslatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "설정"

        // API 키 불러오기
        binding.etApiKey.setText(vm.getApiKey())

        // 현재 모드 표시
        vm.modeStatus.observe(this) { /* 메인에서만 표시 */ }

        // 저장 버튼
        binding.btnSave.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim() ?: ""
            vm.saveApiKey(key)
            Toast.makeText(this,
                if (key.isEmpty()) "API 키 삭제 → 오프라인 모드"
                else "✅ 저장! 온라인 모드 활성화",
                Toast.LENGTH_SHORT).show()
            finish()
        }

        // API 키 발급 링크
        binding.btnGetKey.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys")))
        }

        // Groq 정보
        binding.btnGroqInfo.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://groq.com")))
        }

        // 오프라인 언어팩 다운로드
        setupDownloadButtons()
    }

    private fun setupDownloadButtons() {
        val languages = listOf(
            Triple(TranslateLanguage.ENGLISH, "영어", "en"),
            Triple(TranslateLanguage.JAPANESE, "일본어", "ja"),
            Triple(TranslateLanguage.CHINESE, "중국어", "zh"),
            Triple(TranslateLanguage.THAI, "태국어", "th"),
            Triple(TranslateLanguage.VIETNAMESE, "베트남어", "vi")
        )

        // 언어팩 섹션을 btnSave 아래에 동적으로 추가
        val container = binding.root.getChildAt(0) as? android.widget.LinearLayout ?: return

        val title = android.widget.TextView(this).apply {
            text = "📦 오프라인 언어팩 (Wi-Fi로 1회 다운로드)"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 48, 0, 16)
        }
        container.addView(title)

        languages.forEach { (mlCode, name, _) ->
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val label = android.widget.TextView(this).apply {
                text = name
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val progress = android.widget.ProgressBar(this).apply {
                visibility = View.GONE
                layoutParams = android.widget.LinearLayout.LayoutParams(48, 48).apply { marginEnd = 16 }
            }
            val btn = com.google.android.material.button.MaterialButton(this).apply {
                text = "다운로드"
                textSize = 12f
            }

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.KOREAN)
                .setTargetLanguage(mlCode)
                .build()
            val translator = Translation.getClient(options)

            // 이미 다운됐는지 확인
            translator.downloadModelIfNeeded()
                .addOnSuccessListener { btn.text = "✅ 완료"; btn.isEnabled = false }

            btn.setOnClickListener {
                btn.visibility = View.GONE
                progress.visibility = View.VISIBLE
                translator.downloadModelIfNeeded()
                    .addOnSuccessListener {
                        progress.visibility = View.GONE
                        btn.visibility = View.VISIBLE
                        btn.text = "✅ 완료"
                        btn.isEnabled = false
                        Toast.makeText(this, "$name 다운로드 완료!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        progress.visibility = View.GONE
                        btn.visibility = View.VISIBLE
                        Toast.makeText(this, "Wi-Fi 연결을 확인해주세요.", Toast.LENGTH_SHORT).show()
                    }
            }

            row.addView(label)
            row.addView(progress)
            row.addView(btn)
            container.addView(row)
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}