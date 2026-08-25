# Sealed Hierarchy Audit

Date: 2026-08-23

Scope: documentation-only investigation of Java sealed interfaces/classes as a
follow-up to the reentrancy and owner-state work. No source code was changed.

Representative source-shape commands:

```bash
rg --files | rg '\.java$'
rg --files | rg 'module-info\.java$'
rg -n "extends (Constant|TypeConstant|FrameDependentConstant|TypeInfo|Op|AstNode|BinaryAST|ObjectHandle|ClassTemplate|TypeComposition|Component)\b" javatools/src/main/java
rg -n "implements RegisterInfo|extends DeferredCallHandle|extends CompositionNode|extends CallChain" javatools/src/main/java
```

Important baseline: this repo has no `module-info.java`. In an unnamed module,
sealed direct subclasses must be in the same package as the sealed root. That
makes broad roots such as `org.xvm.asm.Constant`, `org.xvm.asm.Op`,
`org.xvm.runtime.ObjectHandle`, and `org.xvm.runtime.ClassTemplate` awkward
because their natural subclasses are spread across subpackages.

## Executive Summary

Sealed types can help here, but the useful cases are narrow. They do not add
happens-before edges, do not make fields immutable, and do not fix owner-local
lazy caches by themselves. The reentrancy value comes only when sealing prevents
an unsafe owner-bearing subclass or makes a lifecycle state impossible to
construct.

Best first safety candidates:

- `TypeInfo permits TypeInfoReal`: only one direct subclass was found, and
  `TypeInfoReal` is already final with a source comment explaining the
  owner-local constructor invariant.
- `FrameDependentConstant permits RegisterConstant, MethodBindingConstant,
  HandleConstant`: small owner-sensitive family. `HandleConstant` wraps a live
  `ObjectHandle` and already has adoption restrictions.

Best maintainability candidates:

- `CompositionNode` nested subclasses.
- `ConditionalConstant` and `MultiCondition`.
- `BinaryAST` / `ExprAST` after accounting for the existing `NodeType` factory.

Do not start with:

- top-level `Constant`, `Op`, `ObjectHandle`, `ClassTemplate`, or the entire
  compiler `AstNode` tree;
- `RegisterInfo` until the JIT register records are in the same package or the
  build has a named module;
- any generated or bridge classes under `javatools_jitbridge`.

## Candidate Matrix

| Candidate | Classification | What sealed would buy | Reentrancy / memory-model value | Main costs and risks |
| --- | --- | --- | --- | --- |
| `TypeInfo` -> `TypeInfoReal` | Must consider for safety | Prevents anonymous/test/external `TypeInfo` implementations from bypassing `TypeInfoReal` owner-local `MethodInfo`, `PropertyInfo`, and `ChildInfo` construction. | Real owner-confinement value. `TypeInfoReal` is already final because subclassing would expose partially constructed owner metadata. Sealing the abstract root would encode that invariant. | Compile compatibility for any external subclass. Tests currently instantiate `TypeInfoReal` directly, but source-shape checks must confirm no `new TypeInfo` anonymous classes return. |
| `FrameDependentConstant` -> `RegisterConstant`, `MethodBindingConstant`, `HandleConstant` | Must consider for safety | Prevents new frame-dependent constant kinds from appearing without an explicit review of frame owner, runtime handle, serialization format, and adoption behavior. | Real owner/runtime-state value. `HandleConstant` wraps a live `ObjectHandle`; `MethodBindingConstant.getHandle(Frame)` resolves through the current frame; `RegisterConstant` reads frame arguments. | Leaves are public. Making them `final` is the strongest safety shape but can break external subclassing. Making them `non-sealed` lowers safety value. |
| `TypeComposition` -> `ClassComposition`, `PropertyComposition`, `DelegatingComposition` -> `CanonicalizedTypeComposition`, `ProxyComposition` | Must consider for safety, not first | Could make container-owned composition implementations explicit and prevent fake compositions with wrong `Container` ownership. | Real owner-confinement value because every composition answers `getContainer()`, `getTemplate()`, and type access views. | Tests already define a `TestComposition implements TypeComposition`. Any sealing would require test rewrites or an explicit test adapter. This is runtime API surface, so check extension expectations first. |
| `ObjectHandle.DeferredCallHandle` -> `DeferredPropertyHandle`, `DeferredSingletonHandle`, `DeferredArrayHandle` | Must consider for safety when touching deferred runtime flow | The runtime could exhaustively know which deferred handles represent calls/exceptions versus special property, singleton, or array completion paths. | Moderate value. Deferred handles carry frame/exceptions and participate in continuation flow; preventing unknown subclasses makes review easier. | `DeferredCallHandle` is public and used broadly as a return marker. External or future native-template code may subclass it. Verify with source and downstream compatibility before finalizing leaves. |
| `SingletonConstant.InitState` as sealed lifecycle variants | Must consider for safety if that state machine is revisited | Replace nullable fields in one record with explicit `Empty`, `Initializing`, `Completed`, and possibly `Waiting` variants. | Strong JMM/reentrancy value because the state is held in an `AtomicReference`; sealed variants would further reduce impossible lifecycle snapshots. | This is not just adding `sealed` to an existing hierarchy. It is a state-machine refactor and must preserve CAS transitions and singleton stress tests. |
| `TypeConstant` family | Should consider for maintainability | Closes the set of type-constant shapes used by many `instanceof` chains and `ConstantPool` factory paths. Helps future pattern switches and forces every new type constant to declare its relationship to adoption/cache reset rules. | Some safety value because `TypeConstant.setContaining(...)` clears owner-local caches and JIT/runtime helpers. Most existing risk is mutable cache/adoption behavior, not external subclasses. | Large permits graph. Direct/intermediate classes include transient compiler-only `CastTypeConstant` through relational type classes. Diff size and review cost are high. |
| `ConditionalConstant` / `MultiCondition` family | Should consider for maintainability | Closes link-time condition shapes: `NotCondition`, `AllCondition`, `AnyCondition`, `NamedCondition`, `PresentCondition`, `VersionMatchesCondition`, `VersionedCondition`. | Low direct reentrancy value. Conditions are logical constant shapes, mostly useful for exhaustiveness and preventing accidental new condition forms outside the factory paths. | Small risk. Still check deserialization in `ConstantPool` and relation simplification code in `ConditionalConstant`, `AllCondition`, and `AnyCondition`. |
| `BinaryAST` / `ExprAST` / helper expression bases | Should consider for maintainability | Aligns the serialized AST class tree with `BinaryAST.NodeType.instantiate()`, giving clearer exhaustiveness around binary AST decoding/encoding. | Mostly modernization. It helps source-shape correctness for serialized nodes but does not fix shared mutable runtime state. | `NodeType` is not one-to-one with classes: several node types share one class, some are special (`None`, `Escape`, `RegisterExpr`), and `ReturnTStmt` is still TODO in the factory. |
| `CompositionNode` nested subclasses | Should consider for maintainability | Closes the composition clause set: `Extends`, `Annotates`, `Incorporates`, `Implements`, `Delegates`, `Into`, `Import`, `Default`. | Low direct reentrancy value, but useful for type-composition reasoning and impossible-state reduction in compiler code. | Small diff, all in one file. The main risk is future parser extensibility or tests that expect to subclass a composition node. |
| Compiler parser AST roots: `AstNode`, `Expression`, `Statement`, `TypeExpression` | Should consider for maintainability, backlog only | Could separate top-level AST categories and enable future pattern-switch visitors. | Mostly modernization. The parser AST is mutable and request-local; sealing it does not establish confinement or publication safety. | Very large permits lists, many subclasses and inner classes, reflection-based child iteration, and public classes. Not a first wave. |
| `Component` hierarchy | Not worth it / risky for the first wave | Could theoretically encode file/module/class/method/property structure shapes. | Some owner-construction value, but existing fixes already made key roots such as `FileStructure` final and removed constructor-time virtual hooks. | Tests subclass `MethodStructure` and `PropertyStructure` for constructor-escape coverage. The hierarchy is central to deserialization, cloning, conditional siblings, and public/protected constructors. |
| `Constant` root | Not worth it / risky | Would close the full constant universe around `Constant.Format`. | Some safety value in theory, but the practical hazards are adoption, owner caches, live handles, and pool mutation. Sealing the root does not solve those. | Direct subclasses span `org.xvm.asm` and `org.xvm.asm.constants`; no named module means sealing the root is awkward. Very large public API and large diff. Seal same-package subfamilies instead. |
| `Op` root and opcode subclasses | Not worth it / risky | Would mirror the VM opcode table and prevent unknown opcode classes. | Low current safety value. Constructor-shape dispatch was already addressed by explicit opcode shape metadata in bases such as `OpCondJump` and `OpGeneral`. | Direct subclasses span `org.xvm.asm` and `org.xvm.asm.op`; no named module. Many op classes, generated-looking naming conventions, and bytecode factory switches make this high-churn. |
| `ObjectHandle` root | Not worth it / risky | Would close runtime handle kinds. | Potential owner value, but too broad. Real fixes are owner checks, immutable handles, and safe clone/mask behavior. | Many nested and template-specific subclasses across packages. Native templates naturally create specialized handles. Root sealing is likely an extensibility break. |
| `ClassTemplate` root | Not worth it / risky | Would close native/runtime template kinds. | Low as a sealed-root change. Owner-local template tables are the important reentrancy fix. | Native templates and tests extend `ClassTemplate`; this is an intentional extension point inside the runtime. |
| JIT `RegisterInfo` records | Not worth it / risky until package/module work | Would close the register descriptor sum type around `SingleSlot`, `ExtendedSlot`, `MultiSlot`, `Narrowed`, and `Ref`. | Mostly maintainability for JIT code paths that pattern-match register storage. | `RegisterInfo` is in `org.xvm.javajit`, implementors are in `org.xvm.javajit.registers`; no named module. Current source shape blocks cheap sealing. |
| JIT `BuildContext.OpAction` | Not worth it / risky | None; it is a functional interface for lambdas and small action chains. | No reentrancy value. | Sealing would break lambda usage. Leave it open. |

## Reentrancy Versus Modernization

Sealed types are only reentrancy-relevant when they encode one of these facts:

- only owner-safe implementations of an API can exist;
- a constructor cannot be overridden to observe a partially constructed owner;
- a lifecycle state has no nullable/impossible combinations;
- a switch over runtime state is exhaustive, so a new state cannot silently fall
  into a default branch.

Most candidates above are pure modernization. `BinaryAST`, `CompositionNode`,
`ConditionalConstant`, and parser AST roots make code easier to audit, but they
do not create safe publication or prevent data races.

The candidates with real memory-model or owner-state value are narrower:

- `TypeInfo` because `TypeInfoReal` owns copied metadata and is already final for
  construction-safety reasons.
- `FrameDependentConstant` because its subclasses derive runtime handles from a
  `Frame` or wrap a live `ObjectHandle`.
- `TypeComposition` because it is the runtime container/type owner boundary.
- `DeferredCallHandle` subtypes because they represent pending frame work.
- `SingletonConstant.InitState` if converted from nullable record fields into
  sealed state variants held by the existing `AtomicReference`.

Even for those, sealing is not a replacement for `final`, `AtomicReference`,
`ConcurrentMap`, explicit owner parameters, or immutable snapshots. It is a
compile-time guardrail around who is allowed to participate in an invariant.

## Risk Checklist

Use this checklist before any sealed PR:

- Package/module boundary: without `module-info.java`, permitted subclasses must
  be in the same package as the sealed root.
- Extensibility: public/protected roots may be intentionally subclassed by tests,
  runtime templates, downstream tools, or future native integrations.
- Serialization/deserialization: `ConstantPool`, `Op.instantiate(...)`, and
  `BinaryAST.NodeType.instantiate()` already encode closed factories. Sealed
  changes must not desynchronize those tables.
- Reflection: tests, diagnostics, or tools may use subclass scanning,
  `Class::getSuperclass`, anonymous test subclasses, or fake implementations.
- Generated/bridge code: do not seal generated-looking bridge/template classes
  or roots implemented by `javatools_jitbridge` without proving generation and
  recompilation behavior.
- Compile compatibility: every direct subclass must become `final`, `sealed`, or
  `non-sealed`; choosing `final` is an API break even when current repo search is
  clean.
- Diff size: broad roots produce large noisy permits lists and distract from the
  owner-state work.
- Performance: sealed does not provide a performance expectation worth claiming
  here. Treat any JIT optimization as incidental unless benchmarked.

## Safe First PR Shape

A small first PR should seal only one or two owner-sensitive, same-package
families:

1. Seal `TypeInfo` with `permits TypeInfoReal`.
2. Optionally seal `FrameDependentConstant` with permits for `RegisterConstant`,
   `MethodBindingConstant`, and `HandleConstant`.
3. Prefer making permitted leaves `final` only after checking there are no
   source or downstream subclasses. If external compatibility matters, use
   `non-sealed` and record that the safety value is weaker.
4. Add source-shape tests or build checks that fail if new unreviewed subclasses
   appear:

   ```bash
   rg -n "extends TypeInfo|new TypeInfo" javatools/src/main/java javatools/src/test/java
   rg -n "extends FrameDependentConstant" javatools/src/main/java javatools/src/test/java
   ```

5. Add focused reflection assertions if useful:

   ```java
   assertTrue(TypeInfo.class.isSealed());
   assertEquals(List.of(TypeInfoReal.class),
           List.of(TypeInfo.class.getPermittedSubclasses()));
   ```

6. Run focused tests before any broader suite:

   ```bash
   ./gradlew :javatools:test --tests org.xvm.asm.constants.TypeInfoMemberOwnershipTest
   ./gradlew :javatools:test --tests org.xvm.asm.constants.MethodInfoTest
   ./gradlew :javatools:test --tests org.xvm.asm.ConstantAdoptionTest
   ./gradlew :javatools:test --tests org.xvm.runtime.SingletonConstantTest
   ```

Do not run `clean` together with those Gradle tasks.

## What Not To Seal First

Do not start with top-level `Constant`, `Op`, `ObjectHandle`, `ClassTemplate`,
full parser `AstNode`, full `Component`, or JIT `RegisterInfo`. They either span
packages without a named module, have likely extension expectations, or require
large permits lists that would bury the safety review.

Also do not seal around generated or bridge packages first. If a generator owns
the class set, seal the generated output only after the generator is updated and
source-shape tests prove regeneration keeps the permits list correct.

## Exhaustive Cost Analysis And Migration Study (2026-08-24)

The sections above answered "where would sealing be safe". This section answers
the harder question: what does *not* sealing cost today, measured against the
working tree on this date. Every site ranked below was read, not classified from
grep output alone; anything asserted only from a scan is marked SUSPECT. The
worked-example format follows the generics audit
(`generics-api-audit.md`, "Worked Examples" section): current code, the failure
the current shape permits, the sealed rewrite in real Java 21 syntax, and the
payoff line — the exact wrong program that stops compiling.

### Method And Aggregate Counts

Source-shape commands, all over `javatools/src/main/java` unless noted:

```bash
rg --files | rg 'module-info\.java$'          # still empty: unnamed module rules apply
rg -c '\binstanceof\b'                        # 1,549 occurrences in main
rg -n 'switch \([^)]*[gG]etFormat\(\)'        # 141 switch sites on a format discriminator
rg -c 'getFormat\(\) =='                      # 151 direct format equality tests
rg -c 'getFormat\(\) !='                      # 33 more, negated
rg -c 'throw new IllegalStateException'       # 814 hand-written "impossible" throws
rg -ic "shouldn't happen|should never happen|should not happen|cannot happen|unreachable"
                                              # 63 admissions in comments
```

Two custom scans supplement `rg`. A window scan groups `if`/`else if
... instanceof` tests on the same subject within 40 lines into cascades: it
finds **49 cascades of 3+ branches, 8 of 5+** — a deliberate undercount, since
it misses multi-line conditions and mixed-subject ladders (the 500-line format
switches below do not even register as cascades). A brace-matching scan
classifies every switch whose selector mentions `getFormat(`, `f_format`,
`m_format`, `nodeType`, or `getOpCode(`: **167 discriminator switches** total,
with defaults distributed as:

| Default behavior | Count | Meaning when a new subtype/format arrives |
| --- | --- | --- |
| `default:` throws | 102 | runtime failure, at best near the decision |
| no `default` at all | 26 | switch statement silently does nothing; code after it runs on a value the cases never blessed |
| `default` returns a value (`null`/`false`/`true`/other) | 15 | silent wrong answer |
| `default` silently `break`s | 9 | silent fallthrough into the post-switch path |
| `default` assigns and continues ("other") | 15 | usually a misroute; read case by case |

So at **65 of 167 sites (39%)** a forgotten format does not even stop at the
switch: it produces an answer. The 102 throwing defaults are the better class
of failure and still only fire at runtime, on the input that happens to reach
them. Instanceof pressure per family (occurrence counts, main source):

| Family tested by `instanceof` | Occurrences |
| --- | --- |
| `IdentityConstant` tree (incl. `ClassConstant`, `PropertyConstant`, formal constants, pseudo-constants) | 227 |
| `TypeConstant` tree | 181 |
| `ObjectHandle` kinds (incl. template-nested handles) | 154 |
| `Component` kinds | 139 |
| `ValueConstant` tree | 104 |
| `ConditionalConstant` tree | 50 |
| `BinaryAST` tree | 9 |

`Constant.Format` has **107 values** (`Constant.java:768`); `Component.Format`
has **16** (`Component.java:2335`). Both enums are the hand-maintained shadow
of a class hierarchy: nearly every one of the 141 `getFormat()` switches is a
pattern match written by hand, with the compiler unable to check coverage
because 107-way enum coverage is unwritable and the class tree is open.

One more baseline datum: `rg -n '\bsealed (interface|class)'` over
`javatools/src/main/java` returns **nothing**. The toolchain is JDK 25
(`version.properties:26`, `org.xtclang.java.jdk=25`) and 13 files already use
type patterns in `switch` (55 `case Type x` / `case null` lines), so the
syntax is in the house — but with zero sealed roots, none of those switches
can drop its `default` arm and none is exhaustiveness-checked.

### 1. Hierarchy Inventory And Sealability Verdicts

Re-verified on this branch: there is no `module-info.java` in any build
(`rg --files | rg module-info` over the repo, empty), so all javatools code
compiles in the unnamed module and **sealed roots require their direct
subclasses in the same package**. A named module would relax this to
same-module, but note `javatools/build.gradle.kts:193-195` deliberately
strips `module-info.class` from the fat jar ("fat JARs should not be JPMS
modules") — the named-module route is not just adding one file, it collides
with the shipping format. Every verdict below therefore uses the same-package
rule. "Outside the repo" was checked: `lang/` and `manualTests/` contain no
Java sources at all, `javatools_jitbridge` subclasses only its own `n*`/
Ecstasy-bridge types, and the Gradle plugin subclasses nothing in these
families. The only subclassers outside `javatools/src/main` are javatools
tests, itemized in the migration study.

| Family (root package) | Subtypes in main | Package spread | Test subclasses | Sealable today? |
| --- | --- | --- | --- | --- |
| `Constant` root (`org.xvm.asm`) | 89 transitive; 7 direct category roots | root in `asm`, categories in `asm.constants` | 7 direct fakes (`DiagnosticConstant` x2, `RuntimeHandleConstant`, `NoPolicyConstant`, `Blocking/Reentrant/FailingRegistrationConstant`) | **No** — cross-package direct children; seal one tier down instead |
| `TypeConstant` | 21 (11 direct; intermediates `RelationalTypeConstant`, `AbstractDependantTypeConstant`, `AbstractDependantChildTypeConstant`, `IntersectionTypeConstant`→`CastTypeConstant`, `TerminalTypeConstant`→`RecursiveTypeConstant`) | all `org.xvm.asm.constants` | none | **Yes, today** |
| `IdentityConstant` | 15 (5 direct; `NamedConstant` fans out to class/formal/package/typedef/multimethod) | all `asm.constants` | `HookDetectingPropertyConstant` (extends `PropertyConstant`), `HookDetectingFormalTypeChildConstant` (extends `FormalTypeChildConstant`), both `AsmConstructorEscapeTest.java:425/:452` | **Yes**, with `non-sealed` on 2 leaves or test rework |
| `PseudoConstant` | 8 | all `asm.constants` | none | **Yes, today** |
| `ConditionalConstant` | 8 (`MultiCondition` → `All`/`AnyCondition`) | all `asm.constants` | none | **Yes, today** — the cleanest family in the repo |
| `ValueConstant` | 27 | all `asm.constants` | `LatchStringConstant` extends `StringConstant` (`ConstantPoolRegistrationDeadlockTest.java:135`, created on this branch) | **Yes**, with `non-sealed StringConstant` or test rework |
| `FrameDependentConstant` | 3 | `asm.constants` | none | **Yes, today** (already first-PR material above) |
| `BinaryAST` / `ExprAST` | 52 (14 direct stmt-ish + `ExprAST` subtree of 37) | all `org.xvm.asm.ast` | none | **Yes, today** |
| `Op` | 233 (18 shape bases in `org.xvm.asm`, 215 classes in `org.xvm.asm.op`) | **split across two packages** | `TestOp` (`RuntimeTest.java:209`) | **No** — root in `asm`, leaves in `asm.op`; needs a package unification or a named module |
| Compiler AST (`AstNode`) | 96 | 95 in `org.xvm.compiler.ast` + `EvalCompiler.EvalStatement` in `org.xvm.compiler` (`EvalCompiler.java:320`) | none | Technically near-sealable, but one out-of-package subclass, reflection-driven child iteration, and a giant permits list; backlog as already ruled |
| `ObjectHandle` | 88 | **21 packages** | 4 fakes | **No**, and should not be — native templates mint handle kinds by design |
| `ClassTemplate` | 173 | **21 packages** | 2 fakes | **No**, deliberate extension point |
| `TypeComposition` | 5 (`ClassComposition`, `PropertyComposition`, `DelegatingComposition` → `CanonicalizedTypeComposition`, `ProxyComposition`) | `org.xvm.runtime` | `TestComposition` x2 | **Yes**, with a test adapter (matrix row above already flags this) |
| `Component` | 9 (7 direct + `ModuleStructure`/`PackageStructure` under `ClassStructure`) | all `org.xvm.asm` | `HookDetectingMethodStructure`, `CloneableMethodStructure` (extend `MethodStructure`), `HookDetectingPropertyStructure` (extends `PropertyStructure`), `AsmConstructorEscapeTest.java` | **Yes** for the root tier, with `non-sealed` on `MethodStructure`/`PropertyStructure` or test rework |
| `RegisterInfo` (JIT) | 5 records (`SingleSlot`, `ExtendedSlot`, `MultiSlot`, `Narrowed`, `Ref`) | interface in `org.xvm.javajit`, records in `org.xvm.javajit.registers` | none | **No as laid out**; yes after a 1-file package move (root into `registers`, or records up) |
| `Argument` | 3 impls: `Constant`, `Register` (`org.xvm.asm`), `TargetInfo` (`org.xvm.compiler.ast.StatementBlock:1520`) | cross-package | none | **No** without a named module; the union is real (see `NameExpression`) but the interface spans compiler and asm |
| `MethodBody` | 0 subtypes in main | `asm.constants` | `OwnerInspectingMethodBody` (`MethodInfoTest.java:308`, created on this branch) | Could simply be `final` today if the test fake is reworked; sealing is overkill for a 0-child class |

Scanner honesty note: a simple-name collision makes `org.xtclang.ecstasy.reflect.nRef`
look like an `Op` descendant (it implements the *Ecstasy* `Var` interface, not
`org.xvm.asm.op.Var`); it is excluded from the counts above.

The headline: **six families — TypeConstant, IdentityConstant,
PseudoConstant, ConditionalConstant, ValueConstant, FrameDependentConstant —
plus BinaryAST are sealable this afternoon** under the unnamed-module rule,
with at most three `non-sealed` test hatches. Those seven families are exactly
the ones under the heaviest instanceof/format-switch pressure (562 instanceof
sites and the large majority of the 141 format switches). The families that
cannot seal cheaply (`Op`, `ObjectHandle`, `ClassTemplate`) are the ones where
the payoff is lowest anyway: `Op` dispatch is already funneled through two
byte-indexed factory tables, and the runtime template families are open on
purpose.

### 2. The Dispatch-Site Inventory

Everything in this subsection was read in full. The table ranks the fifteen
worst sites by what happens when a subtype is added and the site is forgotten.
"Silent" means no throw on the missed path — the program keeps going with a
wrong answer.

| # | Site | Shape | On a forgotten subtype/format |
| --- | --- | --- | --- |
| 1 | `TerminalTypeConstant.isTuple()` — `TerminalTypeConstant.java:848`, switch at `:857` | 16-case format switch over `getDefiningConstant()`, 4 casts, `default: // let's be tolerant to unresolved constants` | **Silent `return false`.** A new defining-constant format makes every type "not a tuple"; wrong compilation results, no diagnostic |
| 2 | `TerminalTypeConstant.resolveTypeParameter()` — `:779`, tail at `:840-844` | format switch whose last arm is `case FormalTypeChild: case DynamicFormal: // this shouldn't happen break;` then `return null` | **Silent `null`** — type-parameter resolution quietly fails; caller-dependent misbehavior far away |
| 3 | `NamedTypeExpression.calculateDefaultType()` — `NamedTypeExpression.java:755`, default at `:833-836` | 8-case switch over the target constant with `case TypeParameter: default: idTarget = null; break;` | **Silent misroute** — new identity/pseudo format is treated as "not a class target" and resolution continues down an unrelated path |
| 4 | `ClassConstant` namespace walkers — `getParentClass():79`, `getOutermost():102`, `getAutoNarrowingBase():151`, plus 2 depth counters | 5 near-identical format switches where `default` means "packages and modules terminate the search" | **Silent early termination.** The default matches *every* unknown format, not just Module/Package — e.g. a `DecoratedClass` parent would silently become "outermost" |
| 5 | `SimulatedLinkerContext.extractRequiredConditions()` — `SimulatedLinkerContext.java:81-107` | 4-branch instanceof cascade over `ConditionalConstant` with **no final else** | **Silent drop** of any unhandled condition kind (`NotCondition`, `AllCondition`, `AnyCondition` are already unhandled; whether they can reach this map is unproven — SUSPECT — but the swallow shape is real) |
| 6 | `Frame.StackFrame.buildShortName()` — `Frame.java:2090`, default at `:2123` | format switch whose `default` recurses to the parent | **Silent omission** — unknown identity formats vanish from stack-frame names; diagnostics lie by elision |
| 7 | `xConst.createConstHandle()` — `xConst.java:122`, `default: break Literal;` | format switch inside an `instanceof LiteralConstant` block using a labeled break as its default | **Silent misroute** — new literal format exits the `Literal:` block and falls into the generic path below, which was never written for it |
| 8 | `IdentityConstant.getValueType()` — `IdentityConstant.java:652`, default at `:673` | `Component.Format` switch whose `default` is `aAnnos = ((ClassStructure) component).collectAnnotations(true);` | **CCE in the default branch itself** — the default *is* a blind cast; any non-class component format that slips past the `isClass()` convention dies here, method tail is a hand-written `UnsupportedOperationException` |
| 9 | `NameExpression.planCodeGen()` — `NameExpression.java:2321-2845` | one 500-line, 13-format switch with per-case casts (`(ParentClassConstant)`, `(IdentityConstant)`, `(ClassConstant)`, `(PropertyConstant)`, ...), `default: throw new IllegalStateException` | ISE at compile-of-user-code time; loud but late, and every arm launders through casts the compiler cannot check |
| 10 | `NameExpression.getMeaning()` — `:2927` | modern pattern switch over `Argument` whose `Constant` arm re-dispatches on `getFormat()` with an *empty* `default:` falling through — under `@SuppressWarnings("fallthrough")` — to a trailing ISE | ISE at runtime; the suppression exists precisely because the hierarchy is not sealed (see the lint-gate note below) |
| 11 | `StringConstant.apply()` / `CharConstant.apply()` — `StringConstant.java:90`, `CharConstant.java:91` | `switch (op.TEXT + that.getFormat().name())` — dispatch by **string concatenation of an operator and a format name**, 15 cases, casts in every arm | Silent fall to `super.apply(...)`, which eventually throws a generic "invalid op" — constant folding quietly refuses instead of the compiler pointing at the missing pairing |
| 12 | `JumpVal.build()` and per-width builders — `JumpVal.java:395-651`, dispatch at `:422-434` | dispatch on `getSingleUnderlyingClass(true).getName()` — a **string switch over class names** — then per-arm cascades with remote-justified casts (`((ByteConstant) ...)` at `:456`, `:457`, `:469`, justified by the `"Int8", "UInt8"` string case) and the assumption comment `// must be the Null case, which we have already handled` | CCE inside JIT codegen, or a wrong `tableswitch` — **corrupted emitted bytecode** if the assumptions drift |
| 13 | `Builder.checkNull()/checkNotNull()` — `Builder.java:1018/:1043` | instanceof cascade over `RegisterInfo` records whose final `else` is guarded only by `assert !reg.cd().isPrimitive()` | With `-ea` off: emits `if_acmpeq` against a primitive slot — **VerifyError in generated classes**, i.e. corrupted output, discovered only when the generated code loads |
| 14 | `xRTViewFromBitToByte.createBitViewDelegate()` — `xRTViewFromBitToByte.java:41-68`, and the same cascade cloned in `xRTViewFromByteToInt64/Int16/Int8/Float64`, `xRTViewFromBit`, `xRTViewFromBitToBoolean/Nibble`, `ByteBasedBitView` (each 3-5 branches) | handle-kind cascades ending in bare `throw new UnsupportedOperationException()` | UOE at runtime the first time a new delegate kind meets a view; the cascade is **duplicated 9+ times**, so one new handle kind means nine forgotten sites |
| 15 | `xConstrainedInteger.invokeNative1` (`:307-352`) triplicated in `BaseInt128.invokeNative1` (`:259`) and `xIntLiteral.invokeNativeN` (`:300`) | 6-branch template-kind cascade for numeric conversion, cloned three times | Fall through to `super` → generic "unknown native" failure; adding a numeric template means finding all three ladders by memory |

Honorable mentions, all read: `AllCondition.addVersion/removeVersion`
(`AllCondition.java:108-160`) encode the *position* convention "the version is
placed at the end of the list" plus instanceof checks — a sum type simulated
with array order; `ParameterizedTypeConstant.resolveTypeParameter`
(`:570-598`) justifies `(ParameterizedTypeConstant) typeActual` by a labeled
`break Unroll` several lines earlier; `NameResolver.resolvedComponent`
(`NameResolver.java:624`) and `ensurePartiallyResolvedComponent` (`:389`)
re-dispatch `Component.Format` with instanceof-plus-cast bodies;
`InvocationExpression.findCallable` (`InvocationExpression.java:2299-2412`)
falls out of its `MethodConstant`/`PropertyConstant` cascade into
`fIdentityMode = true`, silently reinterpreting any future argument kind as
identity-mode navigation (downstream consequence unproven — SUSPECT);
`TerminalTypeConstant.validate()` (`:2075`, 17 cases) catches the unknown
format only as a `VE_UNKNOWN` *user-facing compile error* — the tool blames
the user's code for the tool's own unhandled case.

The concentration is extreme in one file: `TerminalTypeConstant.java` alone
holds **23 `switch (constant.getFormat())` sites, 47 `getDefiningConstant()`
calls, and 48 `((SomethingConstant) constant)` casts** — one hand-rolled,
unchecked pattern match per public operation, all over the same union
(identity constants + pseudo-constants + formal constants + keyword
constants). This is the single highest-value target in the codebase.

On blind casts justified elsewhere ("laundering" in the generics audit's
vocabulary), the canonical shapes found:

- cast justified by a *different method's* switch: every
  `((ClassConstant) constant)` inside `TerminalTypeConstant` is sound only
  because `isSingleUnderlyingClass(...)` was consulted somewhere upstream;
- cast justified by a *string*: `JumpVal.buildByteSwitch`'s
  `((ByteConstant) constant)` is guaranteed by the `"Int8", "UInt8"` class-
  name string switch at `:423`;
- cast justified by *wire trust*: `BinaryAST.readAST` (`BinaryAST.java:294`)
  does `N node = (N) nodeType.instantiate()` — an unchecked conversion from a
  byte read off disk to whatever the call site hoped for — and
  `readExprAST` (`:382`) hard-casts `(ExprAST) nodeType.instantiate()`, so a
  statement node type in an expression slot is a CCE during deserialization;
- cast in a `default:` branch: `IdentityConstant.getValueType` (`:673`),
  where the fallback path *is* the unchecked assumption.

And on hand-rolled double dispatch: `Constant.apply(Token.Id, Constant)` is a
binary-operator dispatch implemented per-subclass by switching on
`op.TEXT + that.getFormat().name()` — the operand's dynamic type is folded
into a string because the type system was never asked to carry it. A sealed
value-constant tree turns each `apply` into one nested pattern switch and
deletes every cast in it.

### 3. Elimination Proofs

Real Java 21 syntax throughout; no `default` arms, so each rewrite is
exhaustiveness-checked, and each payoff line names what javac now refuses.
Sealing declarations are shown once and reused by later proofs.

#### Proof 1: `ConditionalConstant` — the silent link-time drop

The family (8 subtypes, one package, zero test subclasses) seals with two
lines of change:

```java
public abstract sealed class ConditionalConstant extends Constant
        permits MultiCondition, NamedCondition, NotCondition,
                PresentCondition, VersionMatchesCondition, VersionedCondition { ... }

public abstract sealed class MultiCondition extends ConditionalConstant
        permits AllCondition, AnyCondition { ... }
```

BEFORE (`SimulatedLinkerContext.java:81-107`) — four branches, no else, four
casts:

```java
if (condEach instanceof NamedCondition) {
    ...
    names.add(((NamedCondition) condEach).getName());
} else if (condEach instanceof PresentCondition) {
    ...
    present.put(((PresentCondition) condEach).getPresentConstant(), true);
} else if (condEach instanceof VersionMatchesCondition condModuleVer) {
    ...
} else if (condEach instanceof VersionedCondition) {
    assert version == null;
    version = ((VersionedCondition) condEach).getVersion();
}
```

A `NotCondition` — or any condition kind added next year — is *required* by
the influence map and silently contributes nothing to the simulated context.

AFTER:

```java
switch (condEach) {
case NamedCondition cond          -> ensureNames().add(cond.getName());
case PresentCondition cond        -> ensurePresent().put(cond.getPresentConstant(), true);
case VersionMatchesCondition cond -> ensureModules().put(cond.getModuleConstant(),
                                          cond.getVersionConstant().getVersion());
case VersionedCondition cond      -> version = cond.getVersion();
case NotCondition cond            -> throw new UnsupportedOperationException(
                                          "negated requirement: " + cond);  // explicit decision
case MultiCondition cond          -> cond.conditions().forEach(this::extractRequired);
}
```

PAYOFF. The decision about `NotCondition` is now *written down*, and adding a
ninth condition kind produces, at every consumer simultaneously:

```
SimulatedLinkerContext.java: error: the switch statement does not cover all
    possible input values
```

versus today, where the new kind is dropped from link-time requirement
extraction and nothing ever reports it.

#### Proof 2: `TerminalTypeConstant.isTuple()` — the tolerant lie

The union behind `getDefiningConstant()` is the real prize. Give it a name:

```java
public sealed interface DefiningConstant
        permits IdentityConstant, PseudoConstant, KeywordConstant { }
// IdentityConstant and PseudoConstant are themselves sealed over their
// same-package trees per the inventory above; FormalConstant is a sealed
// intermediate under NamedConstant.
```

and change `getDefiningConstant()` to return `DefiningConstant` instead of
`Constant`.

BEFORE (`TerminalTypeConstant.java:855-901`, abridged) — 16 format cases,
four casts, and the default that answers instead of asking:

```java
Constant constant = getDefiningConstant();
switch (constant.getFormat()) {
case Module: case Package: case IsConst: case IsEnum:
case IsModule: case IsPackage: case IsClass:
    return false;
case NativeClass:
    idClz = ((NativeRebaseConstant) constant).getClassConstant();
    break;
case Class:
    idClz = (ClassConstant) constant;
    break;
case Property: case TypeParameter: case FormalTypeChild: case DynamicFormal:
    return ((FormalConstant) constant).getConstraintType().isTuple();
case ThisClass: case ParentClass: case ChildClass:
    idClz = ((PseudoConstant) constant).getDeclarationLevelClass();
    break;
default:
    // let's be tolerant to unresolved constants
    return false;
}
```

AFTER:

```java
IdentityConstant idClz = switch (getDefiningConstant()) {
    case ModuleConstant ignored    -> null;
    case PackageConstant ignored   -> null;
    case KeywordConstant ignored   -> null;
    case FormalConstant formal     -> { yield null; /* handled below */ }
    case NativeRebaseConstant nrc  -> nrc.getClassConstant();
    case ClassConstant clz         -> clz;
    case PseudoConstant pseudo     -> pseudo.getDeclarationLevelClass();
    ...                            // every remaining permitted subtype, spelled out
};
```

(the formal-constant arm returns `formal.getConstraintType().isTuple()`
directly in the statement form; the sketch shows the shape, not the final
factoring).

PAYOFF. Three things die at once: the four casts (the pattern binds the
subtype), the 16-way format enumeration (categories collapse to their sealed
intermediates — `FormalConstant` is one arm, not four), and the tolerant
default. Add a new defining-constant kind and:

```
TerminalTypeConstant.java: error: the switch expression does not cover all
    possible input values
```

fires in `isTuple` — and in the other **22** format switches in the same file
the moment they are converted, instead of 23 silent or late-throwing sites to
find by hand. This is the multiplier: one sealed union retires 23 hand-rolled
matches and 48 casts in one file.

#### Proof 3: `ClassConstant.getOutermost()` — the walk that stops early

BEFORE (`ClassConstant.java:102-122`):

```java
switch (parent.getFormat()) {
case Class:
    outermost = (ClassConstant) parent;
    break;
case Property:
case Method:
    // ignored (we'll use its parent)
    break;
// packages and modules "terminate" this search
default:
    return outermost;
}
```

The comment says "packages and modules"; the code says "anything I have not
heard of". A `DecoratedClass`, `MultiMethod`, or `Typedef` parent — or the
next identity kind — terminates the walk and yields a wrong "outermost"
class, which then feeds auto-narrowing math (`getAutoNarrowingBase`,
`getDepthFromOutermost`) silently.

AFTER, over sealed `IdentityConstant`:

```java
return switch (parent) {
    case ClassConstant clz          -> { outermost = clz; yield walkUp(); }
    case PropertyConstant ignored,
         MethodConstant ignored     -> walkUp();       // skip, use its parent
    case MultiMethodConstant ignored-> walkUp();       // was: silently "outermost"
    case TypedefConstant ignored    -> walkUp();       //   — forced decisions now
    case ModuleConstant ignored,
         PackageConstant ignored    -> outermost;      // the two the comment meant
    case DecoratedClassConstant dec -> ...;            // must be answered
    case PureIdentityConstant p     -> ...;            // must be answered
    ...
};
```

PAYOFF. Five sibling walkers in the same file (`:79`, `:102`, `:125`, `:151`,
`:173`) share the same silent default; sealing forces each to say which
formats terminate and which pass through, and a new `IdentityConstant` kind
refuses to compile at all five instead of terminating searches early at
whichever ones the author forgets exist.

#### Proof 4: `IdentityConstant.getValueType()` — the cast *is* the default

BEFORE (`IdentityConstant.java:661-689`): the laundering cast and the
hand-maintained throw, both required by today's shape:

```java
switch (component.getFormat()) {
case ENUM:      return pool.ensureParameterizedTypeConstant(pool.typeEnumeration(), type);
case ENUMVALUE: return pool.ensureParameterizedTypeConstant(pool.typeEnumValue(), type);
case PROPERTY:  aAnnos = Annotation.NO_ANNOTATIONS; break;
default:
    aAnnos = ((ClassStructure) component).collectAnnotations(true);   // blind
    break;
}
...
throw new UnsupportedOperationException("constant-class=" + getClass().getSimpleName());
```

AFTER, over sealed `Component` (root tier: `ClassStructure`,
`PropertyStructure`, `MethodStructure`, `MultiMethodStructure`,
`TypedefStructure`, `FileStructure`, `CompositeComponent`, with
`ModuleStructure`/`PackageStructure` under `ClassStructure`):

```java
Annotation[] aAnnos = switch (component) {
    case ClassStructure clz when clz.getFormat() == Format.ENUM      -> ...;
    case ClassStructure clz when clz.getFormat() == Format.ENUMVALUE -> ...;
    case ClassStructure clz       -> clz.collectAnnotations(true);   // typed, checked
    case PropertyStructure ignored-> Annotation.NO_ANNOTATIONS;
    case MethodStructure m        -> throw invalidValueType(m);      // was implicit
    case MultiMethodStructure m   -> throw invalidValueType(m);
    case TypedefStructure t       -> throw invalidValueType(t);
    case FileStructure f          -> throw invalidValueType(f);
    case CompositeComponent c     -> throw invalidValueType(c);
};
```

PAYOFF. This is the requested two-for-one: the rewrite **deletes the
laundering cast** (`(ClassStructure)` becomes a checked pattern binding) and
**deletes the hand-maintained "should never happen" tail** (the
`UnsupportedOperationException` at `:689` becomes per-arm, named, and — the
part that matters — *provably complete*: a new component kind is

```
IdentityConstant.java: error: the switch expression does not cover all
    possible input values
```

not a CCE inside a default branch four layers under `ensureTypeInfo`.

#### Proof 5: `NameExpression.getMeaning()` — the suppressed lint gate

This site shows the interaction with the fatal lint gates this branch already
installed (`org.xtclang.build.java.gradle.kts:58-66` forces
`-Xlint:this-escape -Xlint:fallthrough` with `-Werror`).

BEFORE (`NameExpression.java:2926-2978`):

```java
@SuppressWarnings("fallthrough")
protected Meaning getMeaning() {
    Argument arg = m_arg;
    switch (arg) {
    case null:            return Meaning.Unknown;
    case Register reg:    return ...;
    case TargetInfo ignored: return Meaning.Reserved;
    case Constant constant:
        switch (constant.getFormat()) {
        case Module: case Package: case ThisClass: case ParentClass:
            return Meaning.Class;
        ... 8 more format cases ...
        }
        // fall through
    default:
    }
    throw new IllegalStateException("arg=" + arg);
}
```

The outer switch is already a modern pattern switch — the migration stopped
exactly at the sealed-hierarchy boundary. Because `Constant`'s format space is
open-ended, the inner switch cannot be exhaustive, so the method needs a
deliberate fallthrough, which trips the repo's own fatal
`-Xlint:fallthrough`, which is silenced with `@SuppressWarnings` — a
suppression whose entire reason to exist is the unsealed hierarchy.

AFTER, with sealed `IdentityConstant`/`PseudoConstant` (and `m_arg` narrowed
to the real union it holds):

```java
protected Meaning getMeaning() {
    return switch (m_arg) {
        case null                     -> Meaning.Unknown;
        case Register reg             -> reg.isPredefined() ? ... : Meaning.Variable;
        case TargetInfo ignored       -> Meaning.Reserved;
        case ModuleConstant ignored,
             PackageConstant ignored,
             ThisClassConstant ignored,
             ParentClassConstant ignored -> Meaning.Class;
        case ClassConstant ignored,
             DecoratedClassConstant ignored ->
                m_plan == Plan.TypeOfClass ? Meaning.Type : Meaning.Class;
        case PropertyConstant ignored -> Meaning.Property;
        case FormalTypeChildConstant ignored -> Meaning.FormalChildType;
        case MethodConstant ignored,
             MultiMethodConstant ignored -> Meaning.Method;
        case TypedefConstant ignored  -> Meaning.Type;
        ...
    };
}
```

PAYOFF. The `@SuppressWarnings("fallthrough")`, the empty `default:`, and the
trailing `IllegalStateException` all disappear; the lint gate goes back to
meaning something at this site; and a new identity kind is a compile error
naming this method — instead of an ISE thrown mid-compilation of *user* code.

#### Proof 6: `BinaryAST` — the wire format with a TODO in its factory

BEFORE. `NodeType.instantiate()` (`BinaryAST.java:167-253`) maps 80 enum
values to 52 classes with `default -> throw new
UnsupportedOperationException("nodeType: " + this)` — and the mapping is
*known incomplete today*: `// TODO case ReturnTStmt ->` sits above that
default. The read boundary trusts it blindly (`:294-296`, `:382`):

```java
N node = (N) (NodeType.valueOf(in.readUnsignedByte())).instantiate();
...
ExprAST node = (ExprAST) nodeType.instantiate();
```

A `ReturnTStmt` byte in a stream is a runtime UOE; a statement byte where an
expression is expected is a CCE; both surface during module deserialization
with no hint which writer produced the byte.

AFTER. Seal the tree in place (all 52 classes share `org.xvm.asm.ast`):

```java
public abstract sealed class BinaryAST
        permits ExprAST, AssertStmtAST, BreakStmtAST, ContinueStmtAST,
                DoWhileStmtAST, ForEachStmtAST, ForStmtAST, IfStmtAST,
                InitAST, LoopStmtAST, ReturnStmtAST, StmtBlockAST,
                TryCatchStmtAST, TryFinallyStmtAST, WhileStmtAST { ... }

public abstract sealed class ExprAST extends BinaryAST
        permits ArrayAccessExprAST, AssignAST, BiExprAST, ... { ... }
```

then make `instantiate()` a switch expression with **no default**: 80 enum
constants, each mapped or explicitly rejected
(`case Escape, RegisterExpr -> throw ...` stays, as those are wire-encoding
artifacts, but now it is a *listed decision*, not a basket).

PAYOFF. Adding `NodeType.ReturnTStmt`'s implementation — or any new node —
without touching the factory stops compiling:

```
BinaryAST.java: error: the switch expression does not cover all possible
    input values
```

and sealing `ExprAST` documents at the type level which nodes may legally
appear in expression position, turning the `:382` cast into a checked
pattern (`case ExprAST expr -> expr; case BinaryAST stmt -> throw
corruptStream(stmt)`) — a *deliberate* corrupt-stream diagnostic instead of an
incidental CCE.

#### Proof 7: `Builder.checkNull()` — sealed records or corrupted bytecode

BEFORE (`Builder.java:1018-1035`):

```java
if (reg instanceof ExtendedSlot extSlot) {
    assert reg.cd().isPrimitive();
    code.iload(extSlot.extSlot()).ifne(lblNull);
} else if (reg instanceof MultiSlot multiSlot) {
    assert reg.type().removeNullable().isXvmPrimitive();
    code.iload(multiSlot.extSlot()).ifne(lblNull);
} else {
    assert !reg.cd().isPrimitive();
    reg.load(code);
    loadNull(code);
    code.if_acmpeq(lblNull);
}
```

The final `else` handles `SingleSlot`, `Narrowed`, *and* `Ref` — and every
future register kind — on the strength of an `assert`. With assertions
disabled, a new primitive-carrying register kind gets `if_acmpeq` emitted
against a primitive slot: the failure is a `VerifyError` (or worse, silently
wrong null semantics) in *generated* classes, diagnosed from the emitted
bytecode backwards.

AFTER. Move `RegisterInfo` into `org.xvm.javajit.registers` beside its five
implementations (one-file move; the interface has no other same-package
dependents) and seal it:

```java
public sealed interface RegisterInfo
        permits SingleSlot, ExtendedSlot, MultiSlot, Narrowed, Ref { ... }

public static void checkNull(CodeBuilder code, RegisterInfo reg, Label lblNull) {
    switch (reg) {
    case ExtendedSlot(var bctx, var regId, var slot, var extSlot, ...) ->
        code.iload(extSlot).ifne(lblNull);
    case MultiSlot ms   -> code.iload(ms.extSlot()).ifne(lblNull);
    case SingleSlot ss  -> refCheck(code, ss, lblNull);
    case Narrowed n     -> refCheck(code, n, lblNull);
    case Ref r          -> refCheck(code, r, lblNull);
    }
}
```

PAYOFF. The three asserts become structure; record patterns destructure the
slot directly; and a sixth register representation refuses to compile at
`checkNull`, `checkNotNull`, the sibling ladder at `Builder.java:961-966`,
and the assert-guarded slot rewrites at `BuildContext.java:2278-2318` in the
same sweep — versus today's failure mode of shipping a JIT that emits
unverifiable classes.

### 4. Incremental Migration Study

**Compatibility shape.** Adding `sealed`/`permits` is invisible to callers:
no descriptor changes, existing binaries that merely *use* these classes link
unchanged. It is breaking only for subclassers — at compile time for source,
and at class-load time (`IncompatibleClassChangeError`) for any stale
pre-compiled subclass not in the permits list. The inventory above establishes
that the subclasser set is: javatools main (in-package), javatools tests, and
nobody else — no Java in `lang/` or `manualTests/`, nothing in the plugin,
nothing in `javatools_jitbridge` touching these families. So each family
migrates independently, one PR each, with no cross-build coordination.

**The test-fake ledger.** This branch's own hardening work manufactured most
of the current fakes, and they are the only source-level blockers:

| Test fake | Extends | Blocks | Fix |
| --- | --- | --- | --- |
| `DiagnosticConstant` x2, `RuntimeHandleConstant`, `NoPolicyConstant`, `BlockingRegistrationConstant`, `ReentrantRegistrationConstant`, `FailingRegistrationConstant` (`ConstantAdoptionTest`, `ConstantPoolDiagnosticsTest`) | `Constant` directly | only sealing the `Constant` *root*, which is off the table anyway (cross-package) | none needed for the recommended plan |
| `LatchStringConstant` (`ConstantPoolRegistrationDeadlockTest.java:135`) | `StringConstant` | making `StringConstant` `final` in the ValueConstant wave | declare `StringConstant` `non-sealed`, or re-express the latch as a pool-level test hook |
| `HookDetectingPropertyConstant` / `HookDetectingFormalTypeChildConstant` (`AsmConstructorEscapeTest.java:425/:452`) | `PropertyConstant` / `FormalTypeChildConstant` | finalizing those two leaves in the IdentityConstant wave | `non-sealed` on both, with a comment noting the safety value forgone; the constructor-escape probes cannot move in-package because a permitted subclass must be compiled with the sealed class, and test sources are a separate compilation |
| `OwnerInspectingMethodBody` (`MethodInfoTest.java:308`) | `MethodBody` | making `MethodBody` `final` | keep `MethodBody` open (it has zero main subclasses; `final` is desirable but this fake must be reworked first) |
| `HookDetectingMethodStructure`, `CloneableMethodStructure`, `HookDetectingPropertyStructure` (`AsmConstructorEscapeTest.java`) | `MethodStructure` / `PropertyStructure` | finalizing those leaves in the Component wave | `non-sealed` on both structures |
| `TestOp` (`RuntimeTest.java:209`) | `Op` | Op-root sealing (last stage regardless) | trivial rework or `non-sealed` base |
| `TestComposition` x2, `TestHandle`, `NonSharedHandle`, `TestRefHandle`, `RegisterBoundProbe`, `TestTemplate`, `NullOwnerTemplate` | `TypeComposition` / `ObjectHandle` / `ClassTemplate` | TypeComposition sealing only; the other two families stay open by design | test adapter for `TypeComposition` per the matrix row above |

Note the structural fact underlying the whole column: **a permitted subclass
cannot live in the test source set.** Sealing therefore always chooses between
`non-sealed` leaf hatches (weakest, but honest and greppable) and reworking
the fake. Every fake above was added deliberately on this branch; each sealed
wave should decide fake-by-fake and record the decision in this document.

**Package/module constraint recap per family.** Same-package rule satisfied
today: TypeConstant, IdentityConstant, PseudoConstant, ConditionalConstant,
ValueConstant, FrameDependentConstant (all `org.xvm.asm.constants`); BinaryAST
(`org.xvm.asm.ast`); Component (`org.xvm.asm`); TypeComposition
(`org.xvm.runtime`). Requires code movement or a named module: `Constant`
root (root in `asm`, categories in `asm.constants`), `Op` (root+bases in
`asm`, 215 leaves in `asm.op`), `RegisterInfo` (interface in `javajit`,
records in `javajit.registers` — cheapest move in the repo), `Argument`
(spans `asm` and `compiler.ast`). The named-module route would unlock all
four at once but collides with the fat-jar packaging decision at
`javatools/build.gradle.kts:193-195`; do not pay that cost for `Op` alone —
the byte-indexed factory (`Op.instantiate`, `Op.java:1351`, 215 cases,
`default:` throws `IllegalStateException("op=..")` at `:1592`) already fails
loudly at load time, which is the least dangerous failure mode in this whole
document.

**Lint-gate interplay.** The build already runs `-Werror` with
`-Xlint:fallthrough` and `-Xlint:this-escape` re-enabled on top of the
default set (`org.xtclang.build.java.gradle.kts:51-66`). Sealing compounds
with this in three ways: (1) exhaustive pattern switches need no fallthrough
and no empty defaults, so suppressions like `NameExpression.java:2926` are
deleted rather than audited; (2) a new subtype turns every non-exhaustive
switch into a hard error *under the existing -Werror regime with zero new
build machinery* — the gate is javac itself; (3) the source-shape checks
proposed in "Safe First PR Shape" above become redundant for sealed families
(the compiler enforces the permits list), and only remain necessary for
`non-sealed` hatches, which is exactly the greppable residue one wants.

**Staged order and effort.** Smallest closed family first as the proof, then
by payoff-per-diff. Estimates are focused engineering days including test
rework and running the focused suites listed in "Safe First PR Shape"; they
assume the family's dispatch sites are converted opportunistically (sealing
does not *require* converting all 141 switches at once — each conversion is
independently safe and independently valuable).

| Stage | Family | Diff shape | Effort |
| --- | --- | --- | --- |
| 0 | `TypeInfo`, `FrameDependentConstant` (already specified above), plus `ConditionalConstant` + `PseudoConstant` | ~22 permits clauses, Proof 1 rewrite as the demonstrator, `SimulatedLinkerContext` + `AllCondition` sites | 2-3 days |
| 1 | `BinaryAST`/`ExprAST` | 52 classes, one package; `NodeType.instantiate` to no-default switch; typed `readExprAST` boundary (Proof 6) | 4-5 days |
| 2 | `TypeConstant` | 21 permits; convert the relational/terminal instanceof sites (181 occurrences, most trivial pattern bindings) | 1-2 weeks |
| 3 | `IdentityConstant` + `ValueConstant` + the `DefiningConstant` union | the big one: `TerminalTypeConstant`'s 23 switches / 48 casts, `NameExpression.planCodeGen`/`getMeaning`, `NamedTypeExpression.calculateDefaultType`, `ClassConstant` walkers, `StringConstant/CharConstant.apply`; 2 `non-sealed` hatches | 2-3 weeks, divisible by file |
| 4 | `Component` + `TypeComposition` | 9 + 5 permits, 2 `non-sealed` structures, test adapter; Proof 4 sites | ~1 week |
| 5 | `RegisterInfo` (anytime — 1-2 days after the package move, Proof 7), then `Op` only if/when a package unification or module decision happens independently | `Op` churn is ~233 files of `final`/`sealed` markers for review-only value | days (RegisterInfo); do not schedule `Op` on its own merits |

**What must not be sealed.** Unchanged from the matrix above, now with the
inventory numbers behind it: `ClassTemplate` (175 subtypes, 21 packages — the
native-template extension point), `ObjectHandle` root (92/21 — handle kinds
are per-template by design), `DeferredCallHandle` leaves until the downstream
compatibility check in the matrix row is done, `BuildContext.OpAction`
(functional interface), the compiler `AstNode` tree (96 subtypes plus the
out-of-package `EvalCompiler.EvalStatement` at `EvalCompiler.java:320`, plus
reflection-driven child iteration — backlog stays backlog), and anything
under `javatools_jitbridge`.

### 5. Verdict Table

| Family | Subtypes (main) | Sealable today | instanceof + discriminator-switch pressure | Worst forgotten-subtype failure | Effort |
| --- | --- | --- | --- | --- | --- |
| `ConditionalConstant` | 8 | **yes** | 50 + a handful of switches | required link condition silently dropped (`SimulatedLinkerContext.java:81`) | trivial |
| `FrameDependentConstant` | 3 | **yes** | few, owner-sensitive | unreviewed frame/handle constant kind | trivial |
| `PseudoConstant` | 8 | **yes** | shares the 227 identity-tree sites | silent misroutes in auto-narrowing paths | trivial |
| `BinaryAST`/`ExprAST` | 52 | **yes** | 9 instanceof + the NodeType factory | UOE/CCE during module deserialization; `ReturnTStmt` hole is live today (`BinaryAST.java:239`) | small |
| `TypeConstant` | 21 | **yes** | 181 instanceof + 6 type-format switches | silent wrong answers (`isTuple` false, `resolveTypeParameter` null) feeding codegen | medium |
| `IdentityConstant` | 15 | yes + 2 `non-sealed` | 227 instanceof; most of the 141 `Constant.Format` switches discriminate this tree plus the pseudo/formal constants | silent early termination / `idTarget = null` misroutes; CCE-in-default | large, highest payoff |
| `ValueConstant` | 27 | yes + 1 `non-sealed` | 104 instanceof; `apply` string-dispatch | constant folding silently refuses; JIT switch corruption (`JumpVal`) | medium |
| `Component` | 9 | yes + 2 `non-sealed` | 139 instanceof + ~25 Component.Format switches | CCE in default branches (`IdentityConstant.getValueType`) | small-medium |
| `TypeComposition` | 5 | yes + test adapter | moderate | fake compositions with wrong container ownership | small |
| `RegisterInfo` | 5 | after 1-file move | JIT builders | **corrupted emitted bytecode** (`Builder.checkNull`) | trivial after move |
| `Op` | 233 | no (package split) | 2 x 215-case factory tables | loud ISE at load — least urgent | large, defer |
| `Argument` | 3 | no (cross-package) | `NameExpression` ladders | silent identity-mode reinterpretation (SUSPECT) | needs module decision |
| Compiler `AstNode` | 96 | near (1 stray subclass) | heavy but request-local | compile-time NPEs/misroutes, user-visible errors | backlog |
| `ObjectHandle` / `ClassTemplate` | 88 / 173 | no, by design | 154 instanceof | n/a — keep open | do not seal |

**How this compounds with the typed-boundary work.** The generics audit and
this study are the same argument at two different joints. The typed-boundary
work stops *payload* lies between components: a `CompletableFuture` that
promises `ObjectHandle[]` and delivers a `TupleHandle` becomes unwritable.
Sealing stops *shape* lies within a component: "this constant is always a
`ClassConstant` here" stops being a comment plus a cast plus one of 814
`IllegalStateException`s, and becomes a pattern arm the compiler audits at
every switch, every time the hierarchy changes. Both are the same left shift:
converting conventions that today live in 63 "shouldn't happen" comments and
in reviewers' heads into obligations javac enforces on whoever makes the next
change. For a fork, that last clause is the whole point. Upstream adds a
`TypeConstant` subtype or a `Constant.Format` value; today this fork finds
out via a tolerant `default` returning `false` somewhere under
`ensureTypeInfo`, three test suites later — or never. With the seven
cheaply-sealable families sealed, the rebase itself enumerates every dispatch
site that needs a decision, as compile errors with file and line, before
anything runs. The maintenance cost of divergence drops from "re-audit 1,549
instanceof sites and 167 switches by memory" to "answer the compiler's list",
which is the only version of that cost that stays paid.

### Stage 0 Landed (2026-08-24)

Implemented on this branch, same day, as a separately submittable unit: the
four proof families are sealed - `ConditionalConstant` (permits its six direct
kinds; `MultiCondition` sealed over `AllCondition`/`AnyCondition`),
`PseudoConstant` (eight leaves), `FrameDependentConstant` (three leaves), and
`TypeInfo permits TypeInfoReal` - with every leaf made `final` (23 files, all
modifier-only). `SimulatedLinkerContext.extractRequiredConditions` is the
demonstrator rewrite: the four-branch instanceof cascade with four casts and a
silent drop became an exhaustive pattern switch with no default; the
previously-invisible `NotCondition`/`MultiCondition` drops are now written
arms. `SealedConstantFamiliesTest` pins each permits list and the
final-or-sealed leaf discipline by reflection. Verified: full
`:javatools:test` suite and `xdk:installDist` green.

What javac now refuses (real output, captured against the sealed classes):

```
error: class is not allowed to extend sealed class: ConditionalConstant
    (as it is not listed in its 'permits' clause)
```

```
error: cannot inherit from final NotCondition
```

and removing any arm from the demonstrator switch:

```
error: the switch expression does not cover all possible input values
```

### Stages 1-4 Landed (2026-08-24, same day)

The remaining waves shipped as separate commits the same day, each gated by
the full javatools suite and `xdk:installDist`:

- **Stage 1** (`dc39387bd`): `BinaryAST`/`ExprAST` sealed over the 52-class
  tree. `NodeType.instantiate()` lost its default arm - the five
  unimplemented node types (`ReturnTStmt` among them) are explicit case arms,
  so a new `NodeType` constant is a compile error at the factory; the
  `readExprAST` wire boundary reports statement-in-expression-position as a
  corrupt-stream `IOException`. `SealedAstFamiliesTest` pins the hole set,
  the `ReturnTStmt` behavior, the 0..31 expression-encoding window, and the
  53-class sealed closure. Since BAST consumption is dormant upstream, no
  consumer conversions were done - the wire format got the fence, nothing
  more.
- **Stage 2** (`298067019`): `TypeConstant` sealed over its 21-class tree,
  modifier-only.
- **Stage 3** (`cace6570a`): `IdentityConstant` sealed with **zero**
  `non-sealed` hatches - the constructor-escape probe fakes that subclassed
  `PropertyConstant`/`FormalTypeChildConstant` were retired because the fatal
  `-Xlint:this-escape` gate enforces their property at compile time, and the
  reworked test pins the surviving half (constructor parent validation still
  fires, non-virtually). `ValueConstant` sealed.
- **Stage 4** (`07ed937b3`): `Component` sealed.
  `TypeComposition` stays unsealed pending its test adapter.
- **Hatch closure** (`ab5788584`): the remaining three `non-sealed` hatches
  proved unnecessary and are `final`: the pool-registration deadlock latch
  extends the (unsealed) `Constant` root directly - the interleaving lives in
  `register()`/adoption, Constant-level machinery, and the test still
  reproduces it; the `MethodStructure`/`PropertyStructure` fakes were
  replaced by same-package access to the protected constructors and
  `cloneBody()`. Every sealed family is now fully closed: **zero
  `non-sealed` declarations in main sources**, pinned with no hatch
  exemptions in the reflection tests.
- **Payoff rewrites** (`78111b85f`): `ClassConstant.getParentClass()` and
  `getOutermost()` are exhaustive pattern switches over the sealed
  `IdentityConstant` tree - the "any format I never heard of terminates the
  search" default is gone, every silent-terminator arm is explicit and
  labeled, and javac checks both the coverage and the pattern dominance
  (`NativeRebaseConstant` before `ClassConstant`, `FormalTypeChildConstant`
  before `PropertyConstant` - orderings the format switch could not even
  express). `NameExpression.getMeaning()` dropped its
  `@SuppressWarnings("fallthrough")`, its empty `default:`, and its
  hand-maintained trailing `IllegalStateException` for nested exhaustive
  switches, behavior preserved arm-for-arm.

### Conversion Waves A-E (2026-08-24, same day)

The dispatch-site conversion followed the sealing the same day, in
partitioned waves, each gated by the full suite and `xdk:installDist`:

- **Flagship file** (`bb72c4b58` + `10ba2869d`): TerminalTypeConstant's 22
  hierarchy-proxy format switches and all 48 defining-constant casts retired
  via the shipped `DefiningConstant` union - without the 150-call-site
  return-type change (a checked union conversion at the file boundary
  replaced it). The one survivor (`:1547`) is the keyword-kind data switch.
- **Wave A** (`35eb67527`): ClassStructure's identity-subject sites; the
  triage recorded that most of that file's switches are own-format DATA (one
  class spans ten Component formats) and correctly stay.
- **Wave B** (`1bc765d7d`): fifteen constants-tail sites across
  IdentityConstant, PropertyConstant, ParameterizedTypeConstant,
  ConstantPool, and TypeConstant; four silent subtype-dominance traps
  (formal type children inheriting PropertyConstant arms) made explicit.
- **Wave C** (`b9b0c48cf`, main session): RegisterInfo sealed over its five
  kinds; `Builder.checkNull/checkNotNull` and CommonBuilder static-field
  dispatch exhaustive - the worst-5 assert-guarded-else JIT site closed.
  17 files, +48/-56.
- **Wave E** (`2ac9c107c`): the parser `AstNode` tree sealed over all 96
  classes with exactly one documented hatch (`MethodDeclarationStatement`,
  for the out-of-package `EvalCompiler.EvalStatement`); pinned by
  `SealedParserAstTest`.

- **Composition sealing** (`6ba9dc8a2`, main session): `DelegatingComposition`
  sealed over `CanonicalizedTypeComposition`/`ProxyComposition`, all concrete
  composition classes final. The `TypeComposition` ROOT is deliberately left
  open: `GenericHandleCloneAsTest` and `OwnershipDiagnosticsTest` implement it
  as synthetic view factories, and reworking those fakes would risk their
  regression precision (recorded in the commit message).
- **Wave D - reclassified as STAYS**: the ~35 view-delegate cascades
  (`ByteView`/`BitView` capability checks in the `xRTViewFrom*` templates)
  are open-hierarchy capability probes over the deliberately-unsealed
  `ClassTemplate`/`DelegateHandle` families, already in pattern-bound form;
  converting them to switches would add lines with no exhaustiveness gain.
  Their bare `UnsupportedOperationException` tails were instead given
  subject-naming messages (`dc3e0d90d`): all 61 message-less runtime UOEs now
  identify the delegate/template/type/method that fell through.

Final census: **48 sealed declarations** (from zero at the start of the day),
exactly **one `non-sealed` hatch** in main sources (`MethodDeclarationStatement`,
for the out-of-package `EvalCompiler.EvalStatement`), **77 format switches
remaining of the original 141**, every survivor categorized as staying for a
stated reason - own-format data switches (ClassStructure,
TypeCompositionStatement), serialization factories (ConstantPool, Op,
Disassembler), value-kind data discrimination on multi-format classes,
cross-side format comparisons where the enum is the honest currency, and the
open-hierarchy capability cascades above - and **zero message-less
UnsupportedOperationExceptions** in the runtime.

Still open from this document's plan: `Op` (package split) and
`TypeComposition`'s root (see the optional follow-up row in the backlog).

### 2026-08-25 Cascade Conversion Wave

The remaining convertible cascades were re-scanned (97 raw same-subject
groups) and triaged by value: convert only where an assert-guarded ladder or
silent default becomes always-on, a blind cast becomes checked, or sealed
exhaustiveness is real. Converted (one commit): PresentCondition's
deserialization ladder and three identity casts; AllCondition's
addVersion/removeVersion ladders and the terminalInfluences assert-else
(which under -da silently processed an impossible condition kind as
NotCondition); TypeConstant's two transformer ternary-ladders and the
collectChildren Component dispatch; NameResolver's composite-member casts
(typed Class<T> boundary), pseudo/formal format casts, and the
wildcard-import assert; NameExpression's Singleton assert+cast;
StatementBlock's nesting-walk assert-else and TargetInfo constructor assert.

Explicitly NOT converted, with reasons: capability probes with honest else
arms (view-delegate STAYS, parent-walk probes, isA-driven dispatch mixing
semantic predicates); the `Object`-nid union consumers (TypeInfoReal k1/k2,
xConst/DebugConsole enid) - those collapse only when the nid union itself is
typed (see the MethodBody.Target precedent and the typed-keys backlog row);
the triplicated numeric-conversion ladders (xConstrainedInteger/BaseInt128/
xIntLiteral share a dispatch skeleton but differ per source kind - the honest
collapse is a convertTo double-dispatch design, recorded as a PoC candidate,
not a mechanical extraction); and javajit sites (parked for the JIT rebase).

The sealed-union mass-collapse lever moved separately: `MethodBody.Target`
(five records + a Narrowing sub-union) replaced the five-shape `Object`
payload convention - constructor assert-switch deleted, all 11 payload casts
and the raw `MethodInfo[2]` union gone, mispairing loud on every JVM, and
javac itself enumerated the untyped call sites the greps had missed.
