# Yasmin AI - Android (Stage G: push-to-talk)

Native Kotlin + Jetpack Compose chat app with an animated original avatar, speaking waveform
and Android text-to-speech.

## Structure
- `MainActivity.kt`
- `data/` : `HistoryDb`, `SecureSettings`, `LlmClient`, `ChatRepository`
- `voice/Speaker.kt` : Android TextToSpeech wrapper, Hindi/English detection, voice list
- `ui/` : `ChatViewModel`, `ChatScreen`, `AvatarView` (+ clock), `WaveformView`, `Theme`
- `.github/workflows/build.yml`

## Build
Push to `main` -> Actions tab -> `yasmin-debug-apk` artifact. Uninstall the old app first
(each CI build is signed with a new debug key).

## Privacy and security
- Messages and the last 20 chat turns go to the provider in Base URL (Groq by default).
- Voice output uses the phone's TTS engine. Voices marked "online" may send text to that engine's servers; offline voices do not.
- History stays in app-private storage; backups are disabled. API key is stored encrypted, never in the APK.
- For an app shared with other people use a backend proxy that holds the provider key instead of a user-pasted key.
- Permissions: INTERNET, and RECORD_AUDIO (asked only when you first tap the mic button).
- Push-to-talk only: the mic is open only after you tap it, and closes on result, error, second tap, or when the app leaves the screen. There is no background service. Android shows its own mic indicator while it is open.
- Speech recognition is done by the phone's speech service (usually Google); on many phones the audio goes online.

## Checklist
Written, not yet compiled or run by the author (verify on device):
- [x] Animated avatar: idle (breathing, blinking), thinking, speaking
- [x] Speaking waveform (simulated, see below), stops when speech ends
- [x] Thinking indicator (typing dots + avatar arcs)
- [x] Android TTS, auto/Hindi/English mode, voice pick per language, speed, volume, on/off
- [x] Message explaining when a voice is missing
- [x] Tap avatar to stop speaking; mute button in header

Stage G (written, not yet compiled or run by the author):
- [x] Push-to-talk mic button, runtime permission, partial text, auto-send of recognised text
- [x] Listening animation on the avatar while the mic is open
- [x] Hindi / English mic language toggle (HI / EN chip). Android cannot auto-detect the spoken language.
- [x] Mic stops when the app goes to background; denied-permission banner with App settings button

Not implemented:
- [ ] "Hey Yasmin" wake word (investigated, NOT implemented)
- [ ] Approved memories screen, phone functions with confirmation
- [ ] Guaranteed female voice (Android does not expose voice gender)
- [ ] True audio-level waveform (Android TTS gives no amplitude)
- [ ] Hinglish in Roman script read by the Hindi voice: needs on-device testing

## Troubleshooting
- No sound: media volume, voice output switch (speaker icon), Settings > Test.
- "Hindi voice available nahi": phone Settings > Text-to-speech > Google > Install voice data > Hindi.
- Build error: copy the red lines from the failed Actions run.
