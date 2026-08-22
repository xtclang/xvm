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
