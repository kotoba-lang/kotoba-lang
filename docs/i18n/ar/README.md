# Kotoba

> **الذكاء الاصطناعي يكتب بحرية. Kotoba يرسم الحدود.**

**اللغات:** [الفهرس](../README.md) ·
[English](../../../README.md) ·
[Bahasa Indonesia](../id/README.md) ·
[Basa Jawa](../jv/README.md) ·
[Basa Sunda](../su/README.md) ·
[Español](../es/README.md) ·
[עברית](../he/README.md) ·
[한국어](../ko/README.md) ·
[Italiano](../it/README.md) ·
**العربية الفصحى** ·
[الدارجة](../ar-MA/README.md) ·
[المصرية](../arz/README.md) ·
[Deutsch](../de/README.md)

هذه الصفحة بالعربية الفصحى (BCP-47 `ar`). لقراء الدارجة المغربية انظر
[`ar-MA`](../ar-MA/README.md). لقراء المصرية / `ar-EG` انظر
[`arz`](../arz/README.md). بطاقة البداية المنشورة على
[kotoba-lang.org/ar/](https://kotoba-lang.org/ar/) تبقى كما هي.

Kotoba لغة ومكدس حوسبة حدسي وتصريحي وsecurity-first لوكلاء الذكاء
الاصطناعي — وللناس الذين يكتبون معهم بأسلوب vibe-code.

**البرمجيات القائمة تضيف الأمن حول البرنامج. Kotoba يجعل الأمن خاصية
للحساب كله.**

صفحة GitHub هذه تساعد على إنهاء **مهمة واحدة**: تثبيت CLI وتجميع برنامج
مفحوص. يبقى [README](../../../README.md) الإنجليزي النظرة العامة. العقود
المعيارية تبقى ملفات إنجليزية مقروءة آلياً.

> **ملاحظة الترجمة.** ترجمة آلية. مراجعة متحدث أصلي: **غير متحققة**.
> الإنجليزية هي المصدر.

> **Language Release URL: HOLD.** لا يملك `kotoba-lang/kotoba-lang` عنوان
> Release للغة. وسوم CLI على
> [`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) ربط تنفيذ،
> وليست ذلك الباب.

[kotoba-lang.org](https://kotoba-lang.org) · التنفيذ وCLI:
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) ·
[البدء](getting-started.md)

```sh
brew tap kotoba-lang/kotoba
brew install kotoba
kotoba -e '(+ 1 2)'
```

ذلك التعبير يسلك مسار التجميع المقبول نفسه لملف. هذه راحة compile-and-run،
وليست `eval` حرة وقت التشغيل.

## المهمة الواحدة

1. تثبيت CLI (Homebrew أو المثبّت الذي يتحقق من checksum).
2. `kotoba selfhost check --json` — اقبل فقط استجابة `valid` بقائمة
   مشكلات فارغة.
3. جمّع `hello.kotoba` واقبل فقط استجابة `emitted`.

الخطوات الكاملة: [البدء مع Kotoba](getting-started.md).

## ما ليس هذا

- النشر المستضاف المدفوع **ليس** حيّاً. `kotoba deploy` اليوم ليس نظيراً
  لـ Deno Deploy يمكن شراؤه.
- Kotoba على شكل Clojure؛ ليست وعداً بأن Clojure على JVM أو ClojureScript
  اعتباطيين يعملان دون تغيير.
- هذه الصفحة لا تخترع أرقام GMV أو تبنّي زبائن أو تسريعات عامة.

بطاقات بداية الموقع المنشورة سابقاً (دون تغيير):
[हिन्दी](https://kotoba-lang.org/hi/) ·
[தமிழ்](https://kotoba-lang.org/ta/) ·
[简体中文](https://kotoba-lang.org/zh-Hans/) ·
[العربية](https://kotoba-lang.org/ar/) ·
[Українська](https://kotoba-lang.org/uk/) ·
[Español](https://kotoba-lang.org/es/) ·
[Français](https://kotoba-lang.org/fr/).
