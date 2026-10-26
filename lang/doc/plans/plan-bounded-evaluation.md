# Bounded evaluation in the debugger: design for review

**Status (2026-10-26): decided and done. Tier 1 (`fda3dc3e8` runtime, `0540055e9` editors, shared X313), and
tier 2: running code on the stopped fiber (`d77fb9b51`), ending it at its deadline, on cancel, resume and terminate
(`72c7bdbbc`), the server and worker (`68491020b`), the editors (`03a422f6c`, shared X314), and the
`evaluationRunsProgram` opt-in (`70cdd636f`, shared X315); and on top of it R10's values on demand, an object's
computed properties and `toString()` as lazy variables (`1d88e086d`, shared X316).** This
is R7's last feature in the
[execution plan](plan-embedded-execution.md#additive-delivery-and-acceptance): Debug Console expressions,
watches and hovers on any expression, conditions and logpoint messages beyond paths, and, through them, R10's
`toString()` on demand and computed properties. The questions at the end are decided.

## What there is today

- **Paths, without running code (R7m).** `SessionDebugger.evaluate(frame, path)` reads a path such as
  `point.x` or `ints[1]`: a name, then fields and array elements. The waiting fiber reads it on its own thread,
  and nothing of the program runs. Watches, hovers, the Debug Console, conditions (R9a) and logpoint `{...}`
  parts all use it. Anything else fails with "is not evaluated: only variables, their fields and array elements
  are, such as point.x or ints[1]."
- **The console debugger's `E` and `BC` (javatools `DebugConsole`).** `EvalCompiler` compiles an expression in
  a frame's context into a lambda whose arguments are the frame's registers, as a child of the frame's method.
  The console runs it as a call from the stopped frame: `frame.call1(lambda, ...)` returns `R_CALL`, the
  interpreter runs the lambda on the same fiber, and a continuation takes the result or exception and returns to
  the debugger. Conditional breakpoints compile `{return <condition>;}` once per breakpoint, typed as `Boolean`.
  It has no time limit: an expression that loops forever hangs the program.
- **The runtime allows the call.** The line hook (`Nop` calls `Debugger.checkBreakPoint(frame, pc)`) and the
  exception hook (`ServiceContext`, "the debugger made a natural call") both accept `R_CALL`, so a debugger can
  run code on a fiber it stopped. `Fiber.isAssociated()` relates a fiber to the fibers it calls in other
  services, which is how a step into follows a call across services.

## The problem to bound

Running the program's code from a stop is what makes an evaluation useful (operators, method calls,
`toString()`, computed properties), and what makes it dangerous:

1. **It may not end.** A loop, a recursion, or a native call that blocks.
2. **It may wait forever.** A stopped fiber holds its service's thread while it waits. An expression that calls
   into a service whose fiber is stopped can never get an answer, so the evaluation deadlocks. A call into a
   service that is merely idle works.
3. **It may change the program.** A method can assign fields, print, or start work. A watch is evaluated again
   at every stop, a condition at every hit of its line, so a side effect there repeats silently.
4. **It may stop.** The evaluated code can reach a breakpoint, throw, or be reached by a pause.
5. **It is slow at a breakpoint hit.** Compiling with `EvalCompiler` takes milliseconds, and running a call
   costs far more than reading a path. A condition in a hot loop must stay cheap.

## Proposal: three tiers, chosen by what asks

| Tier | What it evaluates | Runs the program's code | Used for |
|------|-------------------|-------------------------|----------|
| 0, paths (done) | variables, `this`, fields and array elements | no | hovers; the Variables view |
| 1, pure expressions | tier 0, plus literals and operators on numbers of every built-in numeric type, `String`, `Char`, `Boolean` and `Null` | no | watches, conditions, hit-time logpoint parts |
| 2, code | any expression `EvalCompiler` compiles in the frame | yes, bounded | the Debug Console (`repl`); on demand: `toString()` and computed properties (R10) |

### Tier 1: pure expressions, evaluated in Java

A small expression language that the debugger evaluates itself, over the values it reads as paths, with no
Ecstasy code running:

- Literals: integers and decimals as the compiler's own lexer reads them (`0x1F`, `1_000`, `1.5`), strings,
  characters, `True`, `False`, `Null`. A path's index may be any `Int` expression, such as `ints[i - 1]`.
- Operators: unary `-`, `!` and `~`; `*`, `/`, `%`, `+`, `-`, `<<`, `>>`, `>>>`, `&`, `^`, `|`; the
  comparisons `<`, `<=`, `>`, `>=`, `==` and `!=`, chained as in Ecstasy (`0 <= i < n`); `&&`, `||`, `?:`
  and `? :`; and parentheses, all with Ecstasy's precedence.
- Types: every built-in numeric type, `Int8` to `Int128` and `UInt8` to `UInt128`, `IntN` and `UIntN`,
  `Dec32` to `Dec128`, `Float8e4` to `Float64` and `BFloat16`, `Bit` and `Nibble`. Each operator on them runs
  the runtime's own native operation for that type, the Java code the interpreter runs for it, which runs no
  Ecstasy code, so overflow, division by zero and rounding are exactly the program's. A literal takes the
  type of the other operand, as the compiler converts it, and between two literals an operator computes what
  the compiler would; an operator between two different types is refused, as Ecstasy's compiler refuses it.
  Also comparisons of `Char` and `String`; `==` and `!=` of enum values, `Boolean` among them, and of
  anything with `Null`; and `&`, `|` and `^` on `Boolean`.
- Anything else, such as a call, `==` on two objects, or a computed property, fails with the reason. An
  operation that would run Ecstasy code is undone before that code runs, and its message names the method
  it would have run.
- Found while building it (2026-10-23): joining strings is not native in the runtime. A `String`'s `+`
  calls `toString()` on its right side, and that is Ecstasy code even for `String`, `Char` and the numbers
  (`String.toString()`, and for an `Int` the `Stringable.toString()` it inherits). Tier 1 therefore refuses
  `s + i`: "s + i would run Stringable.toString(), which the debugger does not run here." It joins strings once those methods are native,
  and tier 2 joins them by running them. Logpoints are not affected: a `{...}` part's value is shown as the
  Variables view shows it.
- Going through the runtime's native operations is also a test of the runtime's model of these types: a type
  whose operation is missing, or not native, shows up as an expression tier 1 cannot evaluate.

Why a separate evaluator, rather than compiling with `EvalCompiler` and running only "pure" code: deciding
that compiled code is pure needs type information on every op, since an `Add` on a user type calls its
method. A small evaluator of a fixed grammar is easier to make safe, costs microseconds at a breakpoint hit,
needs no compilation per frame, and cannot change the program. The cost is the parser and the dispatch to the runtime's operations; the
operations themselves are the runtime's.

This tier makes conditions such as `i > 2 && name == "x"` and logpoints such as `{n + 1}` work, safely, at
every hit, in both editors (IntelliJ sends conditions today).

### Tier 2: code, on the stopped fiber, bounded

For the Debug Console, and on demand for `toString()` and computed properties:

- **Where it runs.** On the fiber that owns the frame, as the console debugger does it. That fiber leaves its
  wait, `checkBreakPoint` returns `R_CALL` into the compiled lambda, and the lambda's continuation records the
  result and takes the fiber back into the same stop, without a new `stopped` event. Other fibers stay where
  they wait, so the stopped program does not run on.
- **Calls into other services.** Fibers associated with the evaluating one (`Fiber.isAssociated()`) do not
  wait at lines while it runs, so a call into an idle service works. A call into a service whose fiber is
  stopped cannot work, and the deadline below ends it, with that reason. Every other fiber stays where it
  waits, so what the user inspects does not change underneath (decided 2026-10-23).

  Why this, and not letting the whole program run as Java debuggers do by default: Java threads share memory
  and locks, so a method evaluated on a stopped thread can need a lock that another stopped thread holds, and
  only letting every thread run avoids that deadlock. An Ecstasy service runs one fiber at a time and shares no
  mutable state with others, so an evaluation can only wait on another service by calling it, and the fibers
  that call reaches run. What it cannot do, and the deadline ends with the reason: call into a service whose
  own fiber is stopped mid-method, and wait for work that an unrelated fiber would do, such as a future that
  another service completes.
- **Opt in to running the whole program.** A launch can let the whole program run while an evaluation does
  (`evaluationRunsProgram` in VS Code's `launch.json`, an option of IntelliJ's Ecstasy Application
  configuration, off by default), for programs that need it. Its fibers then run as without a debugger,
  without stopping at breakpoints, and wait at their next line once the evaluation ends; the stop's frames
  and variables may have changed by then, which `invalidated` tells the editors.
- **Documented where it is built.** The implementation explains this model in a long comment where it lives,
  in `SessionDebugger`: which fibers run during an evaluation and why, how the deadline and cancel end it,
  and its known limits (a call into a stopped service, waiting on unrelated fibers, a native call that blocks).
- **Deadline.** Each evaluation has a fixed time limit (decided 2026-10-23): 2 seconds for the console, and 1
  second for a value the user opens on demand, `toString()` or a computed property (R10). Every line the
  evaluating fiber or its associated fibers reach checks the deadline, so loops end. Past it, the debugger
  raises an exception into the evaluation's frames, as it ends a terminated program's fibers, and they unwind
  to the lambda. A native call that blocks cannot be interrupted: the answer then fails at the deadline, the
  fiber stays busy until the call returns, and resume and terminate wait for it, with a message saying so.
- **Cancel.** DAP `cancel` (`supportsCancelRequest`) ends an evaluation the same way, before its deadline.
- **No stops inside.** Breakpoints, exception filters and a pending pause do not stop the evaluation's fibers.
  An exception the evaluation throws is its answer: "threw IllegalState: ...", with its stack.
- **Side effects are allowed, and visible.** What the expression changes stays changed. After it, the
  session sends DAP `invalidated` (areas `variables`), so the editors read the variables again. VS Code
  supports it; LSP4IJ does not handle `invalidated` yet: UP40, fixed in LSP4IJ pull request
  [#1766](https://github.com/redhat-developer/lsp4ij/pull/1766) (2026-10-23).
- **One at a time.** A second evaluation while one runs waits for it; an evaluation from a frame of another
  fiber runs on that fiber.
- **Compilation.** `EvalCompiler` adds the lambda to the method, and removes it afterwards, as `E` does; the
  program is stopped meanwhile. A compile error is the answer, with the compiler's message.

### Which tier each request uses

| Request | Tier | Why |
|---------|------|-----|
| `evaluate`, context `hover` | 1 | Hovering must not change the program; editors hover often. |
| `evaluate`, context `watch` | 1 | Watches are evaluated again at every stop. |
| `evaluate`, context `repl` | 2 | Typed by the user, once, who expects code to run. |
| `evaluate`, context `clipboard` | 1 | As a watch. |
| breakpoint `condition`, logpoint `{...}` | 1 | At every hit; must be cheap and change nothing. |
| a lazy computed property, `toString()` on demand (R10) | 2 | Only when the user clicks for it (`presentationHint.lazy`). |

A watch or condition that needs code fails with the reason, rather than running it (decided 2026-10-23).
Letting a watch run code when the user opts in, such as with a prefix or a setting, is a possible later
improvement, not part of this plan.

## Delivery

1. **Tier 1** (runtime evaluator, then the editors): conditions, logpoints and watches with operators. Low
   risk: no code runs. Shared playbook cases for a comparison condition and an arithmetic logpoint, in both
   editors, since IntelliJ sends conditions. Done: `fda3dc3e8` (`PureExpression`) and `0540055e9`, shared X313, whose
   logpoint IntelliJ checks once LSP4IJ sends log messages (UP32).
2. **Tier 2 for the Debug Console**: the call on the stopped fiber, the deadline, `cancel`, `invalidated`,
   associated fibers, and the opt-in to running the whole program. The hard part, and where the tests go: an
   expression that loops, one that throws, one that calls an idle service, one that calls a stopped service,
   one with a side effect, a breakpoint inside, terminating or resuming during an evaluation, and the opt-in.
   In five steps, each its own commit:
   1. R7r, the runtime (`d77fb9b51`): `SessionDebugger.run()` and the fiber waiting again in the same stop;
      a thrown exception, a side effect, a breakpoint inside, an idle service and a resume during a run are
      tested. A run's value shows as its `toString()`, an expression without one runs as a statement, and
      only the run's fiber and the fibers of other services that it calls run.
   2. R7s, the runtime (`72c7bdbbc`): the deadline, cancel, and resume and terminate ending a run; the loop,
      a loop that catches what ends it, and the stopped service are tested. The host passes the limit to
      `run()`, so the 2 and 1 seconds are the server's to choose (R7t). Unlike the design above, resume and
      terminate do not wait for a fiber that a native call blocks: its answer fails at once, and it unwinds
      once the call returns.
   3. R7t, the server and worker (`68491020b`): a `repl` line is read as tier 1 reads it first, which runs
      nothing and shows a value as the Variables view does, and what that refuses runs as code, for at most 2
      seconds; `cancel` ends it by the request's `seq`, which `DebugMessageEndpoint` hands the server, also
      before its code starts; `invalidated` (`variables`) follows once code has run, for a client that takes it.
      A call with an effect, a cancelled loop and a loop at its limit are tested with a real worker.
   4. R7u, the editors and shared X314 (`03a422f6c`): VS Code's Debug Console and IntelliJ's Evaluate Expression,
      whose origin LSP4IJ maps to `repl`, run code; X314 checks a call's effect, the time limit and the exit in both,
      cancel and `invalidated` in VS Code, since LSP4IJ never sends `cancel` (UP41) nor handles `invalidated`
      (UP40).
   5. R7v, the `evaluationRunsProgram` opt-in (`70cdd636f`): a launch argument, in VS Code's `launch.json` and as
      a checkbox of IntelliJ's Ecstasy Application configuration, that makes a console line's run one of the
      whole program (`SessionDebugger.run(..., program)`): every other fiber leaves its wait and runs as without
      a debugger, with no breakpoint, logpoint, step, pause or exception stop and the clock running, and waits
      in the same stop again at its next line once the run ends, its old frame IDs reading nothing; `invalidated`
      is then for `all` areas. Shared X315 calls into a service whose fiber the stop held mid-method.
3. **R10 on top of tier 2**: `toString()` on demand and computed properties as lazy variables, which also lets
   UP35 be checked in IntelliJ with our adapter. Done: `1d88e086d` (R10a). An object's fields are followed by a
   lazy variable for each public computed property and one for `toString()`; reading one runs the getter, or
   `toString()`, as a run on the stopped fiber with the one-second limit, without compiling anything. A lazy
   read sends no `invalidated`, which would fold the value just shown. Shared X316; UP35 works natively with
   LSP4IJ #1768.

## Questions to decide

1. ~~Tier 1's reach.~~ Decided 2026-10-23: every built-in numeric type from the start, through the runtime's
   native operations, which also checks the runtime's model of them.
2. ~~Watches and code.~~ Decided 2026-10-23: never, for now; opting in is a later improvement.
3. ~~Deadlines.~~ Decided 2026-10-23: fixed, 2 seconds for the console and 1 second for values opened on
   demand; a setting only if a program needs one.
4. ~~Other services during an evaluation.~~ Decided 2026-10-23: only the fibers the evaluation calls into
   run, documented in the code with why and its limits, with a launch option to let the whole program run.
5. ~~Side effects in the console.~~ Decided 2026-10-23: allowed, and announced with `invalidated`, as the first
   implementation.

All questions are decided; the plan is ready to start with tier 1.
