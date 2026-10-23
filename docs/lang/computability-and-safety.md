# Expressiveness, Rice's theorem, and Kotoba's safety boundary

Status: explanatory design note, reviewed 23 October 2026. This note does not
change language semantics or qualify a runtime. [日本語](computability-and-safety.ja.md).
The [blog source](../../site/computability-article.edn) contains the shorter
English and Japanese introduction.

Kotoba combines a high-level programming surface with explicit effect and
authority boundaries. Its checks have defined scope: accepting a component
does not establish arbitrary functional correctness, semantic equivalence, or
successful termination for every input.

| Question | Kotoba's approach | Limit of the claim |
|---|---|---|
| Is useful computation convenient to express? | Collections, records, functions and elaboration into checked primitives | Usability needs evidence from actual authoring tasks |
| Which effects may a component require? | Conservative propagation through a closed call graph | An effect row need not describe the exact effects of every concrete execution |
| May this operation run now? | Intersect compiler evidence, intent, delegation, local policy and runtime availability | Correct enforcement depends on the compiler, host and provider |
| Will this computation finish successfully? | Target-specific resource budgets and host interruption | Forced termination does not prove successful completion |
| Is this the selected definition or result? | Content-addressed identities | Identity does not decide arbitrary program equivalence or correctness |

## Turing tarpit: expressiveness and convenience

Alan Perlis's epigram 54 warns: “Beware of the Turing tar-pit in which everything
is possible but nothing of interest is easy.” This is a language-design warning,
not an undecidability theorem. Computational universality alone says little
about the effort required to write, maintain or review useful programs.

Kotoba's [guest grammar](../../lang/guest-grammar.edn) states that expressiveness
grows through elaboration into existing primitives or declared capability kits.
Its high-level surface includes collections, destructuring, records and
higher-order functions within the supported profile. A small core need not make
authors reconstruct every abstraction from low-level operations.

That is a design response to the tarpit, not proof that it has been avoided.
Assess representative applications, workarounds, diagnostics and the burden of
target differences. Consult the [surface status](../../lang/surface-status.edn)
and [syntax review](../kotoba-syntax-quality-review.md) for actual coverage;
describing a construct does not qualify every backend.

## Rice's theorem: the scope of a checker

Rice's theorem concerns arbitrary programs in a universal computation model:
every nontrivial property of the partial function computed by a program is
undecidable. Here, nontrivial means true of some computed functions and false
of others. There is no algorithm that always terminates with the correct answer
for every program for such a property.

This leaves room for restricted program classes, individual proofs, conservative
analyses and runtime enforcement. A checker can establish a sufficient condition
while rejecting some programs that would behave acceptably. Nor does the theorem
say that every question about source syntax is undecidable.

Kotoba's effect analysis gathers direct effects and propagates capability
effects through calls to a fixed point. This is a finite-set analysis of the
admitted call graph. It does not need to decide exactly which branches will be
reached for every input, or what arbitrary computation means. Local handled
abilities have their own rules; the implementation does not simply union every
kind of effect indiscriminately.

At execution time, the [authority contract](logic-authority.md) requires all of:

1. The operation is statically possible according to admitted compiler evidence.
2. This execution requests the concrete operation.
3. Delegation grants it within the relevant scope.
4. Local policy permits it.
5. The provider and runtime world currently support it.

Only then does the host supply a concrete capability. For example, a correctly
confined component without effective network authority cannot reach the network
provider through that boundary. Enforcing this does not require understanding
every possible purpose of its computation. A granted operation can still be a
poor or harmful choice: the boundary alone does not establish user intent or
business correctness.

The [safety claims](../../lang/safety-claims.edn) name the trusted computing base
and residual risks. Compiler analysis, binding, scope checks and provider
behavior must all implement the contract; a policy formula is not a proof that
every deployment does so. Datalog authorization does not prove compiler
soundness.

## General recursion, finite runs, and termination

Kotoba admits recursion and `loop`/`recur`. The current contracts do not require
a general termination proof for every admitted component. It should therefore
not be described as a total language that proves all programs finish.

Three different questions must stay separate:

- **Idealized expressive power:** whether a formal language model, with stated
  assumptions about memory and resources, can simulate universal computation.
  Recursion or self-hosting alone is not a proof of Turing completeness.
- **A bounded execution:** what a particular artifact, input, target and budget
  can do. Fixing finite input/state domains and an effective bound on every
  transition permits exhaustive bounded analysis in principle, often at
  impractical cost. This does not decide the corresponding unbounded problem.
- **A successful result:** whether the program computes the intended answer
  before a resource limit or supervisor interrupts it.

The [limits authority](../../lang/limits.edn) separates what a program may do
from target-specific budgets and interruption. The [fuel model](fuel-model.md)
contains important qualifications:

- Reference, Wasm and restricted JavaScript profiles describe a default call
  fuel of 512; this is not an instruction or wall-clock budget.
- Synthesized loop helpers have zero-charge self-tail re-entry in the recorded
  KIR/Wasm paths. A finite call-fuel counter alone does not bound those loops.
- The POSIX native kexe profile has deterministic fuel metering off by default.
  Its CPU/wall emergency stop is outside language semantics. Native profiles
  that keep metering on are listed separately in `lang/limits.edn`.
- Provider time, memory, output and instruction interruption need their own
  limits. A host must actually supply and enforce the required mechanism.

An execution returning a resource error or being killed is not evidence that
the original algorithm terminates normally. General statements such as “fuel
proves every Kotoba program terminates” exceed the current contract.

## Content identity and semantic equivalence

The [typed-eval contract](../../lang/typed-eval.edn) distinguishes DefCID,
AdmissionCID and ValueCID. They identify a selected definition, its admitted
execution context and a resulting value respectively. Canonicalization and
content identity make comparison and reproduction useful within their declared
scope; they do not decide whether arbitrary programs compute the same function.
Different definitions can compute the same function. An identified or signed
definition can also contain a logic error.

For the physical costs this design addresses, and the distinction between
IPLD identity, compiler forms and execution layout, see
[physical model and IPLD](physical-model-and-ipld.md).

## Evidence and maintenance

This is a source-reading assessment, not a new runtime test or production
qualification. The language contracts were inspected at
`kotoba-lang/kotoba-lang@1e535060ddfd989fd4866a44b38eea440e03ee67`.
Committed implementation evidence, read independently of local uncommitted
changes, is:

- [Effect inference at kotoba-sema@15e45a3](https://github.com/kotoba-lang/kotoba-sema/blob/15e45a3a37d027142e179332723ebc7d80f0d469/src/kotoba/compiler/frontend/analyze.cljk#L3104-L3196).
- [Loop-helper execution at osaho@8d046ca](https://github.com/kotoba-lang/osaho/blob/8d046cad2207460ff8b881565331d5bb57d91d56/src/kotoba/kir.cljk#L3713-L3739).

These are revision-specific implementation observations, including compatibility
paths. They do not certify the complete selfhost/native toolchain. Revisit this
note when effect inference, recursion, metering, host interruption, value bounds
or content-identity semantics change. Current owner contracts take precedence
over this dated explanation.

Theory references:

- [Alan Perlis, epigram 54, Yale University](https://www.cs.yale.edu/homes/perlis-alan/quotes.html).
- [Boaz Barak, Rice's theorem, Introduction to Theoretical Computer Science](https://introtcs.org/public/lec_08_uncomputability.html#rices-theorem).
