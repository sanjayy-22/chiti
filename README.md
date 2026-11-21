# Chitti Replica (Android prototype)

A functional replica of the *Chitti: Your Call Assistant* concept: a friendly cat
assistant that answers calls when you can't, talks to the caller, and leaves you a
note — who called, what they wanted, and one-tap ways to act on it.

**Provider: Groq Cloud.** Chat via `POST /chat/completions`, speech-to-text via
Groq Whisper `POST /audio/transcriptions` (OpenAI-compatible `https://api.groq.com/openai/v1`).
Spoken replies use Android's built-in text-to-speech, so no second provider is needed.
No Groq key? The app runs in scripted-reply mode instead.

## 1. Move the project home

It was built in a sandbox temp dir. Move it once with PowerShell:

```powershell
Move-Item -Path 'C:\Users\sanja\AppData\LocalLow\muse-shell-sandbox-01a1fc92-21d9-7b62-9c24-cba70d00a74e\chitti-replica' -Destination 'C:\Users\sanja\chitti-replica'
```

## 2. Get a Groq key (free)

1. Go to https://console.groq.com/keys and create a key (`gsk_…`).
2. Either paste it in the app's **Settings** screen (recommended, no rebuild), or put
   `groqApiKey=gsk_…` in `local.properties` (copy from `local.properties.example`).
   Keys never leave the device except in calls to `api.groq.com`.

## 3. Open and run

1. Open `C:\Users\sanja\chitti-replica` in Android Studio (it will write `sdk.dir`
   into `local.properties` for you).
2. Let Gradle sync (needs internet for first sync: AGP 8.5.2, Kotlin 1.9.24, Compose BOM).
3. Run on an emulator or device (min SDK 29). Grant mic permission when asked.

## 4. Demo flow (simulated calls — no carrier needed)

1. Home → enter a caller number → **Ring my phone (simulation)**.
2. **Let Chitti answer** → conversation screen. The caller side is *you*:
   type the caller's words, or tap the mic button to speak (transcribed by Whisper).
3. Chitti replies (Groq, or scripted without a key) and speaks each reply aloud.
4. **End call** → Chitti summarizes (who called, what they wanted, follow-ups) and
   saves the note. From the note: call back (dialer), message (SMS), add contact,
   report spam. Full transcript is kept under the summary.

## 5. Real-network call screening (optional, device only)

`call/ChittiScreeningService.kt` is a `CallScreeningService` scaffold. To use it for
real calls on Android 10+: install on a device, grant the **Call Screening role**
(Settings → Apps → Default apps), and extend the service to bridge screened calls
into the conversation flow. Emulators cannot receive real carrier calls.

## File map

- `ui/` — `HomeScreen` (simulate + notes log), `IncomingCallScreen`,
  `ConversationScreen` (+ mic/TTS/transcript), `NoteScreen` (summary + one-tap actions),
  `SettingsScreen` (key, model, connection test)
- `ai/AssistantEngine.kt` — engine interface, `GroqEngine` (chat + Whisper +
  JSON summaries), `ScriptedEngine` fallback, `Engines.create()`
- `data/` — `Models`/`CallStore` (in-memory notes), `Settings` (key + model prefs)
- `call/` — `SimulatedCallManager`, `ChittiScreeningService`

## Notes & costs

- Groq has a free tier; Whisper + chat calls are billed per use on paid tiers.
- Notes are in-memory (cleared on process death) — persistence is the obvious next step.
- Package `com.example.chittireplica` is a placeholder; the UI/branding here is an
  original demo, not the store app's assets.
- Status: code complete and self-reviewed; **not compiled in this environment**
  (no shell/build tools here) — first Gradle sync in Android Studio is the verification step.
