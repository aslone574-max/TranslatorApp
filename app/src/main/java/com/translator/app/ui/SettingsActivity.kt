package com.translator.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.translator.app.R
import com.translator.app.model.Language
import com.translator.app.model.Languages

class SettingsActivity : AppCompatActivity() {

    private val vm: TranslatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply { setBackgroundResource(R.drawable.bg_gradient) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        scroll.addView(container)
        setContentView(scroll)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "설정"

        // ─── 모드 상태 ───
        val modeText = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 32)
        }
        vm.modeStatus.observe(this) { modeText.text = "현재: $it" }
        container.addView(modeText)

        // ─── API 키 카드 ───
        addSectionTitle(container, "🔑 Groq API 키 (온라인 고품질)")
        val apiKeyInput = TextInputEditText(this).apply { setText(vm.getApiKey()); textSize = 14f }
        val til = TextInputLayout(this).apply {
            hint = "gsk_xxxxxxxxxxxxxxxxxxxx"
            addView(apiKeyInput)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        container.addView(til)

        val saveBtn = MaterialButton(this).apply {
            text = "✅ 저장"
            setOnClickListener {
                val key = apiKeyInput.text?.toString()?.trim() ?: ""
                vm.saveApiKey(key)
                modeText.text = "현재: ${getStatusText()}"
                Toast.makeText(this@SettingsActivity, if (key.isEmpty()) "API 키 삭제됨 → 오프라인 모드" else "저장 완료! 온라인 모드 활성화", Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(saveBtn)

        val getKeyBtn = MaterialButton(this).apply {
            text = "🌐 무료 API 키 발급 →"
            setBackgroundColor(0x33FFFFFF)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 12; bottomMargin = 40 }
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))) }
        }
        container.addView(getKeyBtn)

        // ─── 오프라인 언어팩 ───
        addSectionTitle(container, "📦 오프라인 언어팩 (Wi-Fi로 1회 다운로드)")

        listOf(
            Triple(Languages.ENGLISH, TranslateLanguage.ENGLISH, "영어"),
            Triple(Languages.JAPANESE, TranslateLanguage.JAPANESE, "일본어"),
            Triple(Languages.CHINESE_SIMPLIFIED, TranslateLanguage.CHINESE, "중국어"),
            Triple(Languages.THAI, TranslateLanguage.THAI, "태국어"),
            Triple(Languages.VIETNAMESE, TranslateLanguage.VIETNAMESE, "베트남어")
        ).forEach { (lang, mlCode, name) -> addDownloadRow(container, lang, mlCode, name) }
    }

    private fun addSectionTitle(container: LinearLayout, title: String) {
        container.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        })
    }

    private fun addDownloadRow(container: LinearLayout, lang: Language, mlCode: String, name: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 12)
        }
        val label = TextView(this).apply {
            text = "${lang.flag} $name"
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val progress = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(48, 48).apply { marginEnd = 16 }
        }
        val btn = MaterialButton(this).apply { text = "다운로드"; textSize = 12f }

        val options = TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.KOREAN).setTargetLanguage(mlCode).build()
        val translator = Translation.getClient(options)
        translator.downloadModelIfNeeded().addOnSuccessListener { btn.text = "✅ 완료"; btn.isEnabled = false }

        btn.setOnClickListener {
            btn.visibility = View.GONE; progress.visibility = View.VISIBLE
            translator.downloadModelIfNeeded()
                .addOnSuccessListener { progress.visibility = View.GONE; btn.visibility = View.VISIBLE; btn.text = "✅ 완료"; btn.isEnabled = false; Toast.makeText(this, "$name 완료!", Toast.LENGTH_SHORT).show() }
                .addOnFailureListener { progress.visibility = View.GONE; btn.visibility = View.VISIBLE; Toast.makeText(this, "Wi-Fi 연결을 확인해주세요.", Toast.LENGTH_SHORT).show() }
        }

        row.addView(label); row.addView(progress); row.addView(btn)
        container.addView(row)
    }

    private fun getStatusText(): String {
        val online = vm.translationService.isOnline()
        val hasKey = vm.translationService.hasApiKey()
        return when {
            online && hasKey -> "🌐 온라인 (고품질)"
            online && !hasKey -> "🔑 API 키 없음 → 오프라인 모드"
            else -> "📴 오프라인 모드"
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
