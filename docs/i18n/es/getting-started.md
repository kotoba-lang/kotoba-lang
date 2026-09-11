# Empezar con Kotoba

**Idiomas:** [índice](../README.md) ·
[English](../../getting-started.md) ·
[Bahasa Indonesia](../id/getting-started.md) ·
[Basa Jawa](../jv/getting-started.md) ·
[Basa Sunda](../su/getting-started.md) ·
**Español** ·
[עברית](../he/getting-started.md) ·
[한국어](../ko/getting-started.md) ·
[Italiano](../it/getting-started.md) ·
[العربية](../ar/getting-started.md) ·
[الدارجة](../ar-MA/getting-started.md) ·
[المصرية](../arz/getting-started.md) ·
[Deutsch](../de/getting-started.md)

> **Aviso de traducción.** Traducción automática de la guía inglesa
> [getting started](../../getting-started.md). Revisión de hablante nativo:
> **no verificada**. El inglés sigue siendo la fuente. Código, comandos,
> URLs e identificadores no cambian.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang` no tiene URL de
> Release de lenguaje. Las etiquetas CLI en `kotoba-lang/kotoba` no son esa
> puerta.

La tarjeta de inicio en [kotoba-lang.org/es/](https://kotoba-lang.org/es/)
sigue publicada y no se sustituye.

Este camino lleva de una máquina vacía a un programa comprobado.
La instalación y los binarios pertenecen a
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba), no a este
repositorio de contrato de lenguaje.

## 1. Instalar la CLI nativa

En macOS o Linux con Homebrew:

```sh
brew tap kotoba-lang/kotoba
brew trust kotoba-lang/kotoba
brew install kotoba
```

Homebrew 6 se niega a cargar una fórmula de un tap al que no se le ha dicho
que confíe, así que sin la línea del medio `brew install` se detiene con
`Refusing to load formula … from untrusted tap`.

Como alternativa, el instalador que verifica checksum publicado por el
repositorio de implementación:

```sh
curl -fsSL https://raw.githubusercontent.com/kotoba-lang/kotoba/main/install.sh | sh
```

La página de releases es la autoridad de plataformas y artefactos
disponibles:
<https://github.com/kotoba-lang/kotoba/releases>.

## 2. Comprobar la instalación

```sh
kotoba selfhost check --json
```

Acepta solo una respuesta `valid` con la lista de problemas vacía.
Instalar no prueba que un fuente compiló o se ejecutó.

## 3. Construir un archivo fuente

Crea `hello.kotoba`:

```clojure
(ns hello (:export [main]))
(defn main [] :i64 (+ 40 2))
```

Compílalo para WebAssembly:

```sh
kotoba compile hello.kotoba --target wasm --output hello.wasm --json
```

Acepta solo una respuesta `emitted`. Para una comprobación de ejecución
independiente del host, sigue el
[AI-agent quickstart](https://kotoba-lang.org/agent-quickstart.md)
publicado (inglés): carga el módulo, rechaza imports inesperados y llama al
`main` exportado. Un comando CLI del adaptador puede devolver `planned` /
`adapter-required`; eso es un plan, no evidencia de ejecución.

El código solo-Kotoba nuevo usa `.kotoba`. El fuente compartido de la
familia Clojure usa `.cljc` y elige el comportamiento de Kotoba con
`#?(:kotoba …)`.

## 4. Hacer los efectos explícitos

El fuente Kotoba no tiene autoridad ambiente de sistema de archivos, red,
secreto, reloj o proceso. Un componente declara imports; la política
concede un subconjunto acotado; el runtime enlaza solo los proveedores
concedidos. Una política vacía niega todo efecto de host, incluido
`:host/http`. Eso es el producto para código no confiable: la autoridad no
concedida no se ejecuta.

El deploy alojado de pago de esas concesiones aún no es un producto
público. No trates `kotoba deploy` como un análogo de Deno Deploy que se
pueda comprar hoy.

Empieza por [capability values](../../lang/capability-values.md) (inglés)
antes de escribir código con efectos.

## 5. Conocer el límite de compatibilidad

Kotoba tiene forma de Clojure; no promete que Clojure JVM o ClojureScript
arbitrarios funcionen sin cambios. Antes de depender de una forma, mira la
clasificación actual:

- resumen humano: [language surface matrix](../../lang/surface-matrix.md)
- autoridad de máquina: [`lang/surface-status.edn`](../../../lang/surface-status.edn)
- gramática admitida: [`lang/guest-grammar.edn`](../../../lang/guest-grammar.edn)

Sigue con la [referencia de lenguaje](../../reference/language.md) y la
[referencia de tooling](../../reference/tooling.md) (ambas en inglés).
