# Kotoba

> **ה־AI כותב בחופשיות. Kotoba מצייר את הגבול.**

**שפות:** [מפתח](../README.md) ·
[English](../../../README.md) ·
[Bahasa Indonesia](../id/README.md) ·
[Basa Jawa](../jv/README.md) ·
[Basa Sunda](../su/README.md) ·
[Español](../es/README.md) ·
**עברית** ·
[한국어](../ko/README.md) ·
[Italiano](../it/README.md) ·
[العربية](../ar/README.md) ·
[الدارجة](../ar-MA/README.md) ·
[المصرية](../arz/README.md) ·
[Deutsch](../de/README.md)

Kotoba היא שפה ומחסנית חישוב אינטואיטיבית, דקלרטיבית ו־security-first
לסוכני AI — ולבני אדם שכותבים איתם ב־vibe-code.

**תוכנה קיימת מוסיפה אבטחה מסביב לתוכנית. Kotoba הופכת אבטחה למאפיין של
כל החישוב.**

דף GitHub זה עוזר לסיים **משימה אחת**: להתקין את ה־CLI ולהדר תוכנית
שעברה בדיקה. ה־[README](../../../README.md) באנגלית נשאר סקירת הפרויקט.
חוזים נורמטיביים נשארים קבצים באנגלית שקריאים למכונה.

> **הערת תרגום.** תרגום מכונה. ביקורת דובר שפת אם: **לא אומתה**. אנגלית
> היא המקור.

> **Language Release URL: HOLD.** ל־`kotoba-lang/kotoba-lang` אין URL של
> Release שפה. תגי CLI ב־
> [`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba)
> הם קישור מימוש, לא השער הזה.

[kotoba-lang.org](https://kotoba-lang.org) · מימוש ו־CLI:
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) ·
[מתחילים](getting-started.md)

```sh
brew tap kotoba-lang/kotoba
brew install kotoba
kotoba -e '(+ 1 2)'
```

הביטוי הזה עובר באותו נתיב הידור מאושר כמו קובץ. זו נוחות compile-and-run,
לא `eval` חופשי בזמן ריצה.

## המשימה האחת

1. להתקין את ה־CLI (Homebrew או מתקין עם בדיקת checksum).
2. `kotoba selfhost check --json` — לקבל רק תשובת `valid` עם רשימת בעיות
   ריקה.
3. להדר את `hello.kotoba` ולקבל רק תשובת `emitted`.

השלבים המלאים: [מתחילים עם Kotoba](getting-started.md).

## מה זה לא

- פריסה מתארחת בתשלום **אינה** חיה. `kotoba deploy` אינו היום אנלוג
  של Deno Deploy שאפשר לקנות.
- Kotoba בצורת Clojure; אין הבטחה ש־Clojure ל־JVM או ClojureScript
  שרירותיים ירוצו בלי שינוי.
- הדף הזה לא ממציא GMV, טרקשן של לקוחות או האצות אוניברסליות.

כרטיסי התחלה באתר שכבר פורסמו (ללא שינוי):
[हिन्दी](https://kotoba-lang.org/hi/) ·
[தமிழ்](https://kotoba-lang.org/ta/) ·
[简体中文](https://kotoba-lang.org/zh-Hans/) ·
[العربية](https://kotoba-lang.org/ar/) ·
[Українська](https://kotoba-lang.org/uk/) ·
[Español](https://kotoba-lang.org/es/) ·
[Français](https://kotoba-lang.org/fr/).
