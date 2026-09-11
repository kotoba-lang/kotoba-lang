# البدء مع Kotoba

**اللغات:** [الفهرس](../README.md) ·
[English](../../getting-started.md) ·
[Bahasa Indonesia](../id/getting-started.md) ·
[Basa Jawa](../jv/getting-started.md) ·
[Basa Sunda](../su/getting-started.md) ·
[Español](../es/getting-started.md) ·
[עברית](../he/getting-started.md) ·
[한국어](../ko/getting-started.md) ·
[Italiano](../it/getting-started.md) ·
**العربية الفصحى** ·
[الدارجة](../ar-MA/getting-started.md) ·
[المصرية](../arz/getting-started.md) ·
[Deutsch](../de/getting-started.md)

> **ملاحظة الترجمة.** ترجمة آلية للدليل الإنجليزي
> [getting started](../../getting-started.md). مراجعة متحدث أصلي:
> **غير متحققة**. الإنجليزية تبقى المصدر. الرمز والأوامر والعناوين
> والمعرّفات لم تُغيَّر.

> **Language Release URL: HOLD.** لا يملك `kotoba-lang/kotoba-lang` عنوان
> Release للغة. وسوم CLI على `kotoba-lang/kotoba` ليست ذلك الباب.

هذه الصفحة بالعربية الفصحى (`ar`). الدارجة المغربية:
[`ar-MA`](../ar-MA/getting-started.md). المصرية / `ar-EG`:
[`arz`](../arz/getting-started.md). بطاقة
[kotoba-lang.org/ar/](https://kotoba-lang.org/ar/) تبقى كما هي.

هذا المسار ينتقل من آلة فارغة إلى برنامج مفحوص. التثبيت والإصدارات
الثنائية ملك
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba)، لا مستودع
عقد اللغة هذا.

## 1. تثبيت CLI الأصلي

على macOS أو Linux مع Homebrew:

```sh
brew tap kotoba-lang/kotoba
brew trust kotoba-lang/kotoba
brew install kotoba
```

Homebrew 6 يرفض تحميل صيغة من tap لم يُطلب منه الوثوق به، لذلك بدون السطر
الأوسط يتوقف `brew install` عند
`Refusing to load formula … from untrusted tap`.

بدلاً من ذلك، المثبّت الذي يتحقق من checksum والمنشور من مستودع التنفيذ:

```sh
curl -fsSL https://raw.githubusercontent.com/kotoba-lang/kotoba/main/install.sh | sh
```

صفحة الإصدارات هي السلطة للمنصات والقطع المتاحة:
<https://github.com/kotoba-lang/kotoba/releases>.

## 2. فحص التثبيت

```sh
kotoba selfhost check --json
```

اقبل فقط استجابة `valid` بقائمة مشكلات فارغة. التثبيت وحده لا يثبت أن
مصدراً جُمّع أو عمل.

## 3. بناء ملف مصدر

أنشئ `hello.kotoba`:

```clojure
(ns hello (:export [main]))
(defn main [] :i64 (+ 40 2))
```

جمّعه لـ WebAssembly:

```sh
kotoba compile hello.kotoba --target wasm --output hello.wasm --json
```

اقبل فقط استجابة `emitted`. لفحص تنفيذ مستقل عن المضيف، اتبع
[AI-agent quickstart](https://kotoba-lang.org/agent-quickstart.md)
المنشور (إنجليزي): حمّل الوحدة، ارفض واردات غير متوقعة، واستدعِ `main`
المُصدَّر. أمر CLI يملكه المحوّل قد يعيد `planned` / `adapter-required`؛
هذه خطة، لا دليل تنفيذ.

الرمز الجديد الخاص بـ Kotoba يستخدم `.kotoba`. المصدر المشترك من عائلة
Clojure يستخدم `.cljc` ويختار سلوك Kotoba بـ `#?(:kotoba …)`.

## 4. اجعل الآثار صريحة

مصدر Kotoba ليست له سلطة محيطية على نظام الملفات أو الشبكة أو السر أو
الساعة أو العملية. يعلن المكوّن الواردات؛ تمنح السياسة مجموعة جزئية
محدودة؛ يربط وقت التشغيل المزوّدين الممنوحين فقط. سياسة فارغة ترفض كل
أثر مضيف، بما فيه `:host/http`. هذا المنتج للكود غير الموثوق: السلطة
غير الممنوحة لا تعمل.

النشر المستضاف المدفوع لتلك المنح ليس منتجاً عاماً بعد. لا تعامل
`kotoba deploy` كنظير Deno Deploy يمكن شراؤه اليوم.

ابدأ من [capability values](../../lang/capability-values.md) (إنجليزي)
قبل كتابة كود ذي آثار.

## 5. اعرف حد التوافق

Kotoba على شكل Clojure؛ ليست وعداً بأن Clojure على JVM أو ClojureScript
اعتباطيين يعملان دون تغيير. افحص التصنيف الحالي قبل الاعتماد على صيغة:

- نظرة بشرية: [language surface matrix](../../lang/surface-matrix.md)
- سلطة الآلة: [`lang/surface-status.edn`](../../../lang/surface-status.edn)
- النحو المقبول: [`lang/guest-grammar.edn`](../../../lang/guest-grammar.edn)

تابع مع [language reference](../../reference/language.md) و
[tooling reference](../../reference/tooling.md) (كلاهما بالإنجليزية).
