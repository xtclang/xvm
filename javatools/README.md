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