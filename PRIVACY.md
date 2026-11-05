# Privacy Policy

_Last updated: 5 November 2026_

Zabankhan is an Android and iOS app for learning languages through reading, listening and review. It has no accounts, ads, app-level analytics, crash reporting or developer-operated server. Android’s bundled ML Kit SDK has its own diagnostics and usage reporting, described below. The developer does not receive your study data.

## What the app stores

Your imported texts, PDFs, videos and subtitles, saved vocabulary and meanings, highlights, reading positions, review history, study statistics, pronunciation results and settings stay in the app's storage. Translations and synthesized audio may be cached locally. An optional LLM API key is stored with settings. Uninstalling removes app storage; exported files and device backups may remain.

## When the app goes online

Services receive the information needed for the selected feature and standard network information, including your IP address. Their own privacy policies apply. Online voice is the initial voice setting; selecting the device voice avoids the Edge service. Translation can send selected text and surrounding context automatically when you open a word's meaning.

| Feature | Recipient | Information sent |
|---|---|---|
| Translation | Google Translate web endpoints, with MyMemory as fallback | Selected words, sentences or paragraphs and language pair |
| Optional LLM translation | The provider you choose: OpenRouter, DeepSeek, Qwen, Kimi or a custom HTTPS endpoint | Translation prompt, text/context, languages, model and API credential |
| Online read-aloud | Microsoft Edge online voice service | Text to speak, voice and language |
| Device read-aloud | The installed system speech engine | Text and language; network use depends on the engine and installed voice |
| Pronunciation/shadowing | Android SpeechRecognizer service or Apple Speech | Microphone audio and recognition language. iOS requests on-device recognition when supported; recognition may otherwise use the platform provider's servers. Android behavior depends on the installed service. |
| Dictionary links | The dictionary selected for the learning language, such as WordReference, B-amooz, Glosbe, Cambridge, Larousse, Linguee, Reverso, Wiktionary, Merriam-Webster, Vajehyab or Abadis | Lookup word in a browser request |
| News and reading content | RFI, franceinfo, VOA, BBC, Vikidia, Simple English Wikipedia and article sources you open | Feed, search or article request; search terms if entered |
| YouTube | YouTube/Google, including the embedded IFrame player and caption endpoints | Video/channel identifiers, playback requests, caption language; the embedded website may use its own storage and network requests |

Android ML Kit may send Google device/app information, installation identifiers and performance/usage metrics for diagnostics and analytics. It processes OCR images and recognition results on-device. See [Google’s ML Kit disclosure](https://developers.google.com/ml-kit/android-data-disclosure). This SDK behavior applies to both Android flavors, independently of the developer.

PDF rendering and OCR use local libraries (Android PDFBox/ML Kit bundled recognition; Apple PDFKit/Vision). Local video and imported subtitles are processed on the device. Microphone audio is used for speech recognition and is not saved by Zabankhan as recordings; recognition services may process it under their own policies.

## Backups

Local ZIP backups contain the database (texts, vocabulary, history and statistics), settings **including any saved LLM API key**, imported PDFs and videos/subtitles. They are not encrypted by the app. Keep them private. Automatic local snapshots retain the five newest successful backups; exported copies remain where you put them.

Android's system backup is enabled for eligible app data. Cache/no-backup directories are excluded by Android; our rules also exclude the preferences file containing the API key, automatic backup archives and restore staging. Android device backup is managed by the operating system and your chosen backup provider.

On iOS you can share a backup to Files, including iCloud Drive if you choose. The current app has no Google Drive sign-in or direct cloud backup integration, in either Android flavor. Files you export are managed by the destination service; removing the app does not remove those copies. The operating system may also back up app data according to your device settings.

## Permissions

- Internet/network state: online translation, voice, content and video; network state is also used by Android background work.
- Microphone and Apple speech recognition: pronunciation and shadowing, after platform permission is granted.
- Notifications: optional review reminders. Denying permission does not prevent reading or review.
- Receive boot completed: reschedule Android reminders/background work after restart.
- Wake lock and foreground-service permissions inherited from WorkManager: scheduled local backup work. There is no continuous background microphone recording.

Files are selected through system pickers; the app does not request broad storage access. Android does not permit cleartext HTTP connections. No tracking permission is requested.

## Children

The developer does not knowingly collect personal information from anyone, including children. Online services have their own terms and age requirements.

## Changes

Policy updates will be published here with a revised date.

## Contact

Open an issue at <https://github.com/Ziaeemehr/zabankhan/issues>. Do not include private texts, API keys or backups in public issues.
