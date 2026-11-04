# پلن ساخت اپ «زبان‌خوان» برای اندروید و iOS

## زمینه و هدف

پروژهٔ `FrenchReaderSpike` یک اپ اندرویدی است با `Kotlin` و `Jetpack Compose` و `Room`، حدود ۲۵۹ فایل. این اپ فقط برای فرانسه به فارسی یا انگلیسی ساخته شده است. اپ جدید `zabankhan` در پوشهٔ `/Users/ziaee/git/zabankhan` همان تجربه را روی اندروید و iOS می‌دهد و هر زبانی را برای یادگیری می‌پذیرد.

تصمیم‌های گرفته‌شده:

- فناوری: `Kotlin Multiplatform` همراه با `Compose Multiplatform`.
- زبان‌های یادگیری نسخهٔ اول: فرانسوی و انگلیسی. فارسی هیچ‌وقت زبان یادگیری نیست و فقط زبان معنی است (پیش‌فرض)، انگلیسی هم به‌عنوان زبان معنی انتخاب‌شدنی است. معماری چندزبانه است.
- محل انتشار: کافه‌بازار و مایکت، `Google Play` و `App Store`/`TestFlight`.
- منبع کد: یک مخزن تازه. کد `FrenchReader` ماژول‌به‌ماژول منتقل و چندزبانه می‌شود و خود `FrenchReader` دست نمی‌خورد.

ویژگی‌های اصلی که کاربر خواسته است:

- مطالعهٔ متن با لمس کلمه و باز شدن دیکشنری.
- مرور کلمه‌ها با جعبهٔ لایتنر.
- تماشای ویدیو با زیرنویس قابل‌لمس.
- خواندن `PDF`، که اولویت بالایی دارد.
- پشتیبانی از قلم اپل در فاز آخر.

---

## ۱. معماری کلی

### ساختار مخزن

ساختار از قالب استاندارد `KMP wizard` شروع می‌شود و یک ماژول دارد. ماژول‌ها فقط وقتی جدا می‌شوند که لازم شود.

```
zabankhan/
  composeApp/
    src/commonMain/   # UI + منطق + داده (بیشتر کد)
    src/androidMain/  # actualهای اندروید
    src/iosMain/      # actualهای iOS (Kotlin/Native)
    src/commonTest/
  iosApp/             # پروژهٔ Xcode، SwiftUI host + کد Swift بومی (PDFKit, PencilKit)
  content-tools/      # اسکریپت‌های پایتون ساخت بستهٔ زبان (از tools/ قدیمی، با --lang)
  gradle/libs.versions.toml
```

پکیج برنامه:

```
com.ziaee.zabankhan
```

### کتابخانه‌ها و جایگزین‌هایشان

در فهرست زیر، سمت چپ چیزی است که در `FrenchReader` به کار رفته و سمت راست جایگزین چندسکویی آن.

- پایگاه داده: `Room 2.6` جای خود را به `Room 2.7+ KMP` همراه با `BundledSQLiteDriver` می‌دهد. نسخهٔ `Kotlin 2.x` و `KSP2` لازم است.
- شبکه: `HttpURLConnection` جای خود را به `Ktor client` می‌دهد. موتور `OkHttp` روی اندروید و موتور `Darwin` روی iOS به کار می‌رود.
- تجزیهٔ `JSON`: کتابخانهٔ `org.json` با `kotlinx.serialization` جایگزین می‌شود.
- زمان: `java.time` با `kotlinx-datetime` جایگزین می‌شود.
- تنظیمات: `SharedPreferences` با `DataStore KMP` جایگزین می‌شود.
- تجزیهٔ `HTML`: کتابخانهٔ `Jsoup` با `Ksoup` جایگزین می‌شود.
- تصویر: `Coil 2` به `Coil 3` ارتقا پیدا می‌کند.
- ناوبری و `ViewModel`: نسخه‌های `KMP` از `navigation-compose` و `lifecycle-viewmodel` به کار می‌روند.
- منابع و ترجمهٔ رابط: فایل‌های `strings.xml` به `Compose Resources` منتقل می‌شوند و ۱۰۵۳ رشته در `en`، `fa` و `fr` حفظ می‌شود.
- تزریق وابستگی: مثل قبل دستی می‌ماند و فقط یک `AppContainer` در `commonMain` اضافه می‌شود. `Hilt` یا `Koin` لازم نیست.

### الگوی کد مخصوص هر سیستم‌عامل

هر قابلیتی که به سیستم‌عامل وابسته است یک `interface` در `commonMain` دارد، با دو پیاده‌سازی اندروید و iOS.

- برای کدهای ساده از `expect/actual` استفاده می‌شود.
- برای نماهای بومی، کد `Swift` از طریق یک `factory` به `Kotlin` تزریق می‌شود. نماهای بومی شامل `PDFKit`، `WKWebView`، `AVPlayer` و `PencilKit` هستند.

---

## ۲. مدل چندزبانه، مهم‌ترین تغییر

### مفهوم بستهٔ زبان

برای هر زبان یک شیء `LanguageSpec` در `commonMain` تعریف می‌شود. این شیء همهٔ چیزهایی را که امروز در کد به `"fr"` گره خورده‌اند یک‌جا نگه می‌دارد.

```kotlin
data class LanguageSpec(
  val code: String,            // "fr", "en", "fa"
  val script: Script,          // LATIN, ARABIC, ...
  val direction: TextDir,      // LTR / RTL
  val tokenizer: Tokenizer,    // قواعد کلمه، elision فرانسوی، ZWNJ فارسی
  val sentenceSplitter: SentenceSplitter,
  val fold: (String) -> String,           // حذف اعراب/accent برای جستجو
  val dictionaryLinks: List<DictLink>,    // WordReference, b-amooz, Glosbe ...
  val ttsLocale: String, val sttLocale: String,
  val contentSources: List<ContentSourceId>,
)
```

بستهٔ دادهٔ هر زبان، شامل `lemmas` و `idioms` و `decks` و `stories`، دانلودی است و در خود اپ قرار نمی‌گیرد. دلیلش این است که این داده‌ها برای فرانسوی به‌تنهایی حدود ۱۳ مگابایت است.

- این بسته‌ها در `GitHub Releases` با یک `manifest.json` و شمارهٔ نسخه قرار می‌گیرند.
- اپ بدون این بسته‌ها هم کار می‌کند. فقط رنگ‌گذاری سطح کلمه‌ها و درصد فهم متن ضعیف‌تر می‌شود.

### دو محور زبان

- زبان یادگیری: کاربر چند زبان اضافه می‌کند و بین آن‌ها جابه‌جا می‌شود.
- زبان معنی: فارسی یا انگلیسی است و جدا از زبان رابط کاربری تنظیم می‌شود.

جفت‌های نسخهٔ اول:

```
fr→fa, fr→en, en→fa, fa→en
```

### ستون زبان در پایگاه داده

یک ستون `lang` به جدول‌های `texts`، `vocab`، `vocab_lists`، `highlights`، `headlines`، `resources`، `study_session` و `review_log` اضافه می‌شود. همهٔ کوئری‌ها با زبان فعال فیلتر می‌شوند.

### نقاط داغ فرانسوی‌محور که باید پارامتری شوند

این فهرست را عامل بررسی کد پیدا کرده است و همهٔ مسیرها در `FrenchReader` هستند.

- زبان مبدأ ترجمه به‌صورت ثابت `"fr"` است: در `translate/TranslationService.kt:23,46,52,58`، `TranslationRepository.kt:241` و `PreparationProjection.kt:16`. همین‌طور در پرامپت‌های `LlmTranslator.kt:25,39,56`.
- فهرست‌های `elision`: در `ComprehensionAnalyzer.kt:29` و `VocabHighlighter.kt:99`. این فهرست‌ها به `Tokenizer` فرانسوی منتقل می‌شوند.
- کاربردهای `Locale.FRENCH`: در `ComprehensionAnalyzer`، `LemmaLexicon` و `OcrTextCorrector`.
- تابع `isLikelyFrench` در `VocabSrs.kt:35`: نام آن به یک بررسی خط یا `script` تغییر می‌کند.
- عبارت باقاعدهٔ تقسیم جمله در `TextChunker.kt:140`: فقط حروف بزرگ لاتین را می‌شناسد. هر زبان باید یک `splitter` مخصوص خودش بگیرد.
- تنظیمات صدا: صدای `fr-FR-HenriNeural`، زبان `XTTS`، `RecognizerIntent` با مقدار `fr-FR` و مدل `vosk-model-small-fr`.
- لینک‌های دیکشنری در `DictionarySheet.kt:82-117`.
- منابع محتوا: `fr.vikidia`، `fr.wikisource`، `RFI`، `franceinfo` و پارامتر `hl=fr` در یوتیوب.
- محتوای فقط فرانسوی: آزمون‌های `TCF` و یادداشت‌های گرامر. این‌ها بستهٔ اختیاری زبان فرانسه می‌شوند.

### توکن‌سازی فارسی

اگر فارسی زبان یادگیری باشد، این موارد لازم است:

- پشتیبانی از `ZWNJ` یعنی نیم‌فاصله.
- یکسان‌سازی «ي/ی» و «ك/ک».
- حذف اعراب در جستجو.
- چیدمان راست‌به‌چپ متن خواندنی.

---

## ۳. کدهایی که منتقل می‌شوند

### کدهای خالص Kotlin

این فایل‌ها با تغییر `java.time` و `org.json` و `Normalizer` مستقیم به `commonMain` می‌روند. همهٔ مسیرها در `FrenchReaderSpike/app/src/main/java/com/ziaee/frenchreader/` هستند.

- مرور کلمه‌ها: `data/VocabSrs.kt` (لایتنر پنج‌جعبه‌ای با بازه‌های ۱، ۲، ۴، ۸ و ۱۶ روز) و `ui/ReviewQueue.kt` و `ui/ReviewSessionStats.kt`.
- متن: `text/TextChunker.kt`، `SentenceAlignment.kt`، `TextDirectionDetector.kt` و `MarkdownParser.kt`، به‌علاوهٔ `content/VocabHighlighter.kt` و `FrontMatterParser.kt`.
- واژگان: `comprehension/ComprehensionAnalyzer.kt`، که `FrenchLemmaLexicon` در آن به `LemmaLexicon` تغییر نام می‌دهد، و `watch/IdiomMatcher.kt`.
- ترجمه: پارسرهای پاسخ `Google gtx` و `MyMemory`، به‌علاوهٔ `PersianPolish.kt`، `MeaningPolish.kt`، `TranslationCache` و `LlmProvider`/`LlmClient`.
- زیرنویس: `watch/Subtitles.kt`، که پارسرهای `SRT` و `VTT` و `json3` را دارد، و `TranscriptScroll.kt`، `PlaybackFreeze.kt`، `SubtitleVocab.kt` و `YoutubeUrl.kt`.
- PDF: `content/pdf/PdfHitTest.kt`، `PdfReadingProgress.kt`، `OcrRegionFilter.kt` و `OcrTextCorrector.kt`، به‌علاوهٔ `ui/pdf/PdfViewportGeometry.kt`.
- شدوینگ: `shadowing/TranscriptAligner.kt`، `PaceAnalyzer.kt`، `ShadowTextSummary.kt` و منطق `ShadowingController`.
- واردکردن داده: `content/AnkiImport.kt` و `CsvWordImport.kt`.
- آمار و پشتیبان: `ui/statistics/ActivityStreakCalculation.kt` و `StatisticsAggregates.kt`، به‌علاوهٔ `backup/BackupRetention.kt`.

### رابط کاربری

صفحه‌های `Compose` با تغییرات کم منتقل می‌شوند. فقط `android.*` و `Context` و `Toast` حذف می‌شوند. این صفحه‌ها شامل خانه، کتابخانه، خواندن، مرور، فهرست کلمه‌ها، آمار و تنظیمات هستند.

فایل `ReadingScreen.kt` بیش از ۲۰۰۰ خط دارد و هنگام انتقال به چند فایل شکسته می‌شود. تابع `wordBoundsAt` از آن بیرون می‌آید و به `Tokenizer` منتقل می‌شود.

### قواعد مالک پروژه که حفظ می‌شوند

- هیچ منو یا ردیف `chip` با اسکرول افقی ساخته نمی‌شود. گزینه‌ها با `FlowRow` می‌شکنند یا در یک `dropdown` قرار می‌گیرند.
- جهت متن با محتوا تنظیم می‌شود: فارسی راست‌به‌چپ و فرانسوی چپ‌به‌راست، بدون اینکه کل صفحه برعکس شود.
- فونت `Vazirmatn` حفظ می‌شود.
- رنگ‌ها فقط از `semantic tokens` می‌آیند. پالت تازه از روی لوگو ساخته می‌شود: سرمه‌ای تیره و آبی، با حالت روشن و تیره.
- منطق‌ها پیش از ادعای کامل شدن روی گوشی واقعی آزمایش می‌شوند.

---

## ۴. ویژگی‌های وابسته به سیستم‌عامل

### خواندن PDF، اولویت بالا

در `commonMain` این `interface` تعریف می‌شود:

```kotlin
interface PdfDocumentHandle {
  val pageCount: Int
  suspend fun renderPage(index: Int, widthPx: Int): ImageBitmap
  suspend fun words(index: Int): List<PdfWord>   // متن + کادر نرمال‌شده
}
```

- روی اندروید، کد فعلی بدون تغییر منطق می‌ماند: `PdfRenderer` برای رسم صفحه و `pdfbox-android` با `PdfWordLayout` برای موقعیت کلمه‌ها.
- روی iOS، کتابخانهٔ `PDFKit` به کار می‌رود. صفحه با `PDFPage.thumbnail` یا `draw` رسم می‌شود و کلمه‌ها از `page.string` و `characterBounds(at:)` استخراج و به کلمه گروه می‌شوند. این بخش در `Swift` نوشته و به `Kotlin` تزریق می‌شود.
- برای صفحه‌های اسکن‌شده، روی اندروید `ML Kit` نسخهٔ `bundled` به کار می‌رود که بدون سرویس‌های گوگل هم کار می‌کند و برای بازار مناسب است. روی iOS از `Vision` با `VNRecognizeTextRequest` استفاده می‌شود.
- بخش مشترک شامل این موارد است: نمایش صفحه‌ها با `LazyColumn`، زوم از ۱ تا ۴ برابر، لمس کلمه و باز شدن دیکشنری، انتخاب چندکلمه‌ای با لمس طولانی، انتخاب ناحیه برای `OCR` و ذخیرهٔ صفحهٔ آخر.
- ویژگی تازه هایلایت روی `PDF` است، که امروز در `PdfReaderScreen.kt:414` کاری انجام نمی‌دهد. هایلایت‌ها با کادرهای نرمال‌شده در جدول `pdf_highlights` ذخیره می‌شوند.

### ویدیو با زیرنویس

- برای یوتیوب، `YouTube IFrame API` داخل `WebView` اجرا می‌شود: `android.webkit.WebView` روی اندروید و `WKWebView` با `WKScriptMessageHandler` روی iOS. این نماها با `UIKitView` یا `AndroidView` در `Compose` قرار می‌گیرند. پل ارتباطی `WatchBridge` همان پروتکل را دارد: آماده بودن، زمان، وضعیت و خطا.
- زیرنویس‌ها با `YoutubeCaptionFetcher` از `Innertube` دریافت می‌شوند. این کد با `Ktor` به `commonMain` می‌رود و انتخاب تراک بر اساس زبان یادگیری انجام می‌شود.
- ویژگی تازه پخش ویدیوی محلی است، که برای کاربران ایرانی که یوتیوب برایشان فیلتر است مهم است. کاربر فایل `mp4` را همراه با `srt` یا `vtt` وارد می‌کند.
  - روی اندروید از `Media3 ExoPlayer` استفاده می‌شود که از قبل در پروژه هست.
  - روی iOS از `AVPlayer` استفاده می‌شود.
  - در `commonMain` یک `interface` به نام `VideoPlayerController` تعریف می‌شود که `play`، `pause`، `seek` و `positionFlow` دارد.
- لمس کلمه در زیرنویس همان جریان قبلی را دارد: اول اصطلاح‌ها بررسی می‌شوند، بعد کلمه، بعد ویدیو متوقف می‌شود و `DictionaryPane` باز می‌شود.

### صدا یا TTS

- نسخهٔ اول از موتور صدای خود سیستم استفاده می‌کند: `TextToSpeech` روی اندروید و `AVSpeechSynthesizer` روی iOS. این موتورها آفلاین کار می‌کنند و چندزبانه‌اند. برجسته‌کردن جمله با `callback` بازهٔ کلمه انجام می‌شود که روی iOS از `willSpeakRangeOfSpeechString` می‌آید.
- مرحلهٔ بعد پیاده‌سازی پروتکل `edge-tts` با `Ktor WebSocket` در `commonMain` است تا `Chaquopy` حذف شود. خروجی آن `mp3` همراه با `SentenceBoundary` است و همان `TtsCache` را به کار می‌برد.
- کلاینت `XTTS` برای سرور محلی بدون تغییر به `commonMain` می‌رود.

### شدوینگ و تشخیص گفتار

- روی اندروید، `SpeechRecognizer` اندروید و `Vosk` به کار می‌روند و مدل `Vosk` بر اساس زبان یادگیری انتخاب می‌شود.
- روی iOS، `SFSpeechRecognizer` با حالت `requiresOnDeviceRecognition` به کار می‌رود.
- منطق مقایسهٔ متن با گفتار و سنجش سرعت مشترک است.

### سایر قابلیت‌ها

- واردکردن فایل: روی اندروید `ACTION_SEND` و `PROCESS_TEXT` استفاده می‌شوند. روی iOS، `Share Extension` در فاز بعد اضافه می‌شود. انتخاب فایل در هر دو سیستم با `FileKit` یا `expect/actual` انجام می‌شود.
- خواندن `EPUB`: تجزیه با `Ksoup` انجام می‌شود و بازکردن فایل `zip` با `expect/actual`، یعنی `ZipInputStream` روی اندروید و یک کتابخانهٔ `zip` روی iOS.
- پشتیبان‌گیری: فایل `zip` محلی روی هر دو سیستم ساخته می‌شود.
  - روی اندروید، `Google Drive` فقط در نسخهٔ `play` فعال است.
  - روی iOS، فایل با `Files` و `iCloud Drive` به اشتراک گذاشته می‌شود.
- یادآور مرور: روی اندروید از `AlarmManager` موجود و روی iOS از `UNUserNotificationCenter` استفاده می‌شود. زمان یادآور با `nextReviewReminderAt` به‌صورت مشترک محاسبه می‌شود.

### نسخه‌های اندروید

- نسخهٔ `play` شامل `play-services-auth` و `Drive` است.
- نسخهٔ `bazaar` بدون هیچ وابستگی به سرویس‌های گوگل است و برای کافه‌بازار و مایکت استفاده می‌شود.

---

## ۵. فازهای اجرا

هر فاز خروجی قابل‌نصب روی هر دو سیستم دارد.

### فاز صفر: اسکلت پروژه، کوچک

- ساخت پروژهٔ `KMP` با `Compose MP`، نسخه‌های `Kotlin 2.x`، `Room KMP`، `Ktor` و `DataStore`.
- اجرای `iosApp` روی شبیه‌ساز و اجرای `.debug` روی گوشی اندروید.
- راه‌اندازی `CI` با `GitHub Actions`: ساخت اندروید و اجرای `commonTest`، و ساخت iOS روی `macos runner`.
- تعریف `design tokens` از روی لوگو، فونت `Vazirmatn`، و پشتیبانی راست‌به‌چپ و چپ‌به‌راست.
- انتقال رشته‌های `en`/`fa`/`fr` به `Compose Resources`.

### فاز یک: هستهٔ چندزبانه، بزرگ

- تعریف `LanguageSpec` و ثبت سه زبان `fr`، `en` و `fa`، همراه با `Tokenizer` و `SentenceSplitter` و تابع حذف اعراب برای هر زبان.
- طراحی نسخهٔ یک پایگاه داده با ستون `lang`.
- ساخت صفحه‌های کتابخانه، پوشه، برچسب و جستجوی `FTS`.
- ساخت صفحهٔ خواندن متن: لمس کلمه، دیکشنری، ذخیرهٔ کلمه همراه با جمله و هایلایت.
- اتصال سرویس ترجمه به ترتیب `LLM` اختیاری، سپس `Google gtx`، سپس `MyMemory`، با زبان مبدأ پارامتری.
- ساخت مرور لایتنر، فهرست کلمه‌ها، دک‌ها و یادآور روزانه.
- ساخت واردکردن متن از چسباندن، `TXT` و `Markdown`.
- آماده‌سازی بستهٔ زبان: اسکریپت‌های `tools/build_lemmas.py` و `build_idioms.py` با گزینهٔ `--lang` و دادهٔ `kaikki` برای انگلیسی و فارسی بازنویسی می‌شوند.

### فاز دو: خواندن PDF، بزرگ

- پیاده‌سازی `PdfDocumentHandle` برای اندروید با کد فعلی و برای iOS با `PDFKit` در `Swift`.
- لمس کلمه، انتخاب، زوم، ذخیرهٔ پیشرفت و هایلایت روی `PDF`.
- پشتیبانی `OCR` برای صفحه‌های اسکن‌شده با `ML Kit` روی اندروید و `Vision` روی iOS.

### فاز سه: ویدیو و زیرنویس، بزرگ

- پخش یوتیوب با `WebView` روی هر دو سیستم و دریافت زیرنویس بر اساس زبان یادگیری.
- پخش ویدیوی محلی همراه با فایل `SRT` یا `VTT`.
- لمس کلمه در زیرنویس، جستجوی اصطلاح، ذخیرهٔ کلمه با جملهٔ زیرنویس، و فهرست کانال‌ها برای هر زبان.

### فاز چهار: صدا و شدوینگ، متوسط

- صدای سیستم با برجسته‌کردن جمله، سپس پیاده‌سازی `edge-tts` با `WebSocket`.
- شدوینگ روی متن، کارت مرور و ویدیو.

### فاز پنج: تکمیل قابلیت‌ها، متوسط

- واردکردن `EPUB`، عکس با `OCR`، `Anki JSON` و `CSV`.
- آمار، روزهای پیاپی مطالعه و ثبت مطالعه.
- پشتیبان‌گیری محلی، `Drive` و `iCloud`.
- محتوای آماده: اخبار و داستان‌ها برای هر زبان.
- بستهٔ اختیاری فرانسه شامل گرامر و `TCF`.
- واردکردن فایل پشتیبان `FrenchReader` تا کاربران فعلی کلمه‌ها و متن‌هایشان را با `lang=fr` منتقل کنند.

### فاز شش: انتشار، متوسط

- آماده‌سازی نسخه‌های `bazaar` و `play`، فایل `AAB` و امضای انتشار.
- آماده‌سازی `TestFlight` و `App Store`: صفحهٔ حریم خصوصی و `Privacy Manifest`.
- به‌روزرسانی `PRIVACY.md` برای سرویس‌های تازه.

### فاز هفت: قلم اپل و استایلوس، بعداً

- در iOS، یک لایهٔ `PKCanvasView` از `PencilKit` روی هر صفحهٔ `PDF` قرار می‌گیرد. حاشیه‌نویسی دستی با `PKDrawing.dataRepresentation()` برای هر صفحه در جدول `pdf_ink` ذخیره می‌شود.
- با دابل‌تپ قلم، حالت قلم به حالت لمس کلمه تغییر می‌کند. وضعیت ابزار در `PKToolPicker` نگه داشته می‌شود.
- با زدن قلم روی کلمه دیکشنری باز می‌شود. در `Compose`، نوع اشاره‌گر یعنی `PointerType.Stylus` از انگشت جدا می‌شود.
- اختیاری: یک کادر کوچک برای نوشتن دستی کلمه با `Scribble` و جستجوی آن.
- در اندروید، استایلوس در همین فاز با `Compose ink` یا `Canvas` و `PointerType.Stylus` پشتیبانی می‌شود.

---

## ۶. ریسک‌ها

- **حساب توسعه‌دهندهٔ اپل:** حساب `Apple Developer` و انتشار در `App Store` برای ساکنان ایران به‌دلیل تحریم مشکل دارد. پیش از فاز شش باید مسیر حساب و پرداخت روشن شود.
- **قوانین یوتیوب:** دریافت زیرنویس از `Innertube` شکننده است و ممکن است در بررسی `App Store` رد شود. برای همین پخش ویدیوی محلی همراه با `SRT` بخش اصلی طرح است و یوتیوب قابلیتی است که می‌شود خاموشش کرد.
- **سرویس‌های رایگان ترجمه:** سرویس‌های `Google gtx`، `MyMemory` و `edge-tts` رسمی نیستند و ممکن است قطع شوند. هر کدام پشت یک `interface` قرار می‌گیرد و جایگزین دارد.
- **دقت متن PDF در iOS:** کیفیت `characterBounds` در `PDFKit` برای فارسی و متن‌های چندستونه باید در یک آزمایش کوچک در شروع فاز دو بررسی شود.
- **مجوز داده‌ها:** داده‌های `Lexique` و `Wiktionary` نیاز به ذکر منبع با مجوز `CC BY-SA` دارند. مجوز داستان‌ها و دک‌های فعلی ثبت نشده است و پیش از قرار دادن در بستهٔ زبان باید روشن شود.
- **حجم اپ:** بسته‌های زبان دانلودی‌اند و مدل `Vosk` هم مثل امروز هنگام نیاز دانلود می‌شود.

---

## ۷. روش آزمایش

- **آزمون‌های واحد:** همهٔ منطق مشترک آزمون واحد در `commonTest` دارد: لایتنر، `Tokenizer` هر زبان، پارسر زیرنویس، `hit test` در `PDF`، مقایسهٔ گفتار و پارسرهای ترجمه. آزمون‌های فعلی `FrenchReader` همراه کدشان منتقل می‌شوند.

```
./gradlew allTests
./gradlew :composeApp:assembleDebug
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 16' build
```

- **آزمون روی دستگاه:** هر فاز با بستهٔ `.debug` روی گوشی اندروید واقعی و روی شبیه‌ساز iOS آزمایش می‌شود. از فاز دو به بعد آیفون یا آیپد واقعی هم لازم است. بستهٔ اصلی `FrenchReader` روی گوشی دست نمی‌خورد.
- **معیار پذیرش هر فاز:** این سناریوها روی هر دو سیستم اجرا می‌شوند.
  - فاز یک: افزودن متن فرانسوی و انگلیسی، لمس کلمه، ذخیره، و مرور فردا.
  - فاز دو: بازکردن یک `PDF` متنی و یک `PDF` اسکن‌شده، و لمس کلمه در هر دو.
  - فاز سه: پخش یک ویدیوی یوتیوب و یک فایل `mp4` همراه با `srt`، و لمس کلمه در زیرنویس.
- **بازبینی:** پس از هر فاز یک بازبینی کد جداگانه انجام می‌شود.
