# Kotoba

> **La IA escribe con libertad. Kotoba traza el límite.**

**Idiomas:** [índice](../README.md) ·
[English](../../../README.md) ·
[Bahasa Indonesia](../id/README.md) ·
[Basa Jawa](../jv/README.md) ·
[Basa Sunda](../su/README.md) ·
**Español** ·
[עברית](../he/README.md) ·
[한국어](../ko/README.md) ·
[Italiano](../it/README.md) ·
[العربية](../ar/README.md) ·
[الدارجة](../ar-MA/README.md) ·
[المصرية](../arz/README.md) ·
[Deutsch](../de/README.md)

Kotoba es un lenguaje y una pila de cómputo intuitivos, declarativos y
security-first para agentes de IA — y para humanos que vibe-codean con ellos.

**El software existente añade seguridad alrededor del programa. Kotoba hace
de la seguridad una propiedad de toda la computación.**

Esta página de GitHub ayuda a terminar **un trabajo**: instalar la CLI y
compilar un programa admitido. El [README](../../../README.md) inglés sigue
siendo la visión general. Los contratos normativos siguen en archivos
ingleses legibles por máquina.

> **Aviso de traducción.** Traducción automática. Revisión de hablante
> nativo: **no verificada**. El inglés es la fuente.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang` no tiene URL de
> Release de lenguaje. Las etiquetas CLI en
> [`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) son un
> binding de implementación, no esa puerta.

La tarjeta de inicio ya publicada en
[kotoba-lang.org/es/](https://kotoba-lang.org/es/) no se sustituye.

[kotoba-lang.org](https://kotoba-lang.org) · implementación y CLI:
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) ·
[Empezar](getting-started.md)

```sh
brew tap kotoba-lang/kotoba
brew install kotoba
kotoba -e '(+ 1 2)'
```

Esa expresión sigue la misma ruta de compilación admitida que un archivo.
Es conveniencia compile-and-run, no `eval` irrestricto en tiempo de ejecución.

## El trabajo

1. Instalar la CLI (Homebrew o el instalador con checksum).
2. `kotoba selfhost check --json` — aceptar solo una respuesta `valid` con
   la lista de problemas vacía.
3. Compilar `hello.kotoba` y aceptar solo una respuesta `emitted`.

Pasos completos: [Empezar](getting-started.md).

## Lo que esto no es

- El deploy alojado de pago **no** está en vivo. `kotoba deploy` no es hoy
  un análogo comprable de Deno Deploy.
- Kotoba tiene forma de Clojure; no promete que Clojure JVM o ClojureScript
  arbitrarios funcionen sin cambios.
- Esta página no inventa GMV, tracción de clientes ni aceleraciones universales.

Tarjetas de inicio del sitio (sin cambios):
[हिन्दी](https://kotoba-lang.org/hi/) ·
[தமிழ்](https://kotoba-lang.org/ta/) ·
[简体中文](https://kotoba-lang.org/zh-Hans/) ·
[العربية](https://kotoba-lang.org/ar/) ·
[Українська](https://kotoba-lang.org/uk/) ·
[Español](https://kotoba-lang.org/es/) ·
[Français](https://kotoba-lang.org/fr/).
