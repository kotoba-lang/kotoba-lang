# מתחילים עם Kotoba

**שפות:** [מפתח](../README.md) ·
[English](../../getting-started.md) ·
[Bahasa Indonesia](../id/getting-started.md) ·
[Basa Jawa](../jv/getting-started.md) ·
[Basa Sunda](../su/getting-started.md) ·
[Español](../es/getting-started.md) ·
**עברית** ·
[한국어](../ko/getting-started.md) ·
[Italiano](../it/getting-started.md) ·
[العربية](../ar/getting-started.md) ·
[الدارجة](../ar-MA/getting-started.md) ·
[المصرية](../arz/getting-started.md) ·
[Deutsch](../de/getting-started.md)

> **הערת תרגום.** תרגום מכונה של מדריך
> [getting started](../../getting-started.md) באנגלית. ביקורת דובר שפת אם:
> **לא אומתה**. אנגלית נשארת המקור. קוד, פקודות, כתובות ומזהים לא
> השתנו.

> **Language Release URL: HOLD.** ל־`kotoba-lang/kotoba-lang` אין URL של
> Release שפה. תגי CLI ב־`kotoba-lang/kotoba` אינם השער הזה.

הנתיב הזה מוביל ממכונה ריקה לתוכנית שנבדקה. התקנה ו־release בינאריים
שייכים ל־[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba),
לא למאגר חוזה השפה הזה.

## 1. התקנת ה־CLI המקורי

ב־macOS או Linux עם Homebrew:

```sh
brew tap kotoba-lang/kotoba
brew trust kotoba-lang/kotoba
brew install kotoba
```

Homebrew 6 מסרב לטעון formula מ־tap שלא נאמר לו לבטוח בו, ולכן בלי השורה
האמצעית `brew install` נעצר עם
`Refusing to load formula … from untrusted tap`.

לחלופין, המתקין שמוודא checksum שמפרסם מאגר המימוש:

```sh
curl -fsSL https://raw.githubusercontent.com/kotoba-lang/kotoba/main/install.sh | sh
```

דף ה־release הוא הסמכות לפלטפורמות ולארטיפקטים הזמינים:
<https://github.com/kotoba-lang/kotoba/releases>.

## 2. בדיקת ההתקנה

```sh
kotoba selfhost check --json
```

לקבל רק תשובת `valid` עם רשימת בעיות ריקה. התקנה לבדה לא מוכיחה שמקור
הודר או רץ.

## 3. בניית קובץ מקור

ליצור `hello.kotoba`:

```clojure
(ns hello (:export [main]))
(defn main [] :i64 (+ 40 2))
```

להדר ל־WebAssembly:

```sh
kotoba compile hello.kotoba --target wasm --output hello.wasm --json
```

לקבל רק תשובת `emitted`. לבדיקת הרצה שאינה תלויה במארח, לעקוב אחרי
[AI-agent quickstart](https://kotoba-lang.org/agent-quickstart.md)
המפורסם (אנגלית): לטעון את המודול, לדחות imports לא צפויים, ולקרוא ל־`main`
המיוצא. פקודת CLI של מתאם עשויה להחזיר `planned` / `adapter-required`;
זה תוכנית, לא ראיית הרצה.

קוד חדש רק־Kotoba משתמש ב־`.kotoba`. מקור משותף ממשפחת Clojure משתמש
ב־`.cljc` ובוחר התנהגות Kotoba עם `#?(:kotoba …)`.

## 4. להפוך אפקטים למפורשים

למקור Kotoba אין סמכות סביבתית למערכת קבצים, רשת, סוד, שעון או תהליך.
רכיב מצהיר על imports; מדיניות מעניקה תת־קבוצה מוגבלת; הריצה קושרת רק
ספקים שהוענקו. מדיניות ריקה שוללת כל אפקט מארח, כולל `:host/http`. זה
המוצר לקוד לא מהימן: סמכות שלא הוענקה לא רצה.

פריסה מתארחת בתשלום של אותן הענקות עדיין אינה מוצר ציבורי. אין להתייחס
אל `kotoba deploy` כאנלוג של Deno Deploy שאפשר לקנות היום.

להתחיל ב־[capability values](../../lang/capability-values.md) (אנגלית)
לפני כתיבת קוד עם אפקטים.

## 5. להכיר את גבול התאימות

Kotoba בצורת Clojure; אין הבטחה ש־Clojure ל־JVM או ClojureScript שרירותיים
ירוצו בלי שינוי. לפני הסתמכות על צורה, לבדוק את הסיווג הנוכחי:

- סקירה לבני אדם: [language surface matrix](../../lang/surface-matrix.md)
- סמכות מכונה: [`lang/surface-status.edn`](../../../lang/surface-status.edn)
- דקדוק מאושר: [`lang/guest-grammar.edn`](../../../lang/guest-grammar.edn)

להמשיך עם [language reference](../../reference/language.md) ועם
[tooling reference](../../reference/tooling.md) (שניהם באנגלית).
