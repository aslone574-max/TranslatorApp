package com.translator.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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

        binding.etApiKey.setText(vm.getApiKey())

        binding.btnSave.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim() ?: ""
            if (key.isEmpty()) {
                Toast.makeText(this, "API 키를 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            vm.saveApiKey(key)
            Toast.makeText(this, "✅ 저장 완료!", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnGetKey.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys")))
        }

        binding.btnGroqInfo.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://groq.com")))
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
