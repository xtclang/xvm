# Directory: ./src/ #

This directory contains prototype and proof-of-concept Java code.

## Compiler

Status: Prototype (will be re-written in natural Ecstasy code)

* Driven by `org.xvm.compiler.CommandLine`
* Lexed by `org.xvm.compiler.Lexer` into `org.xvm.compiler.Token` objects
* Recursive descent parsed by `org.xvm.compiler.Parser` into `org.xvm.compiler.ast.AstNode` objects
* AST nodes are 2-pass compiled (with optional re-queuing) via `org.xvm.compiler.ast.StageManager`

## Assembler (prototype)

Status: Prototype (will be re-written in natural Ecstasy code)

* Structures are all based on `org.xvm.asm.XvmStructure`
* Constant values and persistent identity references encoded as `org.xvm.asm.Constant` objects
* Inheritance tree of component types starting with `org.xvm.asm.Component`
* Virtual machine instructions encoded as `org.xvm.asm.Op` objects (see `/xvm/doc/ops.txt`)

## Runtime

Status: Proof-of-concept (will be replaced by LLVM back end)

* Bootstrapped by `org.xvm.runtime.TestConnector`
* Execution fram is `org.xvm.runtime.Frame`

**Warning:** Do **not** optimize this runtime project. This project is a proof-of-concept **only**. The IR was not designed to be interpreted; interpreting the IR is a fools errand and will make you want to cry; additionally, every day that this proof-of-concept still exists, somewhere a kitten dies. Any decision that improves the interpreter is stealing cycles away from building the real runtime, and likely introducing irreversible and awful design decisions. (Translation: _It's ok if the interpreter is slow and bloated; it only has to bootstrap the LLVM IR emission code_.)

## Hints

* On Windows, to compile these sources using "javac", you must add the command line option "-encoding UTF8".
* In IDEA, if you open a class (like `AstNode` or `XvmStructure`) that is the root of a big type hierarchy, then press `Ctrl-H`, it will provide a browsable tree of that type hierarchy.
