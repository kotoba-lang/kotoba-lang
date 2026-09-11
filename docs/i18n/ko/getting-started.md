# Kotoba 시작하기

**언어:** [목록](../README.md) ·
[English](../../getting-started.md) ·
[Bahasa Indonesia](../id/getting-started.md) ·
[Basa Jawa](../jv/getting-started.md) ·
[Basa Sunda](../su/getting-started.md) ·
[Español](../es/getting-started.md) ·
[עברית](../he/getting-started.md) ·
**한국어** ·
[Italiano](../it/getting-started.md) ·
[العربية](../ar/getting-started.md) ·
[الدارجة](../ar-MA/getting-started.md) ·
[المصرية](../arz/getting-started.md) ·
[Deutsch](../de/getting-started.md)

> **번역 안내.** 영어 [getting started](../../getting-started.md)의 기계
> 번역입니다. 원어민 검토: **미확인**. 영어가 원본입니다. 코드, 명령, URL,
> 식별자는 바꾸지 않았습니다.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang`에는 언어
> Release URL이 없습니다. `kotoba-lang/kotoba`의 CLI 태그는 그 게이트가
> 아닙니다.

이 경로는 빈 머신에서 검사된 프로그램까지 갑니다. 설치와 바이너리 릴리스는
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba)가 소유하며,
이 언어 계약 저장소가 소유하지 않습니다.

## 1. 네이티브 CLI 설치

macOS 또는 Linux에서 Homebrew로:

```sh
brew tap kotoba-lang/kotoba
brew trust kotoba-lang/kotoba
brew install kotoba
```

Homebrew 6는 신뢰하라고 알려주지 않은 tap의 formula를 거부합니다. 가운데
줄이 없으면 `brew install`이
`Refusing to load formula … from untrusted tap`으로 멈춥니다.

또는 구현 저장소가 공개한 체크섬 검증 설치기:

```sh
curl -fsSL https://raw.githubusercontent.com/kotoba-lang/kotoba/main/install.sh | sh
```

사용 가능한 플랫폼과 아티팩트의 권위는 릴리스 페이지입니다:
<https://github.com/kotoba-lang/kotoba/releases>.

## 2. 설치 확인

```sh
kotoba selfhost check --json
```

문제 목록이 비어 있는 `valid` 응답만 수락하세요. 설치만으로는 소스가
컴파일되거나 실행되었음을 증명하지 않습니다.

## 3. 소스 파일 만들기

`hello.kotoba`를 만듭니다:

```clojure
(ns hello (:export [main]))
(defn main [] :i64 (+ 40 2))
```

WebAssembly로 컴파일합니다:

```sh
kotoba compile hello.kotoba --target wasm --output hello.wasm --json
```

`emitted` 응답만 수락하세요. 호스트에 의존하지 않는 실행 확인은 공개된
[AI-agent quickstart](https://kotoba-lang.org/agent-quickstart.md)
(영어)를 따르세요: 모듈을 로드하고, 예상 밖 import를 거부하고, 내보낸
`main`을 호출합니다. 어댑터 소유 CLI 명령은 `planned` /
`adapter-required`를 반환할 수 있습니다. 그것은 계획이지 실행 증거가
아닙니다.

새 Kotoba 전용 코드는 `.kotoba`를 씁니다. 공유 Clojure 계열 소스는
`.cljc`를 쓰고 `#?(:kotoba …)`로 Kotoba 동작을 고릅니다.

## 4. 효과를 명시하기

Kotoba 소스에는 파일시스템, 네트워크, 비밀, 시계, 프로세스에 대한 암묵
권한이 없습니다. 컴포넌트는 import를 선언하고, 정책은 한정된 부분집합을
부여하며, 런타임은 부여된 provider만 연결합니다. 빈 정책은 `:host/http`를
포함해 모든 호스트 효과를 거부합니다. 신뢰할 수 없는 코드를 위한 제품이
그것입니다: 부여되지 않은 권한은 실행되지 않습니다.

그 grant의 호스팅된 유료 배포는 아직 공개 제품이 아닙니다. `kotoba deploy`를
오늘 살 수 있는 Deno Deploy 유사 상품으로 보지 마세요.

효과가 있는 코드를 쓰기 전에
[capability values](../../lang/capability-values.md) (영어)부터 보세요.

## 5. 호환 경계를 알기

Kotoba는 Clojure 형태이지, 임의의 JVM Clojure나 ClojureScript가 그대로
실행된다는 약속이 아닙니다. 어떤 형식에 의존하기 전에 현재 분류를 확인하세요:

- 사람용 개요: [language surface matrix](../../lang/surface-matrix.md)
- 기계 권위: [`lang/surface-status.edn`](../../../lang/surface-status.edn)
- 허용 문법: [`lang/guest-grammar.edn`](../../../lang/guest-grammar.edn)

이어서 [language reference](../../reference/language.md)와
[tooling reference](../../reference/tooling.md) (둘 다 영어).
