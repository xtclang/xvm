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

The seven remaining targeted warnings are the two utility warnings fixed on the
separate `lagergren/fix-utils-this-escape` branch and five JIT warnings that are
documented for JIT-specific follow-up.
