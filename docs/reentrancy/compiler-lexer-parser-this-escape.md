# Compiler, Lexer, and Parser This-Escape Fixes

This branch removes the compiler-side constructor escape patterns reported by:

```bash
./gradlew --no-configuration-cache --no-build-cache --rerun-tasks \
  -Porg.xtclang.java.lint=true \
  -Porg.xtclang.java.warningsAsErrors=false \
  -Porg.xtclang.java.maxWarnings=10000 \
  -Porg.xtclang.java.maxErrors=10000 \
  --console=plain --warning-mode=all \
  :javatools:compileJava
```

The fixed warning sites were all under `javatools/src/main/java/org/xvm/compiler/**`:

- `Lexer`: the public constructor no longer calls overridable whitespace
  parsing. Initial whitespace is consumed by private static source helpers.
- `Parser`: constructors no longer prime the token stream by calling `next()`.
  The first token is pulled lazily by `peek()`/`next()`, with `mark()` and
  `restore()` preserving the primed state for speculative parse paths.
- `ComponentStatement` / `MethodDeclarationStatement`: synthetic method
  declarations pass known component metadata through a superclass constructor
  instead of calling component setters from subclass construction.
- `PropertyDeclarationStatement`: annotations lifted out of annotated property
  types are left for normal AST parentage introduction instead of receiving the
  property as parent during construction.
- `SyntheticExpression` subclasses (`ConvertExpression`, `PackExpression`,
  `ToIntExpression`, `TraceExpression`, `UnpackExpression`): constructors only
  capture immutable inputs. Static factories attach the wrapper into the AST and
  finish validation after construction completes.
- `NamedTypeExpression`: synthetic validated type expressions are created
  through `forValidatedType`, so cached type metadata, stage, and parentage are
  assigned after construction.
- `TypeCompositionStatement`: anonymous inner class and fake-module
  construction now use factories that introduce parentage after the statement
  exists.

These patterns were risky because the compiler AST uses parent pointers,
component ownership, stage state, and validation metadata as mutable
construction-time ownership records. If a constructor publishes `this` to a
child, parent, lexer callback, or validation helper, another incremental or
parallel compilation path can observe a partially initialized node. The object
may have a parent but no complete child list, a component but no body, or
validation state computed before subclass fields are initialized.

The fixes avoid suppressions. They move owner-link publication and validation
finalization to explicit post-construction factories or to lazy first-use
priming.

## ConvertExpression Constant Folding

`ConvertExpression` previously called the inherited `convertConstant(...)`
helper from its constructor. That helper catches `ArithmeticException` from
`Constant.convertTo(...)` and returns `null`. This is not a swallowed runtime
conversion failure. It is the compiler's existing signal that the conversion
was not folded into a compile-time `Constant`; the expression still keeps the
conversion method in `m_aidConv`, and code generation emits the runtime
conversion.

The replacement keeps that behavior but uses a private static helper so the
constructor does not dispatch to an overridable method. This PR intentionally
does not change the old stderr soft-assert or the old multi-value partial-null
constant behavior; both sites now carry TODO comments because compiler
diagnostics should go through a structured logger or `ErrorListener`, not
`System.err`.

## Verification

`CompilerThisEscapeConstructionTest` includes hook-detecting `Lexer` and
`Parser` subclasses that throw if the superclass constructor calls their
overrides before subclass construction has completed. It also checks the factory
source shape for the synthetic AST cases.

The forced lint run at `/tmp/xvm-compiler-this-escape.log` reported:

```text
BUILD SUCCESSFUL in 18s
7 emitted this-escape diagnostics
0 Lexer.java, Parser.java, or compiler/ast diagnostics
```

This was the state immediately after the compiler/parser/AST wave. The two
utility warnings remain fixed on the separate `lagergren/fix-utils-this-escape`
branch. Five local JIT warnings were later fixed in this branch; the remaining
JIT constructor warning is `Xvm` startup owner publication, documented in
`jit-implications.md`.

## Compiler AST And Context Mutation Audit (2026-08-25)

Must-audit row 175 asked whether compiler AST/context mutation proved a
runtime/ASM must-fix in this branch. It does not. The sites are real mutable
state, but they are compiler-scoped and currently depend on the compiler's
request-local AST/context contract. If incremental or parallel compilation
shares ASTs, `Context`s, or validation results across requests, this becomes a
compiler-reentrancy bug; that fix belongs on the compiler branch, not in the
runtime/ASM owner PR.

The broad scan used to locate the named families was:

```bash
rg -n --glob '*.java' \
  'm_mapByName|m_constant|m_method|m_lambda|m_listBreak|m_listContinue|NameResolver|Context\.' \
  javatools/src/main/java/org/xvm/compiler \
  javatools/src/main/java/org/xvm/compiler/ast
```

Sites read for classification:

| Family | Evidence | Classification |
| --- | --- | --- |
| `Context.m_mapByName` | `Context.ensureNameMap()` lazily creates and assigns a plain `HashMap`; `replaceArguments(...)` mutates it as validation narrows/renames arguments. | Compiler request-local cache. Safe only while each validation request owns its `Context` chain. Move to request-owned context state if contexts become shared. |
| `NameResolver` staged resolution | `NameResolver.forceResolve(...)`, `resolve(...)`, `ensurePartiallyResolvedComponent()`, and `resolvedComponent(...)` mutate `m_stage`, `m_constant`, `m_constantFirst`, `m_component`, `m_typeMode`, and `m_errs` as one resolver walks one AST name. Import and named-type AST nodes cache resolvers. | Compiler AST-local staged cache. Record-only for this branch; future compiler branch should either make resolvers per validation pass or key cached resolution by request/owner. |
| `InvocationExpression.m_method` and target state | Validation paths assign `m_argMethod`, `m_method`, `m_fBindTarget`, and `m_targetInfo`; later type/codegen paths read `m_method`. The field is explicitly transient. | Compiler validation/codegen state. Wrong if one AST instance is validated concurrently under two contexts; not a runtime container or ASM metadata owner leak. |
| `LambdaExpression.m_lambda` | Validation asserts `m_lambda == null`, creates a `MethodStructure` for the lambda, later uses it for component lookup/codegen, and `clone()` intentionally clears it. | Compiler AST-owned generated-method cache. It already acknowledges clone/retry sensitivity; keep it out of runtime work and redesign with explicit compiler request ownership if ASTs are reused. |
| Break/continue assignment lists | `Statement`, `ForStatement`, `WhileStatement`, `SwitchStatement`, and `ForEachStatement` append transient `Break` records/lists during validation, merge assignment/narrowing maps, then clear or discard entries. | Compiler control-flow validation scratch state. Requires single validation owner per AST/control-flow pass. |

Verdict: close row 175 for this runtime branch as compiler-scoped
record-only. No runtime/ASM must-fix graduated. The compiler branch should
decide between proving one AST/context graph per request, making validation
scratch state explicitly request-owned, or rebuilding/cloning the AST per
concurrent validation pass. The existing constructor-escape fixes and clone
audit reduce accidental early publication, but they do not by themselves make
shared compiler AST mutation safe.
