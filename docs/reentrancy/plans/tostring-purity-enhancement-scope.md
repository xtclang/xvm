# Enhancement scope: side-effect-free `TypeInfo` display (`toString` purity)

**Self-contained enhancement, portable to master as a single commit.** It does NOT depend on any
`lagergren/lazy-instance` runtime change; it only touches display code and one reflection method.
This document states the EXACT scope so it can be cherry-picked/reviewed on its own.

## The problem, stated plainly

An IDE debugger (IntelliJ) renders a value by calling its **no-arg `toString()`** — implicitly, every
time you hover a variable or expand it in the Variables/Watches view. For `TypeInfo` that was actively
dangerous, because the no-arg `toString()` produced the **full member dump**, and the member dump
mutates the very state you are inspecting. This bites in both the compiler and the runtime (more in
the runtime, where `TypeInfo`s are everywhere). **Merely looking at a `TypeInfo` in the debugger
changed it.**

### What actually mutates, and at what granularity

The important finding (this is the "why"): the unsafe boundary is **NOT "runtime vs not."** The old
`toString(boolean fRuntime)` conflated two independent things, and the flag was never the safety line:

- **`fRuntime` gates only the method-chain rendering.** In the method rows, `fRuntime == true` calls
  `MethodInfo.ensureOptimizedMethodChain(this)` — which **computes and caches** the optimized chain
  onto the live `MethodInfo`. That is the heaviest forcing.
- **But the plain `fRuntime == false` dump also mutates.** The Properties and Methods sections call
  `key.resolveNestedIdentity(pool, null)` to compute the `(v)` virtual marker, and that can **intern
  constants into the shared pool**. So *neither* boolean value is "pure."
- **Historically the `fRuntime == true` dump was never even reached.** Nothing in the tree called
  `toString(true)`/`toString(false)` explicitly (verified by grep); the only entry was the no-arg
  `toString()`, which delegated to `toString(false)`. The forcing branch was a latent landmine —
  reachable the moment anyone typed `x.toString(true)` into an evaluate window.

So the only rendering that is **guaranteed pure** is the one-line **header** (identity, progress,
format, flags). Any member walk touches `resolveNestedIdentity`. **The safety boundary is arity, not
the flag.**

## The design (why it is shaped this way)

- **No-arg `toString()` = the PURE header.** This is the one Java/the debugger call implicitly, so it
  must never walk members. Declared `abstract` on `TypeInfo` so a new subclass cannot forget it.
- **`toString(boolean fRuntime)` = the FULL member dump, EXPLICIT only.** Java never calls a
  parameterized overload implicitly, so the debugger never reaches it. Its `fRuntime` keeps its real,
  original meaning — render raw method bodies (`false`) or the optimized/cached chains (`true`). Also
  `abstract` on `TypeInfo`. We did **not** invent a new name like `describeForced`: `fRuntime` is
  already the recognizable flag, and the arity already carries the safe/full distinction.
- **No new method, no new field.** `fRuntime` was always a parameter (never a superclass field), and
  it stays one.

This is deliberately the *smallest* change that makes "inspect a `TypeInfo`" safe while keeping the
full dump exactly available to code that asks for it.

## What changed (exactly)

1. **`TypeInfo.java` (abstract base)**: `toString()` is now `public abstract String toString()` (was a
   concrete `return toString(false)`), forcing every subclass to provide a pure header;
   `toString(boolean fRuntime)` stays abstract with a javadoc spelling out that the safety boundary is
   arity and that `fRuntime` only chooses raw-bodies vs optimized-chains.
2. **`TypeInfoReal.java`**:
   - `toString()` returns the **PURE header** via a new private `appendHeader(StringBuilder)` helper
     (identity, progress, `format=`, `abstract`/`static`/`singleton`). No member walk, no
     `resolveNestedIdentity`, no chain computation, no allocation.
   - `toString(boolean fRuntime)` is the **full member dump** (the historical body), now starting from
     `appendHeader(...)`. Output is **byte-identical to the historical `toString(fRuntime)`**.
   - The per-section `int i` index loops were modernized into `stream().map(...).toList()` feeding a
     small `appendNumberedSection(StringBuilder, String, List<String>)` helper, plus a
     `renderMethod(...)` helper. Same order, same `[i]` numbering, same capped-method skip → identical
     output. `var` used only in new/changed code.
3. **`MethodInfo.java`**: `toString()` now delegates to a new `appendTo(StringBuilder sb)` primitive
   (append into a caller's buffer — allocation-lean for nested rendering). **Output is byte-identical**
   (`append(int)` and `append(String.valueOf(int))` render the same characters). Its `isOp()` interns
   the fixed system `clzOp` once (bounded, one-time) — the win is that the pure `TypeInfo.toString()`
   no longer reaches `MethodInfo` at all.
4. **`xRTType.java`**: `Type.dump()` now calls `ensureTypeInfo().toString(false)` instead of the no-arg
   `...toString()`. Old path: no-arg `toString()` → `toString(false)` (full dump). New path: explicit
   `toString(false)` (full dump). So **`Type.dump()`'s output is preserved byte-for-byte**; only the
   *debugger's* implicit no-arg view changed (to the header).

## Production-safety analysis (the point of the exercise)

The no-arg `toString()` output changed (full dump → header), so every non-display consumer was hunted:

- **The ONLY production consumer of `TypeInfo`'s `toString()` was `Type.dump()`** (`xRTType`, wrapping
  the string in an `xString` handle returned to Ecstasy). Fixed to `toString(false)` → **byte-identical**.
- No `TypeInfo` `toString()` result is used as a **cache/map key**, in **equals/hashCode**, or
  **parsed/compared** anywhere in `javatools/src/main`.
- No other `.toString(true)`/`.toString(false)` callers exist (the boolean overload was only ever
  reached via the no-arg `toString()`).
- No **test** pins `TypeInfo`/`MethodInfo` `toString()` content.
- `MethodInfo.toString()` output is **unchanged**, so any consumer of it is unaffected regardless.

## How to verify

- `TypeInfoDisplayPurityTest` (added): the no-arg `toString()` is a header that does **not** grow the
  shared `ConstantPool` across repeated renders and does not dump members; `toString(false)` still
  produces the full member dump (i.e. `Type.dump()` is intact).
- Gate: `:javatools:test :javatools_utils:test` and **`xdk:installDist`** (compiles + runs real
  Ecstasy, exercising `Type.dump()`) — both green.

## NOT included (deliberately out of scope)

- The other flagged display sites in `side-effect-free-tostring.md` (Op/Argument ambient reads,
  runtime handle/future displays, etc.). This commit does the two `TypeInfo`/`MethodInfo` leaves,
  which are at the bottom of most impure display chains.
- Making the full `toString(boolean)` dump itself non-mutating (e.g. skip `resolveNestedIdentity` for
  the `(v)` marker, or render already-computed chains + `[chain deferred]` instead of forcing). Today
  the explicit dump keeps its historical, possibly-forcing behavior.
- Extending `appendTo` recursively (e.g. `MethodBody.appendTo`) to make the forced dump fully
  allocation-lean.
- A banned-callee enforcement ratchet across all display methods.
