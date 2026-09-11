# ابدأ مع Kotoba

**اللغات:** [الفهرس](../README.md) ·
[English](../../getting-started.md) ·
[Bahasa Indonesia](../id/getting-started.md) ·
[Basa Jawa](../jv/getting-started.md) ·
[Basa Sunda](../su/getting-started.md) ·
[Español](../es/getting-started.md) ·
[עברית](../he/getting-started.md) ·
[한국어](../ko/getting-started.md) ·
[Italiano](../it/getting-started.md) ·
[العربية](../ar/getting-started.md) ·
[الدارجة](../ar-MA/getting-started.md) ·
**المصرية** ·
[Deutsch](../de/getting-started.md)

> **ملاحظة الترجمة.** ترجمة آلية للدليل الإنجليزي
> [getting started](../../getting-started.md). مراجعة متحدث أصلي:
> **مش متحققة**. الإنجليزي هو المصدر. الكود والأوامر والعناوين والمعرّفات
> ما اتغيرتش.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang` مفيش عنده URL
> لـ Release اللغة. تاجات CLI على `kotoba-lang/kotoba` مش البوابة دي.

الصفحة دي بالمصرية (`arz`). اللي بيدوّر على `ar-EG` يستخدم المجلد ده.
الفصحى: [`ar`](../ar/getting-started.md). الدارجة:
[`ar-MA`](../ar-MA/getting-started.md). بطاقة
[kotoba-lang.org/ar/](https://kotoba-lang.org/ar/) تفضل فصحى.

المسار ده بيمشي من آلة فاضية لبرنامج متفحص. التثبيت والإصدارات الثنائية
ملك
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba)، مش مستودع
عقد اللغة ده.

## 1. ثبّت CLI الأصلي

على macOS أو Linux مع Homebrew:

```sh
brew tap kotoba-lang/kotoba
brew trust kotoba-lang/kotoba
brew install kotoba
```

Homebrew 6 بيرفض يحمّل formula من tap ما اتقالوش يثق فيه، فمن غير السطر
اللي في النص `brew install` بيقف عند
`Refusing to load formula … from untrusted tap`.

أو المثبّت اللي بيتأكد من checksum والمنشور من مستودع التنفيذ:

```sh
curl -fsSL https://raw.githubusercontent.com/kotoba-lang/kotoba/main/install.sh | sh
```

صفحة الإصدارات هي السلطة للمنصات والقطع المتاحة:
<https://github.com/kotoba-lang/kotoba/releases>.

## 2. اتأكد من التثبيت

```sh
kotoba selfhost check --json
```

اقبل بس رد `valid` وقائمة المشاكل فاضية. التثبيت لوحده مش بيثبت إن مصدر
اتجمّع أو اشتغل.

## 3. ابني ملف مصدر

اعمل `hello.kotoba`:

```clojure
(ns hello (:export [main]))
(defn main [] :i64 (+ 40 2))
```

جمّعه لـ WebAssembly:

```sh
kotoba compile hello.kotoba --target wasm --output hello.wasm --json
```

اقبل بس رد `emitted`. عشان فحص تنفيذ مستقل عن الهوست، اتبع
[AI-agent quickstart](https://kotoba-lang.org/agent-quickstart.md)
المنشور (إنجليزي): حمّل الموديول، ارفض imports مش متوقعة، ونادي `main`
المتصدّر. أمر CLI بتاع الأدابتر ممكن يرجع `planned` / `adapter-required`؛
ده خطة، مش دليل تنفيذ.

الكود الجديد الخاص بـ Kotoba بيستخدم `.kotoba`. المصدر المشترك من عيلة
Clojure بيستخدم `.cljc` وبيختار سلوك Kotoba بـ `#?(:kotoba …)`.

## 4. خلّي الآثار صريحة

مصدر Kotoba مفيش عنده سلطة محيطية على الملفات أو الشبكة أو السر أو الساعة
أو العملية. المكوّن بيعلن الواردات؛ السياسة بتدي مجموعة جزئية محدودة؛
الرن تايم بيربط المزوّدين الممنوحين بس. سياسة فاضية بترفض كل أثر هوست،
بما فيه `:host/http`. ده المنتج للكود مش الموثوق: السلطة اللي متمنحتش
مش بتشتغل.

النشر المستضاف المدفوع للمنح دي لسه مش منتج عام. متعتبرش `kotoba deploy`
نظير Deno Deploy تقدر تشتريه النهارده.

ابدأ من [capability values](../../lang/capability-values.md) (إنجليزي)
قبل ما تكتب كود فيه آثار.

## 5. اعرف حد التوافق

Kotoba شكلها Clojure؛ مش وعد إن Clojure على JVM أو ClojureScript عشوائي
هيشتغلوا من غير تغيير. شوف التصنيف الحالي قبل ما تعتمد على صيغة:

- نظرة بشرية: [language surface matrix](../../lang/surface-matrix.md)
- سلطة الآلة: [`lang/surface-status.edn`](../../../lang/surface-status.edn)
- النحو المقبول: [`lang/guest-grammar.edn`](../../../lang/guest-grammar.edn)

كمّل مع [language reference](../../reference/language.md) و
[tooling reference](../../reference/tooling.md) (الاتنين بالإنجليزي).
