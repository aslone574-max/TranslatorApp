# 🌐 즉시 통역기 (Instant Translator)

마이크를 꾹 누르고 말하면 **자동으로 언어를 감지**해서 번역해드립니다!

## ✨ 지원 언어

| 언어 | 방향 |
|------|------|
| 🇰🇷 한국어 | 기준 언어 |
| 🇺🇸 영어 | ↔ 한국어 |
| 🇯🇵 일본어 | ↔ 한국어 |
| 🇨🇳 중국어(간체) | ↔ 한국어 |
| 🇹🇼 중국어(번체/대만) | ↔ 한국어 |
| 🇭🇰 광동어 | ↔ 한국어 |
| 🇹🇭 태국어 | ↔ 한국어 |
| 🇻🇳 베트남어 | ↔ 한국어 |

## 🔧 작동 원리

```
🎤 마이크 → Groq Whisper (STT + 언어 자동감지) → Groq LLaMA 70B (번역) → 🔊 TTS 재생
```

- **STT**: Whisper Large V3 (업계 최고 수준 정확도)
- **번역**: LLaMA 3.3 70B (자연스러운 구어체 번역)
- **TTS**: Android 내장 엔진

## 🚀 APK 빌드 방법

### 1단계: 무료 API 키 발급
1. [console.groq.com](https://console.groq.com) 접속
2. 구글 계정으로 무료 가입
3. **API Keys** → **Create API Key** → 복사

### 2단계: Android Studio 설치
[developer.android.com/studio](https://developer.android.com/studio) 에서 다운로드 & 설치

### 3단계: 프로젝트 열기
```
Android Studio → File → Open → TranslatorApp 폴더
```

### 4단계: local.properties 설정
프로젝트 루트에 `local.properties` 파일 생성:
```properties
sdk.dir=C:\Users\홍길동\AppData\Local\Android\Sdk    # Windows
sdk.dir=/Users/홍길동/Library/Android/sdk            # Mac

GROQ_API_KEY=gsk_여기에_본인_키_입력
```

### 5단계: APK 빌드
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```
→ `app/build/outputs/apk/debug/app-debug.apk` 생성!

### 6단계: 핸드폰 설치
APK 파일을 핸드폰으로 전송 후 설치
(설정 → 알 수 없는 앱 → 허용 필요)

---

## 📱 사용법

1. **목표 언어 선택** (상단 언어 칩)
2. **마이크 버튼 꾹 누르기** → 말하기 → 손 떼기
3. 자동으로 번역 + TTS 재생!

### 자동 감지
- **한국어로 말하면** → 선택된 외국어로 번역
- **외국어로 말하면** → 자동 감지 후 한국어로 번역

### 텍스트 입력
왼쪽 ✏️ 버튼을 누르면 텍스트로 직접 입력 가능

---

## 💰 비용

| 항목 | 비용 |
|------|------|
| Groq API | **무료** |
| Whisper STT | 무료 (분당 20회) |
| LLaMA 번역 | 무료 (분당 30회) |
| TTS | 무료 (Android 내장) |

일반 여행 사용량으로는 무료 한도 내 충분히 사용 가능!

---

## 🛠️ 기술 스택

- **언어**: Kotlin
- **STT**: Groq Whisper Large V3
- **번역**: Groq LLaMA 3.3 70B Versatile  
- **TTS**: Android TextToSpeech
- **UI**: Material Design 3

---

문의사항은 Claude에게 물어보세요 🤖
