# Kotoba

> **AI는 자유롭게 씁니다. Kotoba가 경계를 긋습니다.**

**언어:** [목록](../README.md) ·
[English](../../../README.md) ·
[Bahasa Indonesia](../id/README.md) ·
[Basa Jawa](../jv/README.md) ·
[Basa Sunda](../su/README.md) ·
[Español](../es/README.md) ·
[עברית](../he/README.md) ·
**한국어** ·
[Italiano](../it/README.md) ·
[العربية](../ar/README.md) ·
[الدارجة](../ar-MA/README.md) ·
[المصرية](../arz/README.md) ·
[Deutsch](../de/README.md)

Kotoba는 AI 에이전트와, 그들과 함께 바이브 코딩하는 사람을 위한 직관적이고
선언적이며 security-first인 언어이자 컴퓨팅 스택입니다.

**기존 소프트웨어는 프로그램 주변에 보안을 덧붙입니다. Kotoba는 보안을
계산 전체의 속성으로 만듭니다.**

이 GitHub 페이지는 **한 가지 일**을 끝내는 데 도움이 됩니다: CLI를 설치하고
검사된 프로그램을 컴파일하기. 영어 [README](../../../README.md)가 전체
개요입니다. 규범 계약은 기계가 읽는 영어 파일에 남습니다.

> **번역 안내.** 기계 번역입니다. 원어민 검토: **미확인**. 영어가 원본입니다.

> **Language Release URL: HOLD.** `kotoba-lang/kotoba-lang`에는 언어
> Release URL이 없습니다.
> [`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba)의 CLI
> 태그는 구현 바인딩이며 그 게이트가 아닙니다.

[kotoba-lang.org](https://kotoba-lang.org) · 구현과 CLI:
[`kotoba-lang/kotoba`](https://github.com/kotoba-lang/kotoba) ·
[시작하기](getting-started.md)

```sh
brew tap kotoba-lang/kotoba
brew install kotoba
kotoba -e '(+ 1 2)'
```

이 식은 파일과 같은 승인된 컴파일 경로를 따릅니다. 컴파일 후 실행 편의이지,
제한 없는 런타임 `eval`이 아닙니다.

## 한 가지 일

1. CLI 설치 (Homebrew 또는 체크섬 검증 설치기).
2. `kotoba selfhost check --json` — 문제 목록이 비어 있는 `valid` 응답만
   수락.
3. `hello.kotoba`를 컴파일하고 `emitted` 응답만 수락.

전체 단계: [시작하기](getting-started.md).

## 이것이 아닌 것

- 호스팅된 유료 배포는 **아직** 공개 제품이 아닙니다. 오늘의
  `kotoba deploy`는 구매 가능한 Deno Deploy 유사 상품이 아닙니다.
- Kotoba는 Clojure 형태이지, 임의의 JVM Clojure나 ClojureScript가 그대로
  실행된다는 약속이 아닙니다.
- 이 페이지는 GMV, 고객 트래션, 보편적 속도 향상을 지어내지 않습니다.

이미 게시된 사이트 시작 카드(변경 없음):
[हिन्दी](https://kotoba-lang.org/hi/) ·
[தமிழ்](https://kotoba-lang.org/ta/) ·
[简体中文](https://kotoba-lang.org/zh-Hans/) ·
[العربية](https://kotoba-lang.org/ar/) ·
[Українська](https://kotoba-lang.org/uk/) ·
[Español](https://kotoba-lang.org/es/) ·
[Français](https://kotoba-lang.org/fr/).
