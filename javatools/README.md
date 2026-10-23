# Directory: ./javatools/ #

This directory contains the "javatools" project, which is a
set of Java classes that implement the Ecstasy lexer, parser,
compiler, IR, runtime, etc.

This project uses the library produced by the "javatools_utils" project,
and the `implicit.x` resource file from the "ecstasy" project.

The compiler also supports the compiler-backed language server. Start with
[Compiler support for the language server](lsp-compiler.md) for a complete walkthrough of the
compiler changes, their rationale, the LSP consumers and the regression tests. The
[architecture overview](#compiler-architecture-for-language-tooling) below provides a shorter introduction.

(Note: The test portion of this project may have dependencies
on test frameworks.) 

This project produces the `javatools.jar` file.

The License is the Apache License, Version 2.0. 

## Compiler

Status: Suitable for use

* Driven by `org.xvm.tool.Compiler`
* (The original command line tool for the compiler is
  `org.xvm.compiler.CommandLine`)
* Lexed by `org.xvm.compiler.Lexer` into
  `org.xvm.compiler.Token` objects
* Recursive descent parsed by `org.xvm.compiler.Parser` into
  `org.xvm.compiler.ast.AstNode` objects
* AST nodes are multi-pass compiled (with optional re-queuing)
  via `org.xvm.compiler.ast.StageManager`

## Compiler architecture for language tooling

The language server needs the compiler's answers while a user is editing: which declaration a
name binds to, which overload an argument fits, what type an expression has, and which diagnostics
belong to the current document revision. Running the command-line compiler and parsing its output
cannot supply those answers. Successful compilation also discards or rewrites some of the source
information that navigation needs; an unfinished buffer may never reach emission at all.

The [compiler guide](lsp-compiler.md) explains the design rationale and changes to `javatools`
that make this possible. The compiler remains independent of LSP, IntelliJ and VS Code.
It exposes compilation results and source facts; the adapter in `lang/lsp-server` converts those
facts into immutable editor models, applies document-version checks and implements the protocol.

| Compiler mechanism | Why an editor needs it | Code and contract |
| --- | --- | --- |
| Owned diagnostics and explicit stopping | Associate every report with an attempt, retain speculative failures appropriately, and cancel obsolete work without manufacturing source errors. | `ErrorListener`, `ErrorList`, `Reporting`; [diagnostic design](lsp-compiler.md#diagnostics-cancellation-and-reporting-scopes). |
| Snapshot inputs and compilation outcomes | Compile unsaved module/member files together and distinguish successful output from ordinary partial progress after a failure. | `EmbeddingSupport` and the `ModuleInfo` source hooks; [host snapshots and outcomes](lsp-compiler.md#source-snapshots-and-compilation-outcomes). |
| Selected semantic facts | Navigate exact overloads and argument slots without reproducing compiler resolution in the editor. Preserve references lost to constant folding. | `InvocationBinding`, `InitializerBinding` and `EmbeddingSupport.Compilation`. |
| Declaration and incomplete-source analysis | Answer bounded scope, type and argument queries while the buffer is unfinished, without presenting invalid source as executable code. | `EmbeddingSupport.analyzeDeclarations`, `analyzeIncomplete`, `CursorBinding` and `compiler.ast.partial`. |
| Source-tree copying | Probe proposed syntax on an independently parented tree without changing the source tree being inspected. | `AstNode.copyTree` and its covariant specializations. |
| Tolerant repositories and explicit dependency validation | Preserve best-effort repository lookup while letting the adapter validate the specific dependency bytes it accepts. | `DirRepository`, `FileRepository`, `LinkedRepository` and the adapter's `XdkDependency`; no new repository reporting API. |
| Scoped compiler state and cache reporting | Release operation-owned listeners and temporary state, and replay cached diagnostics to the current attempt. | `Reporting`, `TransientThreadLocal` and `TypeInfo` diagnostic replay. |
| Running a built module for an IDE | Run a named entry method with exact arguments in a reusable runtime, without the runner's HTTP host, so editors can offer Run without Gradle. | `EmbeddingSupport.runMethod`, `InterpreterControl` and `lib_runner` tasks; the language server's [embedded build and run](../lang/lsp-server/README.md#embedded-build-and-run). |

### Preserve selected semantic facts

`InvocationBinding` records selected methods, function signatures, constructors and argument-to-
parameter mappings at the point where the compiler establishes them. The maps use AST identity;
they do not guess a target by spelling or make a dynamic function call look like a statically
selected method. `InitializerBinding` preserves validated source facts before a constant
initializer's temporary method is discarded. Without that step, a name can compile correctly but
disappear from references, rename and highlighting after constant folding.

These facts belong to a compilation attempt. An immutable map does not make the contained AST,
type constants or constant pool safe to share across workers. The language adapter copies the
facts it needs into detached models before releasing the attempt. Keeping this boundary explicit
is essential for a long-running editor that repeatedly replaces analyses of unsaved text.

The five source-binding classes live in `org.xvm.compiler.ast.bindings`, alongside `ast.partial`.
Compiler-owned collectors write the facts; a one-operation `ast.BindingAst` bridge associates
lambda parameter registers with written declarations without widening existing AST members.
See [binding ownership and package boundaries](lsp-compiler.md#keep-binding-facts-and-their-writers-together).

### Analyze declarations and unfinished source explicitly

`analyzeDeclarations` resolves declaration structure without promising a fully validated or
emitted module. `analyzeIncomplete` retains supported incomplete syntax and reports cursor facts
from its real lexical context. Its cursor overloads support a broader set of editing positions
than the deliberately narrow trailing-expression overload; their Javadoc defines each boundary.
Unsupported damage remains a diagnostic rather than a guessed semantic result.

Incomplete nodes and their analysis helpers live in `org.xvm.compiler.ast.partial`:
`PartialQueries` coordinates inspection, `PartialCallResolver` and `PartialConstructionResolver`
fit candidates, `PartialArgument` builds disposable argument proposals, and `CursorScope` captures
visible names and resolves unfinished type headers. These helpers are package private.

Java subpackages do not share package access. One internal bridge, `ast.PartialAst`, lets these
helpers reuse ordinary parenting, validation, argument fitting, construction preparation and
scope lookup. Existing AST members retain their protected or package-private visibility. The
bridge delegates compiler operations; the partial package decides which incomplete syntax and
candidate facts to retain. It neither introduces another type checker nor exposes compiler
contexts as a host API. Embedding hosts continue to consume `EmbeddingSupport` results.

Candidate fitting uses ordinary compiler rules on trial syntax. A possible completion is not a
selected invocation, and partial analysis never supplies an executable module. This distinction
lets signature help and completion use authentic compiler information without weakening the
normal validation or emission contract.

### Copy source trees and preserve ordinary compiler behavior

The old pervasive `clone()` calls obscure an important distinction: `Object.clone()` starts with
a shallow field copy, while AST overrides selectively copy children and repair ownership. The
name alone does not tell a caller which bindings, transient state or parents remain shared, and
adding a field can silently change what a clone retains. A clone of a validated node is therefore
not equivalent to freshly parsed syntax. This is especially confusing for speculative editor
queries, which need to know exactly what a trial may mutate without affecting the original.

`copyTree()` names the source-tree copy contract and gives expression/statement callers covariant
return types. Its contract covers child ownership, source metadata and the existing stage-aware
copy behavior; it is not a general deep copy of an entire compiler or constant pool. Specialized
incomplete nodes copy and adopt their children explicitly. The existing `clone()` entry point
continues to use the same copying machinery so the two APIs cannot drift independently.

The compiler's callable typing and fitting corrections serve both ordinary compilation and
cursor probes. Boolean operand corrections preserve evaluation and side effects even when the
result is constant. These are compiler correctness rules, not alternate semantics enabled by an
LSP flag. The separate removal of the abandoned binary AST does not remove the source AST used
by tooling. The serialized format contains no BAST metadata slot; older `.xtc` files must be
recompiled with the current compiler.

Repository lookup remains tolerant: an unreadable candidate can be skipped while another file
or repository supplies the module. An unrelated failed candidate does not establish why an import
is missing. The LSP validates its explicitly configured dependency bytes before accepting immutable
snapshots; it does not infer dependency errors from repository scans. The
[diagnostic guide](lsp-compiler.md#diagnostics-cancellation-and-reporting-scopes) explains the
intentional Java API migrations, retained CLI behavior and regression evidence;
it does not claim universal source or binary compatibility for old Java embedding code.

## Compiler diagnostics and error listeners

The detailed design and migration guide now lives in
[Compiler support for the language server](lsp-compiler.md#diagnostics-cancellation-and-reporting-scopes).
It explains [why probes and final propagated listeners replace implicit suppression](lsp-compiler.md#why-explicit-probes-and-a-final-propagated-listener),
diagnostic ownership, scoped reporting, cancellation, compatibility and regression evidence.

## Assembler

Status: Suitable for use

* Structures are all based on `org.xvm.asm.XvmStructure`
* Constant values and persistent identity references encoded
  as `org.xvm.asm.Constant` objects
* Inheritance tree of component types starting with
  `org.xvm.asm.Component`
* Virtual machine instructions encoded as `org.xvm.asm.Op`
  objects (see the `ops.txt` file in the documentation)

## Runtime

Status: Working proof-of-concept (will be replaced by an
LLVM-based adaptive compiler).

* Command line is `org.xvm.tool.Runner`
* Implementation in `org.xvm.runtime` package

**Warning:** This runtime is not speedy, by any stretch; this
is expected, because it is only intended as a proof-of-concept.
The runtime is currently implemented as an interpreter, and
the interpreter (which would be naturally slow to begin with)
has not been optimized. Its purpose is to be malleable and easy
to test, so that we could prove out the design of the compiler
and the Ecstasy IR.
