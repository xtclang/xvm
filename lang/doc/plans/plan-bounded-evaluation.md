# Bounded evaluation in the debugger: design for review

**Status (2026-10-23): proposed, not started.** This is R7's last feature in the
[execution plan](plan-embedded-execution.md#additive-delivery-and-acceptance): Debug Console expressions,
watches and hovers on any expression, conditions and logpoint messages beyond paths, and, through them, R10's
`toString()` on demand and computed properties. It needs a decision on the open questions at the end before any
code is written.

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
| 1, pure expressions | tier 0, plus literals and operators on `Int`, `String`, `Char`, `Boolean` and `Null` values | no | watches, conditions, hit-time logpoint parts |
| 2, code | any expression `EvalCompiler` compiles in the frame | yes, bounded | the Debug Console (`repl`); on demand: `toString()` and computed properties (R10) |

### Tier 1: pure expressions, evaluated in Java

A small expression language that the debugger evaluates itself, over the values it reads as paths, with no
Ecstasy code running:

- Literals: integers, strings, characters, `True`, `False`, `Null`.
- Operators: unary `-`, `!` and `~`; `*`, `/`, `%`, `+`, `-`, `<<`, `>>`, `&`, `^`, `|`; the comparisons
  `<`, `<=`, `>`, `>=`, `==` and `!=`; `&&`, `||`, `?:` and `? :`; and parentheses.
- Types: `Int` (Int64) arithmetic as Ecstasy does it, so an overflow or a division by zero is an error, not a
  wrapped value; `String` concatenation with `String`, `Int`, `Char` and `Boolean`; equality of those types,
  of enum values, and of anything with `Null`.
- Anything else, such as a call, `==` on two objects, an operator on another numeric type, or a computed
  property, fails with the reason, and points at the Debug Console.

Why a separate evaluator, rather than compiling with `EvalCompiler` and running only "pure" code: deciding
that compiled code is pure needs type information on every op, since an `Add` on a user type calls its
method. A small evaluator of a fixed grammar is easier to make safe, costs microseconds at a breakpoint hit,
needs no compilation per frame, and cannot change the program. The cost is a second implementation of a few
operators' semantics, which tests pin against the runtime's.

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
  stopped cannot work, and the deadline below ends it, with that reason.
- **Deadline.** Each evaluation has a time limit, 2 seconds by default for the console. Every line the
  evaluating fiber or its associated fibers reach checks the deadline, so loops end. Past it, the debugger
  raises an exception into the evaluation's frames, as it ends a terminated program's fibers, and they unwind
  to the lambda. A native call that blocks cannot be interrupted: the answer then fails at the deadline, the
  fiber stays busy until the call returns, and resume and terminate wait for it, with a message saying so.
- **Cancel.** DAP `cancel` (`supportsCancelRequest`) ends an evaluation the same way, before its deadline.
- **No stops inside.** Breakpoints, exception filters and a pending pause do not stop the evaluation's fibers.
  An exception the evaluation throws is its answer: "threw IllegalState: ...", with its stack.
- **Side effects are allowed, and visible.** What the expression changes stays changed. After it, the
  session sends DAP `invalidated` (areas `variables`), so the editors read the variables again. VS Code
  supports it; LSP4IJ does not handle `invalidated` yet, which would be a new upstream entry.
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

A watch or condition that needs code fails with the reason, rather than running it, until there is a reason to
opt in, such as a setting.

## Delivery

1. **Tier 1** (runtime evaluator, then the editors): conditions, logpoints and watches with operators. Low
   risk: no code runs. Shared playbook cases for a comparison condition and an arithmetic logpoint, in both
   editors, since IntelliJ sends conditions.
2. **Tier 2 for the Debug Console**: the call on the stopped fiber, the deadline, `cancel`, `invalidated`, and
   associated fibers. The hard part, and where the tests go: an expression that loops, one that throws, one
   that calls an idle service, one that calls a stopped service, one with a side effect, a breakpoint inside,
   and terminating or resuming during an evaluation.
3. **R10 on top of tier 2**: `toString()` on demand and computed properties as lazy variables, which also lets
   UP35 be checked in IntelliJ with our adapter.

## Questions to decide

1. **Tier 1's reach.** Is the grammar above enough for conditions and watches, or should tier 1 also cover
   other numeric types (`Int8` to `UInt128`, `Dec`, `Float64`) from the start?
2. **Watches and code.** Should a watch that needs code run it (tier 2) when the user asks, for example with a
   `!` prefix, or never?
3. **Deadlines.** 2 seconds for the console, and how long for an on-demand `toString()`? Should they be
   settings?
4. **Other services during an evaluation.** Only associated fibers run (proposed), or should the whole program
   run while the console evaluates, as Java debuggers do by default, at the cost of the stop's state changing
   underneath?
5. **Side effects in the console.** Allowed and announced with `invalidated` (proposed), or refused for
   expressions that assign?
