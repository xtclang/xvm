# Failures with nowhere to go

An audit, not a refactor. The output is findings to triage, not a diff: each site needs a
judgement about whether the failure is real and where it ought to go, and those judgements belong
to whoever owns the code.

Measured on `lagergren/errs`. First taken against `origin/master` at `fbdeb86c7`; re-checked
after the branch was rebased onto `4a1eae6f7`, and the counts are unchanged - this branch
deliberately fixed none of them.

## What was counted

| | |
|---|---|
| `System.err.print*` / `printStackTrace()` in `javatools/src/main/java` | **56** |
| `catch (… ignore) { }` with an empty body | **35** |

### Printed rather than reported, by area

| area | sites |
|---|---|
| `runtime` | 16 |
| `asm` | 8 |
| `asm/constants` | 8 |
| `javajit` | 6 |
| `runtime/template/_native/web` | 5 |
| `compiler/ast` | 3 |
| `javajit/builders` | 3 |
| `runtime/template/_native/crypto` | 2 |
| `tool` | 2 |
| `asm/ast`, `runtime/template/_native/fs`, `runtime/template` | 1 each |

### What the empty catches swallow

| exception | sites | |
|---|---|---|
| `IOException` | 14 | |
| `RuntimeException` | 6 | |
| `CompilerException` | 4 | |
| **`Exception`** | **4** | **catches every checked exception too** |
| **`Throwable`** | **3** | **catches `Error` as well** |
| `WrapperException`, `ArithmeticException`, `NameNotFoundException`, `KeyStoreException`, `IllegalStateException` | 1 each | |

The last two rows were missing from the first version of this audit, which is unfortunate,
because they are the rows that matter most. A `catch (Throwable)` with an empty body swallows
`Error` - an `OutOfMemoryError`, a `StackOverflowError`, a tripped assertion - and continues as
though nothing had happened. That is exactly the shape the prior-art branch found a real bug in:
a DNS continuation caught `Throwable`, so an `Error` reached XTC code disguised as "host not
found", and an interrupt was dropped without restoring the flag.

The seven:

| site | catches |
|---|---|
| `asm/Argument.java:56` | `Throwable` |
| `asm/OpVar.java:109` | `Throwable` |
| `javajit/JitConnector.java:151` | `Throwable` |
| `runtime/template/annotations/xFuture.java:781` | `Exception` |
| `tool/Disassembler.java:285` | `Exception` |
| `tool/Launcher.java:795` | `Exception` |
| `tool/ModuleInfo.java:561` | `Exception` |

All 35 now name the variable `_`, the unnamed variable, rather than `ignore` or `ignored`. That
was a sweep across the whole tree, not a judgement about any of these sites: it makes "this
exception is deliberately dropped" something the language says rather than something a convention
implies, which is worth having before anyone works through the list. The question for each is
unchanged - whether the deliberate choice is still right.

## The sites worth looking at first

Of the 56 printed failures, **five** occur where a listener appears to be in scope - that is, the
code could have reported and chose to print. Those are the clearest candidates:

| site | what it prints |
|---|---|
| `compiler/ast/Expression.java:814` | `"No conversion found for " + constVal` |
| `compiler/ast/ConvertExpression.java:93` | `"No conversion found for " + aVal[i]` |
| `compiler/ast/NameExpression.java:1186` | `"TODO: AST for " + this` |
| `asm/ConstantPool.java:3944` | each error of a local `ErrorList`, then throws |
| `asm/constants/TypeConstant.java:4723` | commented out; not a live site |

**Deliberately not changed here.** The first two carry `// TODO GG: remove the soft assert below`,
so they are someone's marker rather than an oversight, and "fixing" them would silently change what
a compile prints. They are listed so the decision can be made by whoever wrote them.

`ConstantPool:3944` is different in kind: it is the static implicits bootstrap, where no
caller-supplied listener exists, so printing is close to the only option available. It is evidence
for the ownership discussion in `errs.md` rather than a defect on its own.

## Why this is worth doing eventually

The prior-art branch ran the same audit and found real bugs in this category, not just untidy code:

- a DNS continuation caught `Throwable`, so an `Error` reached XTC code as "host not found", and an
  interrupt was discarded without restoring the flag;
- `KeyStoreOperations.deleteKeyStoreEntry` swallowed a failed delete, so a certificate revocation
  reported success with the revoked certificate still in the store;
- `Launcher.showSystemVersion` relied on catching an NPE to mean "not in the repository".

The shape to look for is the same each time: a `catch` whose body is empty because there was
nowhere to report, in code where the caller had every right to be told.

## How to work through it

1. Start with the three `Throwable` and four `Exception` sites above. They are the smallest group
   and the only ones that can swallow an `Error`; the rest at least name what they drop.
2. Then triage the 14 `IOException` sites, which are the richest seam - an I/O failure that
   nobody hears about usually means a later failure with a confusing cause.
3. For each, decide: genuinely nothing to report, report to a listener already in scope, or
   propagate. Only the middle case is a code change of the kind this branch has been making.
4. Do the `runtime` prints last. They are the largest group and the least reachable by a listener
   today, because the runtime side of ownership has not been done - see `errs.md`.

Expect the output to be issues rather than commits. A site that turns out to be a real bug deserves
its own fix with its own test, not a sweep.
