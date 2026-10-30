# Yasmin AI - Android (Stage E)

Native Kotlin + Jetpack Compose chat app. Dark theme, chat bubbles, typing indicator,
local SQLite history, settings dialog.

## Structure
- `app/src/main/java/com/yasmin/ai/MainActivity.kt`
- `data/` : `HistoryDb` (SQLite), `SecureSettings` (encrypted API key), `LlmClient` (HTTP), `ChatRepository`
- `ui/`   : `ChatViewModel`, `ChatScreen`, `Theme`
- `.github/workflows/build.yml` : cloud APK build

## Build (GitHub Actions)
Push to `main` -> repo's **Actions** tab -> latest run -> **Artifacts** -> `yasmin-debug-apk`.
Unzip it, install `app-debug.apk` (allow "unknown sources").

## First run
Settings (gear icon) -> paste your API key (Groq: https://console.groq.com). Defaults: Groq + `openai/gpt-oss-120b`.

## Privacy and security
- Messages and recent history (last 20) are sent to the provider in Base URL (Groq by default). Settings shows the host.
- Chat history stays only in the app's private storage. Backups are disabled.
- The API key is typed by you at runtime and stored encrypted on the device. It is NOT in the source or APK.
- This is fine for a personal app. For an app distributed to other people, never ship a key:
  run a small backend proxy that holds the provider key, authenticates users, rate-limits,
  and forwards `/chat/completions`; then point Base URL to that proxy.
- Only the INTERNET permission is requested. No microphone access exists yet.

## Checklist
Implemented (code written; NOT yet compiled or run by the author):
- [x] Dark chat UI, user/AI bubbles, input, send button
- [x] Typing indicator while waiting for AI
- [x] ViewModel + Repository architecture
- [x] Local conversation history, delete from Settings
- [x] Encrypted API key storage, configurable provider/model
- [x] Hinglish-aware persona prompt

Not implemented yet:
- [ ] Mic / speech recognition (Stage G)
- [ ] Text-to-speech, language and voice selection, speech rate (Stage C/F equivalents on Android)
- [ ] Approved memories screen
- [ ] Animated avatar and waveform (Stage F)
- [ ] Wake word (Stage G, investigation only)
- [ ] Phone functions with confirmation

## Troubleshooting
- Build fails: open the failed run in Actions, copy the red error lines and send them.
- HTTP 401: key wrong. 404: model/Base URL wrong. 403: network or key blocked. 429: rate limit.
