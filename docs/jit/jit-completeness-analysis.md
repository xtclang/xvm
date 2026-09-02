# The Java JIT back-end: what is missing, and what is left to build

Analysis date: 2026-09-02. Read-only: no source file was modified, nothing was committed or pushed.

**Provenance of every number below.** Two revisions are in play and they are not the same JIT.

| | revision | JIT tip |
|---|---|---|
| mainline | `origin/master` `832f14f17` | `03e159e0f` "JIT Milestone: Tupple support; improved Ctx handling (#544)", 2026-08-27 |
| working branch | `lagergren/lazy-instance` `c8594b194` | merge-base `82683bcd2`; **one JIT milestone behind master** |

Static citations (`file:line`) are taken from **`origin/master`** unless the text says otherwise, because
that is where the JIT work lands; master was extracted to a scratch directory rather than checked out.
The end-to-end measurements in [section 2](#2-what-actually-runs-measured) were run against the **branch**
build (`xdk/build/install/xdk`, built 13:27 today), because that is the only built XDK on this machine.
`javatools/src/main/java/org/xvm/javajit/` has not changed on the branch since `43601265e` (2026-09-01),
and the branch's JIT differs from master's only in ErrorListener wiring plus small builder edits
(`git diff origin/master...HEAD -- javatools/src/main/java/org/xvm/javajit/` is 6 files, and master's
`#544` tuple work is absent). Where that difference matters to a claim, it is called out.

---

## Verdict

**An advanced prototype that runs a hello-world and almost nothing else.** The architecture is real and
substantial - 22,074 lines of Ecstasy-to-JVM-bytecode translation in `org.xvm.javajit` plus a 17,668-line
`javatools_jitbridge` native library - the pipeline works end to end (CLI flag, linker, per-module
ClassLoader, class generation on demand, generated code executing, correct output), and 176 of 215
interpreter ops have code generators. But on the repo's own manual test corpus it produces correct output
for **4 of 69 modules** where the interpreter manages 56.

**The single biggest blocker is not any one missing feature: it is that incomplete code generation
silently produces a stub that returns zero/null and reports success, and nothing in the build ever
executes JIT-generated code, so the fact that 52 of the 56 modules the interpreter runs fail under
the JIT is invisible.** Measured, most starkly: a module
named `hello.example.org` compiles, runs under `-J`, prints nothing, and **exits 0**. The same module
under the interpreter prints its output. The cause is a hard-coded allow-list of class names
(`builders/CommonBuilder.java:4117`, 88 entries) whose only user-code entries are `Test*`, `test*` and
`anon*` - and `anon` is prepended by `Xvm.moduleToPackageName` (`Xvm.java:628`) **only for module names
with no dots**. Every properly qualified module name misses the list, and every method of it is emitted
as `defaultLoad(); return;` (`CommonBuilder.java:4085-4089`, helper at `Builder.java:1000`).

Everything else in this document is downstream of that: until failures are loud and measured, the
remaining gap list cannot be trusted to be complete.

---

## 1. Scope, size and entry points (facts)

| | files | lines |
|---|---|---|
| `javatools/src/main/java/org/xvm/javajit/` (master) | 44 | 22,074 |
| `javatools_jitbridge/src/main/java/` (master) | 133 | 17,668 |
| same, on the branch | 43 / 132 | 20,470 / 20,250 |
| interpreter `org.xvm.runtime/` for scale | - | 70,162 |

Three files carry half of it: `builders/CommonBuilder.java` (4,171 lines on the branch),
`BuildContext.java` (3,631), `Builder.java` (2,068).

**It is reachable from a normal run, behind a flag, and the flag works.** Verified call chain:

- `javatools/src/main/java/org/xvm/tool/LauncherOptions.java:76` - `-J` / `--jit`, "Enable the JIT-to-Java back-end".
- `Runner.java:347` - `return isJit ? new JitConnector(repo) : new InterpreterConnector(repo);`
  (also `TestRunner.java:94`).
- `JitConnector.loadModule` (`:42`) links; `start` (`:58`) builds the native injector and container;
  `invoke0` (`:81`) calls `container.newFiber(...)`, which reflectively loads the generated `¤module`
  class and invokes `run`/`run$p`.

There is **no mixed mode**: the two connectors are mutually exclusive, and nothing falls back to the
interpreter when code generation fails.

Translation happens at class-load time - `ModuleLoader.findClass` (`:87-103`) calls
`typeSystem.genClass(...)` and `defineClass`. There is no profiling, no tiering, no deoptimisation and
no on-disk cache of generated classes; "JIT" here means "translate Ecstasy to JVM bytecode on first use
and let HotSpot do the actual JIT". Generated classes are re-derived on every process start.

The rest of the compiler is JIT-aware: 160 files under `org.xvm.asm/` reference `org.xvm.javajit`
(op `build()` methods, `ensureJitClassName`/`ensureJitMethodName` on constants and `MethodInfo`).
`org.xvm.runtime` (the interpreter) references it **not at all** - the two back-ends are fully disjoint.

## 2. What actually runs (measured)

Every `.xtc` in `manualTests/build/xtc/main/lib` (69 modules) was run twice from a scratch directory,
once with `-J` and once without, 45s cap each. Success = exit 0; for the four that passed, stdout was
diffed against the interpreter's and is identical.

| | interpreter | JIT |
|---|---|---|
| exit 0 | 56 / 69 | **4 / 69** |

The four: `EchoTest`, `FizzBuzz`, `TestFizzBuzz`, `TestRun`. 52 modules pass under the interpreter and
fail under the JIT; 13 fail under both (mostly needing network/filesystem or genuinely broken, e.g.
`TestRanges` reports "Missing method run" under both).

**The silent-success case, reproduced deliberately.** Two one-line modules, identical except for the
module name:

```
module HelloPlain      -> -J prints "Hello from a plain module",     exit 0
module hello.example.org -> -J prints WARNING RUNTIME-19 and nothing, exit 0
                            interpreter prints "Hello from a qualified module"
```

Two modules in the corpus fail this way with no diagnostic at all: `operators` and `TestOperators`
produce **zero output and exit 1**, where the interpreter prints "All operator tests passed."

**Failure taxonomy** (deepest `Caused by:` per module; the pairs are the same test under two names):

| n | failure | example modules |
|---|---|---|
| 15 | `java.lang.AssertionError` inside code generation | TestCollections, TestMisc, TestNumbers, TestProps, TestReflection, TestTimeouts |
| 7 | (passes, or fails with no error text at all) | EchoTest, FizzBuzz, TestFizzBuzz, TestRun, **operators, TestOperators**, FailProbe |
| 5 | `ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0` | files, templates, TestFiles, TestUriTemplate, AesRawKeyRepro |
| 4 | `UnsupportedOperationException: Field initializer` (`CommonBuilder.java:930`) | annos, maps, TestAnnotations, TestMaps |
| 4 | `NoSuchMethodError: Number.magnitude$get` - **branch-only**: `NumberBuilder.java:116` comments the case out; master implements it at `:121`, so these four plausibly pass on master | defasn, loop, TestDefAsn, TestLoops |
| 4 | `UnsupportedOperationException: Not implemented: src=Primitive; dst=Ref` | innerouter, lambda, TestInnerOuter, TestLambda |
| 9 | `IllegalStateException: missing class for constant: Class{... module=json/crypto/net/collections}` | IO, crypto, queues, TestTcpClient, ... |
| 2 | `VerifyError: Bad type on operand stack` | array, TestArray |
| 2 | `ClassFormatError: Invalid index 7 in LocalVariableTable` | nesting, TestNesting |
| 2 | `UnsupportedOperationException: NEWCG_0` | generics, TestGenerics |
| 2 | `UnsupportedOperationException: Time{value="1999-12-25T12:01:23"}` (constant loading) | literals, TestLiterals |
| 2 | `UnsupportedOperationException: buildCreateRef` (`BuildContext.java:1320`) | services, TestServices |
| rest | NPEs in `ClassStructure`/`TypeConstant`, `IllegalStateException` | cliTest, compiler, regex, StringBufferTest, xunit_demo |

The `missing class for constant` family is one root cause, not nine: `JitConnector.loadModule` adds only
the main module to the `Linker`, and `Linker.link()` (`Linker.java:375-377`) still reads
`// TODO check for an existing type system` / `// TODO perform linking` / `// TODO register new type
system`. It merges exactly `shared[0]` (ecstasy) into `owned[0]` and does nothing else. **Any program
importing a second library module cannot run.**

Classes observed being stubbed at runtime across the sweep, i.e. silently returning 0/null:
`ecstasy.reflect.Class`, `reflect.Type`, `reflect.Outer$Inner`, `fs.Directory`, `fs.File`, `fs.FileNode`,
`text.RegEx`; plus partially-stubbed methods on `Number`, `IntNumber`, `Int64`, `Map`, `Set`, `Range`,
`UniformIndexed` (the `NO_JIT_METHODS` table, `CommonBuilder.java:4228-4251`).

Timing, for calibration only: `TestFizzBuzz` takes 3.98s under `-J` vs 2.43s interpreted (two runs each,
stable). No steady-state performance conclusion can be drawn from that - it is a startup-dominated
trivial program, and the `-J` figure includes a bytecode dump the connector performs on **every** run
(see section 6).

## 3. Op coverage (facts)

`Op.build(BuildContext, CodeBuilder)` (`asm/Op.java:434`) throws `UnsupportedOperationException` by
default. Walking each of the 215 classes in `asm/op/` up its superclass chain to the nearest `build`
override: **176 have a generator, 38 fall through to the throwing default, 1 (`Redundant`) has none in
its chain.** Six base classes carry the bulk generically: `OpGeneral` (18 `GP_*`), `OpCondJump` (17
`Jump*`), `OpIndex` (19), `OpTest` (14 `Is*`), `OpInPlaceAssign` (11 `IIP_*`), `OpInPlace` (6 `IP_*`).

The 38 unimplemented ops on master, grouped:

| group | ops | what it costs |
|---|---|---|
| tuple-**argument** call/invoke | `Call_T0 Call_T1 Call_TN Call_TT Invoke_T0 Invoke_T1 Invoke_TN Invoke_TT Construct_T` | calling with a tuple-packed argument list |
| property-indexed in-place | `PIP_Add PIP_And PIP_Dec PIP_Div PIP_Inc PIP_Mod PIP_Mul PIP_Or PIP_PostDec PIP_PostInc PIP_PreDec PIP_PreInc PIP_Shl PIP_Shr PIP_ShrAll PIP_Sub PIP_Xor` (17) | `obj.prop[i] += x` and friends |
| construction | `NewCG_0 NewCG_1 NewCG_N NewG_T New_T` | generic child construction; observed as the `NEWCG_0` failure in `generics` |
| control flow | `OpSwitch JumpNFirst CatchStart` | some switch forms; `do..while(first)`; typed catch entry |
| misc | `MoveThis SynInit Var_M Var_MN` | outer-`this` access, synthetic init, "masked" vars |

On the branch this list is 47, because master's `#544` added the tuple-**return** forms
(`Call_0T/1T/NT`, `Invoke_0T/1T/NT`, `Return_T`, `Var_T/TN`). The branch's measured results in
section 2 therefore understate master by exactly that feature.

## 4. Unfinished markers (facts)

`org.xvm.javajit` on master: **82 `TODO`, 70 `UnsupportedOperationException`, 3 `TEMPORARY`**, zero
`FIXME`/`notImplemented`. By theme rather than verbatim:

- **Codegen shape gaps (the largest group, ~30 UOEs).** "Union types not yet supported" appears five
  times in `CommonBuilder` (`:2104 :2323 :2626 :2845 :2989`); "Not implemented: src=X; dst=Y" flavor
  conversions eight times across `BuildContext` and `CommonBuilder`; `Field initializer` (`:930`),
  `Primitive injection` / `MultiSlotPrimitive injection` / `XVM Primitive injection` (`:1374-1384`),
  `Multislot P_Set` (`BuildContext.java:2692`), `Copy context` (`CommonBuilder.java:3525`), `retrieve opts` (`:1442`).
- **Container semantics, entirely absent.** `Container.java:99-106`: `// TODO create child container`,
  `// TODO control surface area`, `// TODO stats surface area`, `// TODO` memory accounting. All four
  `LinkerContext` methods are stubs returning constants (`:117-142`).
- **Memory accounting, declared but empty.** `Ctx.alloc/allocated/realloc/free` (`Ctx.java:71-111`) are
  four documented methods with `// TODO` bodies. The container memory limit that Ecstasy specifies does
  not exist.
- **Services, absent by design-not-yet-done.** `Ctx.java:36` is a commented-out `// xSvc service;`;
  `BuildContext.java:1486,1492` say `this:class will NPE for now` and `this:service will NPE for now`.
- **Linking.** `Linker.java:334,373-377` as quoted above.
- **Debug scaffolding in the product path.** `ModuleLoader.dump` is marked `// TODO: REMOVE` (`:142`);
  `JitConnector` has a `// TEMPORARY: manually added names` dump list (`:232`).
- **The allow-list itself**, `CommonBuilder.java:4117-4251`, whose comments are a candid per-method gap
  list ("RETURN_T is not implemented", "virtual constructor method constant", "verifier stack mismatch",
  "key's formal type is tracked as Object", "A_SUPER argument for a virtual construction", ...).

## 5. Tests (facts, and one surprise)

**There is no test anywhere that executes JIT-generated code.** No Gradle task passes `-J`/`--jit`
(grep over all `*.kts`/`*.gradle`/`*.properties` finds only the `jit-bridge-binary` artifact wiring in
`xdk/build.gradle.kts:42-51`). `testXtc` runs the interpreter.

The only JIT tests are three files, and all three exist **only on `lagergren/lazy-instance`**, not on
master:

| file | tests | run? | what it asserts |
|---|---|---|---|
| `javatools/src/test/java/org/xvm/javajit/JitConcurrencyHazardCensusTest.java` | 4 | yes, `skipped=0` | pins known hazards via reflection: `new Ctx(null,null)` is accepted, `ModuleLoader.loadedClasses` is a plain `HashMap`, `CommonBuilder`'s skip sets are process-global, JIT-name caches are non-volatile |
| `javatools/src/test/java/org/xvm/javajit/JitConstructorEscapeTest.java` | 4 | yes, `skipped=0` | descriptor shape of `JitMethodDesc`/`JitCtorDesc` |
| `javatools/src/test/java/org/xvm/javajit/JitFailurePropagationTest.java` | **0** | never | 40 lines of helper methods and **not one `@Test`** - it has no XML in `build/test-results` because JUnit finds nothing to run |

Verified from `javatools/build/test-results/test/TEST-org.xvm.javajit.*.xml` (timestamped today,
`tests="4" skipped="0" failures="0"` each). So: 8 JIT tests exist, they all really run, none of them
compile or execute a single Ecstasy method - they are source-shape and reflection assertions. The good
news is the absence of silent `assumeTrue` skipping here; the bad news is there is nothing to skip.

## 6. Integration gaps

Verified as **working**: CLI flag; native type system bootstrap from `javatools-jitbridge.jar` probed off
the `javatools` path (`NativeTypeSystem.java:63-71`); per-module `ClassLoader` hierarchy with generation
on demand; injection (`Ctx.inject`, `nMainInjector`); exception translation (a generated XTC exception is
recognised by class-name prefix at `JitConnector.java:144-151`); `LineNumberTable` and
`LocalVariableTable` emission (`BuildContext.java:691,696,750,...`), so generated frames are readable in
a Java stack trace and a Java debugger.

Verified as **missing**:

- **Multi-module linking** - section 2. Gates every non-trivial program.
- **Services, fibers, async, timeouts.** `Container.newFiber` (`Container.java:111-113`) is
  `ScopedValue.where(xvm.Current, new Ctx(...)).run(task)` - it runs the task **synchronously on the
  calling thread**. There is no scheduler, no fiber queue, no `@Future`, no service dispatch, no
  re-entrancy control. `Ctx` has no service field. `TestServices` and `TestTimeouts` fail.
- **Child containers.** `Xvm.createContainer` exists and ids are allocated, but nothing creates one from
  Ecstasy code and the `Container` control/stats surface is three TODOs.
- **Reflection.** `ecstasy.reflect.Class` and `reflect.Type` are stubbed at runtime (observed), so
  anything reaching for a type at runtime gets `null`.
- **Memory accounting / GC.** Java's GC handles reclamation for free - that is the design's main
  dividend - but the container memory *limits* Ecstasy specifies are four empty methods.
- **Ecstasy-level debugging.** The interpreter has `runtime/Debugger.java` and `runtime/DebugConsole.java`;
  `org.xvm.javajit` has no reference to either. Java-level debugging works; `xec -d` against JIT code does not.
- **Debug output in the production path.** `JitConnector.invoke0Impl`'s `finally` block
  (`:159-181`) unconditionally deletes `./jasm` in the process working directory and re-dumps generated
  classes there on **every** run, and `ModuleLoader.dump` deliberately forces transitive class loading to
  do it. This ran during my sweep and created `jasm/` in the scratch directory.

## 7. Trajectory (facts)

34 commits have touched `org.xvm.javajit` since `540034a99` on 2025-05-13 - roughly 15 months, a
milestone every two to four weeks, consistently one author-team cadence, no gaps longer than five weeks.
Recent titles read as a steady climb up the type system rather than a push toward a release:

```
2026-08-27  03e159e0f  JIT Milestone: Tupple support; improved Ctx handling (#544)
2026-08-17  70422a197  JIT Milestone: Class of class generation and more core classes are compiling (#527)
2026-08-06  afd0464c1  JIT Milestone: Initial work on the long-arm naming convention (#515)
2026-07-31  14563dde3  JIT Milestone: More core classes are compiling now. (#512)
2026-06-30  1509f56a7  Java JIT project milestone; aligning the JIT and interpreter execution (#489)
```

**Inference** (not fact): "more core classes are compiling" twice in six weeks, plus the shape of
`JIT_LIST`, says the current working method is to widen the allow-list one library class at a time and
fix whatever breaks. That method has been productive, and it predicts the next milestones are more
library classes plus the remaining tuple-argument ops - not linking, services or containers.

## 8. What remains, in priority order

Ordered by how much each unblocks. Sizes are my estimate, relative to the milestones above (small = days,
medium = a milestone, large = several).

| # | task | why | size | depends on |
|---|---|---|---|---|
| 1 | **Make a code-generation miss a hard failure, not a stub.** Replace the `defaultLoad(); return;` fallback (`CommonBuilder.java:4085-4089`) with a thrown error carrying the class/method; keep the allow-list as an explicit opt-*out* if needed. | Today an unimplemented class produces a program that returns zero/null and exits 0. Every measurement of JIT progress - including this document's - is unreliable until failure is loud. | small | - |
| 2 | **A JIT conformance harness in `check`.** Run the `manualTests` corpus under `-J`, diff stdout against the interpreter, assert a known-pass list that can only grow. | Zero execution coverage today. This is the instrument that makes tasks 3-8 measurable and prevents regression; it also converts the ad-hoc sweep in section 2 into a ratchet. | medium | 1 (otherwise it green-lights silent stubs) |
| 3 | **Multi-module linking.** Implement `Linker.link()`'s three TODOs and make `JitConnector.loadModule` resolve and add the module's dependencies. | 9 of 69 corpus modules die on `missing class for constant`, and every realistic program imports `json`/`net`/`crypto`/`collections`/`web`. Nothing beyond a single-file program can run without it. | medium-large | - |
| 4 | **Close the codegen gaps the harness surfaces.** Concretely, in observed-frequency order: field initializers (`CommonBuilder.java:930`); `src=Primitive; dst=Ref` conversion; the `AssertionError` family (15 modules - needs triage, likely several distinct bugs); the `LocalVariableTable`/`VerifyError` bytecode bugs; non-integer constant loading (`Time`, `Duration`). | These are the actual per-module blockers for the 52 modules that pass interpreted and fail JITted. | large | 2 to prioritise honestly |
| 5 | **The 38 unimplemented ops**, tuple-argument calls and the 17 `PIP_*` first. | Each is a language feature that simply does not compile. `PIP_*` is 17 of the 38 and is one mechanism, so probably one change. | medium | - |
| 6 | **Reflection: real `reflect.Class` / `reflect.Type`.** | Stubbed to `null` today. Blocks `TestReflection`, the xunit modules, and any generic-introspecting library. It is also the deepest dependency of the remaining library classes. | large | 3 |
| 7 | **Services, fibers and async.** A real scheduler behind `Container.newFiber`, a service field in `Ctx`, `this:service`/`this:class`, `buildCreateRef`, `@Future`, timeouts. | The one whole *language pillar* with no implementation at all. Nothing concurrent can run. Nothing else on this list depends on it, which is why it is not higher - but the JIT cannot be called complete without it. | large | 3 |
| 8 | **Container semantics**: child containers, the control/stats surface, and the four `Ctx` memory-accounting methods. | Required by the Ecstasy container model (resource limits, nested containers). The interpreter has this; the JIT does not. | large | 7 |
| 9 | **Remove the `./jasm` dump from the run path** (`JitConnector.java:159-181`, `ModuleLoader.java:142`), behind a flag. | It deletes and rewrites a directory in the user's cwd on every single run, and forces transitive class loading to do it. Small, but it is user-visible damage and it pollutes any timing measurement. | small | - |
| 10 | **A JIT-side debugger**, or an explicit decision to rely on the Java debugger. | `xec -d` has no meaning under `-J`. Line and local-variable tables are already emitted, so the cheap answer may be "document that you attach a Java debugger". | medium | - |

Tasks 1, 2 and 9 are independently valuable, independently verifiable, and could land in a day each.

## 9. Not established

Stated plainly rather than guessed:

- **How much better master's 4/69 would be.** I did not build master. Two taxonomy rows are known to
  differ: `#544` adds the tuple-return ops, and master implements `Number.magnitude$get`
  (`NumberBuilder.java:121`) which the branch has commented out - that row alone is 4 modules. So the
  branch figure understates master by an unmeasured amount, plausibly 4-8 modules. Anyone acting on this
  document should re-run the sweep in section 2 against master before sizing task 4.
- **The root cause of the 15 `AssertionError` failures.** They share an exception type, not necessarily
  a cause. Triage needed before sizing task 4 precisely.
- **Why `operators`/`TestOperators` produce no output and no diagnostic.** They are on the allow-list
  (module class name starts with `anon`), so this is not the stub path. Unexplained.
- **How complete the 88-entry `JIT_LIST` is relative to the library.** `lib_ecstasy/src/main/x` holds 319
  `.x` files, but a file is not a class and many listed entries are wildcards, so "88 of 319" would be a
  false precision. The honest statement is that the list names most of `numbers`, `text`, `collections`
  and `maps`, and almost nothing of `reflect`, `fs`, `io`, `net`, `web`, `annotations`, `iterators`,
  `lang` or `temporal`.
- **Steady-state performance.** Nothing here measures it. The one timing above is startup-dominated and
  includes a debug dump; it says nothing about whether generated code is faster than the interpreter,
  which is the entire point of the project.
- **Whether any design document states the intended completion criteria.** `docs/` has no `jit/`
  directory before this file, and no design doc for the back-end was found.
