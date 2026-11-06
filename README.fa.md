# زبان‌خوان · Zabankhan

اپ مطالعه و یادگیری زبان برای اندروید و iOS، ساخته‌شده با Kotlin Multiplatform و Compose Multiplatform و جانشین French Reader. زبان یادگیری فرانسوی یا انگلیسی است؛ معنی‌ها به فارسی یا انگلیسی نمایش داده می‌شوند.

- خواندن متن، Markdown، EPUB و PDF با ترجمه، دیکشنری، هایلایت و تشخیص متن تصویر.
- ذخیرهٔ واژه و مرور لایتنر، همراه با آمار مطالعه.
- پخش یوتیوب و ویدیوی محلی با زیرنویس قابل‌لمس و تمرین تلفظ.
- دریافت اخبار و مطالب دانشنامه، واردکردن واژه‌های Anki و CSV و پشتیبان محلی.

## ساخت

JDK 17 و Android SDK لازم‌اند. برای iOS به macOS، Xcode و XcodeGen نیاز دارید.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :composeApp:assemblePlayDebug :composeApp:assembleBazaarDebug
./gradlew :composeApp:testPlayDebugUnitTest :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosArm64
./gradlew :composeApp:bundlePlayRelease :composeApp:assembleBazaarRelease
cd iosApp
xcodegen
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Release -destination 'generic/platform=iOS' -derivedDataPath build/rel CODE_SIGNING_ALLOWED=NO build
```

بستهٔ دیباگ پسوند `.debug` دارد. نسخهٔ `bazaar` به نصب Google Play services نیاز ندارد، ولی ML Kit کتابخانه‌های کمکی گوگل و گزارش تشخیصی دارد؛ جزئیات در راهنمای انتشار آمده است. بدون فایل تنظیمات امضا، خروجی انتشار اندروید بدون امضا ساخته می‌شود. نسخهٔ هر دو سیستم از `APP_VERSION_NAME` و `APP_VERSION_CODE` در `gradle.properties` می‌آید؛ پس از تغییر تنظیمات، پروژهٔ Xcode را دوباره با `xcodegen` بسازید.

[راهنمای انتشار](docs/STORE.md) شامل امضا و مراحل فروشگاه‌هاست. [سیاست حریم خصوصی](PRIVACY.md) سرویس‌های آنلاین و محتوای پشتیبان‌ها را توضیح می‌دهد. حساب کاربری و سرور توسعه‌دهنده وجود ندارد.

کلید API در فضای امن دستگاه ذخیره می‌شود، در پشتیبان نیست و پس از بازیابی باید دوباره وارد شود.
