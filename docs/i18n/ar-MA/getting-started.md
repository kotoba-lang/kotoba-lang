# بدا مع Kotoba

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
**الدارجة المغربية** ·
[المصرية](../arz/getting-started.md) ·
[Deutsch](../de/getting-started.md)

> **ملاحظة الترجمة.** ترجمة آلية للدليل الإنجليزي
> [getting started](../../getting-started.md). مراجعة متحدث أصلي:
> **ما تحققاتش**. الإنجليزية كتبقى المصدر. الكود والأوامر والعناوين
> والمعرّفات ما تبدّلوش.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang` ما عندوش URL
> ديال Release اللغة. التاغات ديال CLI على `kotoba-lang/kotoba` ماشي هاد
> الباب.

هاد الصفحة بالدارجة المغربية (`ar-MA`). الفصحى:
[`ar`](../ar/getting-started.md). المصرية / `ar-EG`:
[`arz`](../arz/getting-started.md). بطاقة
[kotoba-lang.org/ar/](https://kotoba-lang.org/ar/) كاتبقى فصحى.

هاد الطريق كيمشي من آلة خاوية لبرنامج مفحوص. التثبيت والإصدارات الثنائية
ملك
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba)، ماشي هاد
المستودع ديال عقد اللغة.

## 1. ثبّت CLI الأصلي

على macOS ولا Linux مع Homebrew:

```sh
brew tap kotoba-lang/kotoba
brew trust kotoba-lang/kotoba
brew install kotoba
```

Homebrew 6 كيرفض يحمّل formula من tap ما تقاليهوش يثق فيه، يعني بلا السطر
الوسط `brew install` كيتوقف بـ
`Refusing to load formula … from untrusted tap`.

ولا المثبّت اللي كيتأكد من checksum والمنشور من مستودع التنفيذ:

```sh
curl -fsSL https://raw.githubusercontent.com/kotoba-lang/kotoba/main/install.sh | sh
```

صفحة الإصدارات هي السلطة للمنصات والقطع المتوفرة:
<https://github.com/kotoba-lang/kotoba/releases>.

## 2. تأكد من التثبيت

```sh
kotoba selfhost check --json
```

قبل غير جواب `valid` وقائمة المشاكل خاوية. التثبيت بوحدو ما كيثبتش أن
المصدر تجمّع ولا خدّام.

## 3. صاوب ملف مصدر

صاوب `hello.kotoba`:

```clojure
(ns hello (:export [main]))
(defn main [] :i64 (+ 40 2))
```

جمّعو لـ WebAssembly:

```sh
kotoba compile hello.kotoba --target wasm --output hello.wasm --json
```

قبل غير جواب `emitted`. باش تفحص التنفيذ بلا ما تعتمد على المضيف، تبع
[AI-agent quickstart](https://kotoba-lang.org/agent-quickstart.md)
المنشور (إنجليزي): حمّل الموديل، رفض imports ما متوقعينش، ونادي `main`
المصدَّر. أمر CLI ديال المحوّل يقدر يرجع `planned` / `adapter-required`؛
هادشي خطة، ماشي دليل تنفيذ.

الكود الجديد الخاص بـ Kotoba كيستعمل `.kotoba`. المصدر المشترك من عائلة
Clojure كيستعمل `.cljc` وكيختار سلوك Kotoba بـ `#?(:kotoba …)`.

## 4. خلي الآثار صريحة

مصدر Kotoba ما عندوش سلطة محيطية على الملفات، الشبكة، السر، الساعة، ولا
العملية. المكوّن كيصّرح الواردات؛ السياسة كتعطي مجموعة محدودة؛ وقت
التشغيل كيربط غير المزودين الممنوحين. سياسة خاوية كترفض كل أثر مضيف،
حتى `:host/http`. هاد هو المنتج للكود اللي ما موثوقش: السلطة اللي ما
تعطاتش ما كتخدمش.

النشر المستضاف المدفوع ديال هاد المنح باقي ماشي منتج عام. متعتبرش
`kotoba deploy` نظير Deno Deploy تقدر تشريه اليوم.

بدا من [capability values](../../lang/capability-values.md) (إنجليزي)
قبل ما تكتب كود فيه آثار.

## 5. عرف حد التوافق

Kotoba على شكل Clojure؛ ماشي وعد بأن Clojure على JVM ولا ClojureScript
كيفما كانو غادي يخدمو بلا تغيير. شوف التصنيف الحالي قبل ما تعتمد على صيغة:

- نظرة بشرية: [language surface matrix](../../lang/surface-matrix.md)
- سلطة الآلة: [`lang/surface-status.edn`](../../../lang/surface-status.edn)
- النحو المقبول: [`lang/guest-grammar.edn`](../../../lang/guest-grammar.edn)

كمّل مع [language reference](../../reference/language.md) و
[tooling reference](../../reference/tooling.md) (جوجهم بالإنجليزية).
