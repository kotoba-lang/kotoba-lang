# Kotoba

> **الذكاء الاصطناعي بيكتب بحرية. Kotoba بيرسم الحدود.**

**اللغات:** [الفهرس](../README.md) ·
[English](../../../README.md) ·
[Bahasa Indonesia](../id/README.md) ·
[Basa Jawa](../jv/README.md) ·
[Basa Sunda](../su/README.md) ·
[Español](../es/README.md) ·
[עברית](../he/README.md) ·
[한국어](../ko/README.md) ·
[Italiano](../it/README.md) ·
[العربية](../ar/README.md) ·
[الدارجة](../ar-MA/README.md) ·
**المصرية** ·
[Deutsch](../de/README.md)

الصفحة دي بالمصرية (BCP-47 `arz`). اللي بيدوّر على `ar-EG` يستخدم المجلد
ده. العربية الفصحى: [`ar`](../ar/README.md). الدارجة المغربية:
[`ar-MA`](../ar-MA/README.md). بطاقة البداية بتاعة الموقع
[kotoba-lang.org/ar/](https://kotoba-lang.org/ar/) فصحى ومش هتتبدل. فيه
كمان صفحات موقع بالمصرية على
[kotoba-lang.org/arz/](https://kotoba-lang.org/arz/).

Kotoba لغة وستاك حساب حدسي وتصريحي وsecurity-first لوكلاء الذكاء
الاصطناعي — وللناس اللي بيعملوا vibe-code معاهم.

**السوفتوير الموجود بيضيف الأمن حوالين البرنامج. Kotoba بيخلي الأمن خاصية
للحساب كله.**

صفحة GitHub دي بتساعد تخلّص **شغل واحد**: تثبّت CLI وتجمّع برنامج
متفحص. [README](../../../README.md) بالإنجليزي هو النظرة العامة. العقود
المعيارية تفضل ملفات إنجليزي الآلة بتقرأها.

> **ملاحظة الترجمة.** ترجمة آلية. مراجعة متحدث أصلي: **مش متحققة**.
> الإنجليزي هو المصدر.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang` مفيش عنده URL
> لـ Release اللغة. تاجات CLI على
> [`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) ربط تنفيذ،
> مش البوابة دي.

[kotoba-lang.org](https://kotoba-lang.org) · التنفيذ وCLI:
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) ·
[ابدأ](getting-started.md)

```sh
brew tap kotoba-lang/kotoba
brew install kotoba
kotoba -e '(+ 1 2)'
```

التعبير ده ماشي في نفس مسار التجميع المقبول زي الملف. دي راحة
compile-and-run، مش `eval` حر وقت التشغيل.

## الشغل الواحد

1. ثبّت CLI (Homebrew أو المثبّت اللي بيتأكد من checksum).
2. `kotoba selfhost check --json` — اقبل بس رد `valid` وقائمة المشاكل
   فاضية.
3. جمّع `hello.kotoba` واقبل بس رد `emitted`.

الخطوات كاملة: [ابدأ مع Kotoba](getting-started.md).

## ده مش

- النشر المستضاف المدفوع **مش** حي. `kotoba deploy` النهارده مش نظير
  Deno Deploy تقدر تشتريه.
- Kotoba شكلها Clojure؛ مش وعد إن Clojure على JVM أو ClojureScript
  عشوائي هيشتغلوا من غير تغيير.
- الصفحة دي مش بتخترع أرقام GMV أو تبني عملاء أو تسريعات عامة.

كروت بداية الموقع اللي اتنشرت قبل كده (من غير تغيير):
[हिन्दी](https://kotoba-lang.org/hi/) ·
[தமிழ்](https://kotoba-lang.org/ta/) ·
[简体中文](https://kotoba-lang.org/zh-Hans/) ·
[العربية](https://kotoba-lang.org/ar/) ·
[Українська](https://kotoba-lang.org/uk/) ·
[Español](https://kotoba-lang.org/es/) ·
[Français](https://kotoba-lang.org/fr/).
