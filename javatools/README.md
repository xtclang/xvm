# Directory: ./javatools/ #

This directory contains the "javatools" project, which is a
set of Java classes that implement the Ecstasy lexer, parser,
compiler, IR, runtime, etc.

This project uses the library produced by the "javatools_utils" project,
and the `implicit.x` resource file from the "ecstasy" project.

Long term, this project will be used to help support IDE
plug-ins for common Java-based IDEs, such as IntelliJ IDEA.

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

### Compiler counters and same-JVM compilation

Several AST nodes allocate synthetic labels or code-container ids from
compiler-wide counters. These counters do not carry semantic ordering; they
only need to produce stable, unique names during code generation. In the
single-threaded compiler path, `AtomicInteger.incrementAndGet()` and
`AtomicInteger.getAndIncrement()` preserve the old sequence exactly:
`++counter` starts at one and `counter++` returns the current value.

The counters are process-wide because they live on AST classes, so same-JVM
incremental compilation or parallel compilation can exercise them from more
than one thread. Plain `int` increments can lose updates in that scenario and
produce duplicate labels. The counters are therefore `private static final
AtomicInteger` fields. This is intentionally separate from runtime container
ownership work: it does not change compiler ownership or reset behavior, it
only gives the existing process-wide counters atomic allocation.

`CompilerCounterAtomicTest` verifies both sides of that contract: sequential
values match the old behavior for normal compilation, and bounded concurrent
allocation produces unique labels.

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
