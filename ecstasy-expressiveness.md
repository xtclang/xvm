# Expressiveness in Ecstasy: Scope Functions, Expressions, and Fluency

An analysis of where the Ecstasy language forces imperative patterns and how it
could become more expressive. Covers three interconnected topics: scope functions,
the statement/expression divide, and `void` as a non-type.

## Table of contents

### Part I: Scope functions

1. [What are scope functions?](#what-are-scope-functions)
   - [Why they matter](#why-they-matter)
   - [Scope functions beyond Kotlin](#scope-functions-beyond-kotlin)
2. [How Kotlin's scope functions work](#background-how-kotlins-scope-functions-work)
3. [Ecstasy language inventory](#ecstasy-language-inventory)
   - [Available building blocks](#available-building-blocks)
   - [Key gap: no receiver lambdas](#key-gap-no-receiver-lambdas)
4. [Why scope functions feel missing in Ecstasy](#why-scope-functions-feel-missing-in-ecstasy)
   - [The core problem: no expression form for object-scoped operations](#the-core-problem-object-scoped-operations-have-no-expression-form)
   - [The cost of not having them](#the-cost-of-not-having-them)
   - [Why Ecstasy is especially well-suited](#why-ecstasy-is-especially-well-suited-for-scope-functions)
   - [Counter-arguments and risks](#counter-arguments-and-risks)
5. [Proposed design for Ecstasy](#proposed-design)
   - [Where to add the methods](#where-to-add-the-methods)
   - [The five functions](#the-five-functions)
   - [Why let/run collapse without receiver lambdas](#why-letrun-collapse)
   - [Refined type preservation](#refined-type-preservation)
6. [Usage examples (Kotlin vs. proposed Ecstasy vs. today)](#usage-examples)
7. [Implementation options](#implementation-options)
   - [Option 1: Methods on Object (recommended)](#option-1-methods-on-object-recommended)
   - [Option 2: Top-level generic functions only](#option-2-top-level-generic-functions-only)
   - [Option 3: Compiler-supported receiver lambdas (aspirational)](#option-3-compiler-supported-receiver-lambdas-aspirational)
   - [Option 4: Hybrid -- methods on Object + compiler narrowing](#option-4-hybrid----methods-on-object--compiler-narrowing-recommended-path)
8. [Comparison with existing Ecstasy idioms](#comparison-with-existing-ecstasy-idioms)
9. [Scope functions recommendation](#recommendation)

### Part II: Codebase analysis

10. [Imperative patterns in the XDK and platform code](#analysis-imperative-patterns-in-the-xdk-and-platform-code)
    - [Category 1: "Lazy Ecstasy" -- better code is possible today](#category-1-lazy-ecstasy----the-language-already-allows-better)
    - [Category 2: "Missing construct" -- the language forces imperative code](#category-2-missing-construct----the-language-forces-imperative-code)
    - [Summary of findings](#summary-of-findings)
    - [Beyond scope functions: other language gaps](#beyond-scope-functions-other-language-gaps-that-force-imperative-patterns)

### Part III: The expression problem

11. [void, if, and the cost of statements](#the-expression-problem-void-if-and-the-cost-of-statements)
    - [void is not a type](#void-is-not-a-type)
    - [How void breaks fluency](#how-void-breaks-fluency)
    - [The if-as-statement limitation](#the-if-as-statement-limitation)
    - [What other languages learned](#the-case-for-expressions-what-other-languages-learned)
    - [How hard would it be to make everything an expression?](#how-hard-would-it-be-to-make-everything-an-expression)
    - [What Ecstasy could do today (Lazy Ecstasy)](#what-ecstasy-could-do-today-lazy-ecstasy)
    - [Language comparison table](#comparison-with-other-languages)
    - [Expressions vs. statements: the philosophical argument](#the-philosophical-argument-expressions-vs-statements)
    - [Expression problem recommendation](#recommendation-1)

### Part IV: What else would developers expect in 2026?

13. [Sealed types, pattern matching, and other modern features](#what-else-would-developers-expect-in-2026)
    - [Sealed types: exhaustive hierarchies as a programming tool](#sealed-types-exhaustive-hierarchies-as-a-programming-tool)
    - [Pattern matching: beyond switch and .is()](#pattern-matching-beyond-switch-and-is)
    - [Result types: structured error handling without exceptions](#result-types-structured-error-handling-without-exceptions)
    - [Extension methods: ad-hoc method addition](#extension-methods-ad-hoc-method-addition)
    - [Collection factory functions](#collection-factory-functions)
    - [Summary: what's missing vs. what's deliberate](#summary-whats-missing-vs-whats-deliberate)

### Appendix

14. [Kotlin scope function decision tree](#appendix-kotlin-scope-function-decision-tree)

---

## What are scope functions?

Kotlin's standard library provides five **scope functions** -- small higher-order
functions whose sole purpose is to execute a block of code in the context of an
object. They don't introduce new functionality; instead they give structure to code
that operates on an object, reducing boilerplate, eliminating temporary variables, and
making intent explicit.

The official Kotlin documentation lives here:
https://kotlinlang.org/docs/scope-functions.html

### Why they matter

Without scope functions, common patterns require either temporary variables or
repetitive qualification:

```kotlin
// Without scope functions -- verbose, scattered
val user = createUser()
val validated = validate(user)
logger.info("Created: ${user.name}")
database.save(validated)

// With scope functions -- intent is clear, data flows visually
createUser()
    .also { logger.info("Created: ${it.name}") }
    .let { validate(it) }
    .let { database.save(it) }
```

Scope functions shine in several recurring situations:

- **Null-safe scoping (`let`)** -- Execute a block only when a nullable value is
  non-null, keeping the logic in expression position instead of requiring an `if`
  block and a mutable variable:
  ```kotlin
  val length = name?.let { it.trim().length } ?: 0
  ```

- **Object configuration (`apply`)** -- Group property assignments on a newly
  constructed object so the configuration is visually tied to the construction,
  and the whole thing is a single expression:
  ```kotlin
  val button = Button().apply {
      text = "Submit"
      isEnabled = true
  }
  ```

- **Side effects in a chain (`also`)** -- Insert logging, validation, or metrics
  into a fluent pipeline without interrupting the data flow:
  ```kotlin
  fetchConnection()
      .also { log.info("Connected to ${it.url}") }
      .execute(query)
  ```

- **Grouping operations (`with`, `run`)** -- Operate on an object's members without
  repeating its name on every line, making the block read like a focused context:
  ```kotlin
  val csv = with(report) {
      "$title,$date,$total"
  }
  ```

- **Temporary scoping (`let`, `run`)** -- Confine a value to a narrow scope to
  prevent accidental reuse, or to convert a statement-oriented flow into an
  expression:
  ```kotlin
  val hex = bytes.let { encode(it) }.let { format(it) }
  ```

These five functions are among the most heavily used parts of Kotlin's standard
library. Their absence is one of the first things Kotlin developers notice when
moving to another language.

### Scope functions beyond Kotlin

The patterns that Kotlin's scope functions capture are not Kotlin-specific -- they
appear across a wide range of languages, which suggests they address a fundamental
need in how developers structure code around objects.

**Ruby** has had `tap` (equivalent to `also` -- execute a block for side effects,
return the original object) and `then`/`yield_self` (equivalent to `let` -- pass
the object to a block, return the block's result) since Ruby 2.5. These are methods
on `Object`, available on every value, and are idiomatic in Ruby chains.

**Rust** provides `let` bindings with pattern matching that serve the scoping purpose,
and the popular `tap` crate adds `.tap()` and `.pipe()` methods to every type --
direct equivalents of `also` and `let`. The Rust RFC process has seen repeated
proposals to add these to `std` because the pattern is so common.

**Swift** doesn't have scope functions in its standard library, but the community
has converged on a widely-used pattern: the `with` free function
(`func with<T>(_ value: T, _ block: (inout T) -> Void) -> T`) appears in countless
Swift projects and style guides. Apple's own SwiftUI framework uses a builder/closure
pattern that achieves the same grouping effect as `apply`.

**Scala** provides similar capabilities through `pipe` and `tap` (added in Scala
2.13 on `implicit class ChainingOps[A]`), giving every value `.pipe(f)` (like `let`)
and `.tap(f)` (like `also`). Earlier Scala versions achieved this through the
ubiquitous `implicit class` pattern -- the community built these independently,
proving the demand predates any single language's implementation.

**JavaScript/TypeScript** lacks built-in scope functions, but the TC39 pipeline
operator proposal (`value |> fn`) has been in progress for years, driven by exactly
the same desire to chain transformations without temporary variables. Libraries like
Lodash provide `_.tap()` and `_.thru()` for the same purpose.

**Dart** has the cascade operator (`..`) built into the language, which is essentially
a syntactic `apply` -- it executes a series of operations on an object and returns
the object itself: `Button()..text = 'OK'..enabled = true`.

**Smalltalk**, one of the oldest object-oriented languages, has had `yourself`
(return the receiver after a cascade of messages) since the 1970s -- the conceptual
ancestor of `also`/`apply`.

The convergence is clear: whether expressed as methods (`tap`, `pipe`, `let`),
operators (`|>`, `..`), or language constructs (`with`), every mature language
ecosystem eventually grows mechanisms for scoped object access, chained
transformations, and side-effect insertion. These are not syntactic sugar for a
particular language's idioms -- they are fundamental patterns of compositional
programming that reduce noise and make intent explicit.

## Background: how Kotlin's scope functions work

Kotlin's five scope functions all execute a block of code in the context of an object.
They differ along two axes:

| Function | Object reference | Return value | Kotlin signature |
|----------|-----------------|--------------|------------------|
| `let`    | `it` (parameter) | block result | `T.let(block: (T) -> R): R` |
| `run`    | `this` (receiver) | block result | `T.run(block: T.() -> R): R` |
| `also`   | `it` (parameter) | original `T` | `T.also(block: (T) -> Unit): T` |
| `apply`  | `this` (receiver) | original `T` | `T.apply(block: T.() -> Unit): T` |
| `with`   | `this` (receiver) | block result | `with(receiver: T, block: T.() -> R): R` |

The **receiver lambda** (`T.() -> R`) is the key language feature that powers `run`,
`apply`, and `with`: inside the block, `this` refers to the scoped object, so its
members can be accessed without qualification.

## Ecstasy language inventory

Before proposing a design, here is what Ecstasy already provides and what it lacks.

### Available building blocks

1. **Lambdas and closures** -- full support:
   ```x
   function Int(String) f = s -> s.size;
   function void()      g = () -> { console.print("hello"); };
   ```

2. **Generics on methods** -- full support:
   ```x
   <Result> Result transform(function Result(Element) fn) { ... }
   ```

3. **Nullable types and safe-call** -- `String?`, `?.`, `?:`, conditional binding
   (`if (String s := expr)`), and conditional return (`conditional String foo()`).

4. **Fluent `this`-returning patterns** -- used throughout the standard library
   (e.g., `Appender.add()` returns `Appender`).

5. **`using` blocks** -- resource-scoping with automatic `close()`:
   ```x
   using (new Timeout(Duration:1S)) { ... }
   ```

6. **Mixins** -- can add methods to types via `into`:
   ```x
   mixin Interval<Element extends immutable Sequential>
           into Range<Element>
           implements Iterable<Element> { ... }
   ```

7. **`this:class`, `this:struct`, `this:service`** -- compiler-recognized
   self-referencing accessors.

### Key gap: no receiver lambdas

Ecstasy has no equivalent of Kotlin's `T.() -> R` -- a function type where `this`
inside the lambda body is rebound to an instance of `T`. All Ecstasy lambdas receive
their context object as an explicit parameter, never as an implicit `this`.

This is the single largest difference. It means:
- Kotlin's `apply { name = "foo"; age = 42 }` (accessing properties unqualified)
  cannot be expressed with the same terseness.
- The Ecstasy equivalents must pass the object as a named parameter (`it` by
  convention).

## Why scope functions feel missing in Ecstasy

Ecstasy is a language that values expressiveness, safety, and composition. It has
lambdas, generics, closures, conditional returns, safe-call operators, and fluent
`Appender`-style APIs. On paper, it has all the ingredients for highly compositional
code.

And yet, writing real Ecstasy -- as seen in the XDK and the platform project --
repeatedly forces developers out of expression-position into statement-position
for operations that are conceptually simple. The gap isn't in *what* the language
can compute, but in *how* it lets you structure that computation.

### The core problem: object-scoped operations have no expression form

Consider a pattern that appears dozens of times in the platform code:

```x
// Create an object, configure it, use it
JsonObject stats = json.newObject();
stats["deployed"] = True;
stats["active"]   = host.active;
stats["storage"]  = utils.storageSize(host.homeDir).toIntLiteral();
return stats.makeImmutable();
```

This is five statements to express one concept: "return an immutable JSON object
with these three fields." The `stats` variable exists only as a handle for the
configuration phase -- it has no independent purpose, it's never passed elsewhere,
and it's never read again after the return. But the language offers no way to
express "create, configure, and return" as a single expression.

With `apply`:

```x
return json.newObject().apply(s -> {
    s["deployed"] = True;
    s["active"]   = host.active;
    s["storage"]  = utils.storageSize(host.homeDir).toIntLiteral();
}).makeImmutable();
```

The variable is gone. The configuration is visually tied to the construction. The
entire thing is one expression that can be inlined into a function argument, a
return statement, or a larger chain.

### The cost of not having them

Without scope functions, Ecstasy developers pay a recurring tax:

1. **Name pollution**: Every intermediate value needs a name, even when it's used
   once and immediately discarded. These names clutter the local scope and give the
   reader false signals that the variable matters beyond its next use.

2. **Forced mutability**: When a conditional or side effect needs to happen in the
   middle of a computation, the developer must break the expression into statements,
   introducing a `var` that could have been a `val`. The analysis section documents
   5+ instances of this pattern in the platform code alone.

3. **Broken chains**: Fluent APIs like iterators, builders, and collection pipelines
   work beautifully in Ecstasy -- until you need to log, validate, or observe an
   intermediate value. Without `also`, the chain must be broken, a temporary
   assigned, the side effect performed, and the chain resumed (or abandoned).

4. **Visual separation of intent**: Construction and configuration are one logical
   operation, but they appear as separate, disconnected statements. The reader must
   mentally group them and verify that no other code intervenes between construction
   and use.

5. **Composability loss**: A five-statement block cannot be passed as an argument,
   returned from a function, or composed with other operations. A single expression
   can. Scope functions convert statement-blocks into expressions, which is the
   fundamental operation of compositional programming.

### Why Ecstasy is especially well-suited for scope functions

Ecstasy has characteristics that would make scope functions *more* natural than
in most languages:

- **Everything is an Object**: Unlike Java (with primitives) or Kotlin (with
  `Any?` vs `Any`), Ecstasy's `Object` is truly universal. Adding methods to
  `Object` gives them to every value with zero exception cases.

- **Closures are first-class**: Ecstasy's lambda syntax is clean, closures
  capture variables correctly, and higher-order functions are idiomatic
  throughout the XDK.

- **Safe-call already exists**: The `?.` operator is already in the language.
  Adding `let` as a method on `Object` means `name?.let(...)` works
  automatically -- no new syntax required. This is arguably the single
  highest-value scope function use case (null-safe scoping in expression
  position).

- **The `Appender` pattern proves the concept**: The XDK already has
  `Appender.add()` returning `Appender` for fluent chaining. Scope functions
  generalize this: `also` is `add()` for any object, `apply` is "configure
  and return self" for any object.

- **The `with()` method already exists on `const` types**: Types like
  `WebAppInfo.with(hostName=..., provider=...)` already use the name `with`
  for immutable updates. A top-level `with(obj, block)` function would
  complement this existing pattern naturally.

### Counter-arguments and risks

Adding scope functions is not without trade-offs:

1. **Naming conflicts**: `let` and `run` are common English words. If existing
   Ecstasy code uses `let` or `run` as local variable names, adding methods with
   those names to `Object` could cause ambiguity. Mitigation: the compiler can
   prefer local names over `Object` methods (as Kotlin does), or alternative
   names can be chosen (`pipe`/`tap` instead of `let`/`also`).

2. **Overuse and readability**: In Kotlin, scope function overuse is a documented
   anti-pattern. Chains of `.let { }.also { }.run { }` can become harder to read
   than plain sequential code. The Kotlin style guide recommends limiting nesting
   to one or two scope functions. This is a cultural/style-guide concern, not a
   language design one.

3. **Object pollution**: Adding four methods to `Object` adds them to every
   type's API surface. In IDE autocompletion, `let`, `run`, `also`, `apply`
   will appear alongside domain methods. This is a real cost, but it's one that
   Kotlin, Scala, and Ruby have all accepted -- and developers in those
   ecosystems report that the benefits far outweigh the noise.

4. **Self-type challenge**: Without compiler support for self-types, `also` and
   `apply` return `Object`, not the concrete type. This forces `.as(Widget)` casts
   at the end of chains, which is ugly and partially defeats the purpose. This is
   a real design challenge that needs a clean solution (see Implementation Options).

5. **Debugging complexity**: Scope function chains can be harder to step through
   in a debugger than sequential statements. Each link in the chain is a lambda
   invocation. This is mitigable with good debugger support (Kotlin's debugger
   handles this well), but it's a consideration for a young ecosystem.

## Proposed design

### Where to add the methods

`Object` is the root interface of every Ecstasy type. Adding scope functions directly
to `Object` makes them universally available -- the same strategy Kotlin uses (extension
functions on `Any?`).

Since `Object` is an interface in Ecstasy, new default methods can be added without
breaking any existing implementation.

### The five functions

```x
interface Object
        extends Comparable {

    // ... existing members ...

    /**
     * Execute a block with this object as the parameter and return the block's result.
     *
     * Use `let` to chain transformations, introduce a named binding in an expression
     * context, or scope a nullable value after a safe-call.
     *
     *   result = computeValue().let(v -> validate(v));
     *   name   = path?.let(p -> p.filename.toString());
     */
    <Result> Result let(function Result(Object) apply) {
        return apply(this);
    }

    /**
     * Execute a block with this object as the parameter and return the block's result.
     *
     * Identical to `let` in Ecstasy (since there are no receiver lambdas, `run` and
     * `let` collapse to the same form). Provided for Kotlin familiarity and to signal
     * intent: use `run` when the block computes a derived value using the object's
     * members; use `let` when the block transforms or validates the object itself.
     *
     *   label = widget.run(w -> $"{w.name} ({w.x}, {w.y})");
     */
    <Result> Result run(function Result(Object) compute) {
        return compute(this);
    }

    /**
     * Execute a side-effecting block with this object as the parameter, then return
     * the original object unchanged.
     *
     * Use `also` for logging, validation, or other side effects in a fluent chain
     * without interrupting the data flow.
     *
     *   connection = openConnection()
     *       .also(c -> log.info($"Opened {c.url}"))
     *       .also(c -> metrics.increment("connections"));
     */
    Object also(function void(Object) block) {
        block(this);
        return this;
    }

    /**
     * Execute a configuration block with this object as the parameter, then return
     * the original object.
     *
     * Use `apply` to configure a freshly constructed object in a fluent expression.
     *
     *   widget = new Widget().apply(w -> {
     *       w.name    = "OK";
     *       w.visible = True;
     *       w.onClick = handler;
     *   });
     */
    Object apply(function void(Object) configure) {
        configure(this);
        return this;
    }
}
```

And as a top-level function (not a method, since `with` takes the receiver as its
first argument -- matching Kotlin's `with(obj) { ... }` call-site):

```x
/**
 * Execute a block with the given object as the parameter and return the block's result.
 *
 * Use `with` to group multiple operations on the same object without repeating its
 * name, or to compute a value from an object that is not part of a fluent chain.
 *
 *   label = with(widget, w -> $"{w.name} ({w.x}, {w.y})");
 */
<ObjectType, Result> Result with(ObjectType obj, function Result(ObjectType) block) {
    return block(obj);
}
```

### Why `let`/`run` collapse

In Kotlin, `let` passes the object as `it` (explicit parameter), while `run` rebinds
`this` (receiver lambda). Since Ecstasy has no receiver lambdas, both would pass the
object as an explicit parameter, making them functionally identical. We keep both names
because they signal different **intent** to the reader:

- `let` = "transform or validate this value"
- `run` = "compute something using this object's members"

This mirrors how many Kotlin style guides distinguish them despite the mechanical
similarity when destructuring is not involved.

### Refined type preservation

The signatures above use `Object` as the parameter type for clarity, but a real
implementation should preserve the concrete type. Ecstasy's `this:type` mechanism or
a self-type parameter could achieve this. The ideal signature for `also` and `apply`
would be:

```x
// Hypothetical -- depends on Ecstasy's ability to express self-types
<This> This also(function void(This) block) {
    block(this);
    return this;
}
```

Without a self-type mechanism, callers would need to cast after `also`/`apply`:

```x
// Without self-type:
Widget widget = new Widget().apply(w -> { w.as(Widget).name = "OK"; }).as(Widget);

// With self-type (ideal):
Widget widget = new Widget().apply(w -> { w.name = "OK"; });
```

If `Object` methods cannot express self-types today, this can be solved with a
generic top-level function instead:

```x
<ObjectType> ObjectType also(ObjectType obj, function void(ObjectType) block) {
    block(obj);
    return obj;
}

<ObjectType> ObjectType apply(ObjectType obj, function void(ObjectType) block) {
    block(obj);
    return obj;
}
```

This sacrifices the method-call syntax (`obj.also(...)`) for correct typing
(`also(obj, ...)`), but avoids any casts.

## Usage examples

### `let` -- transform / null-safe scoping

**Kotlin:**
```kotlin
val length = name?.let { it.trim().length } ?: 0
```

**Ecstasy (proposed):**
```x
Int length = name?.let(n -> n.trim().size) ?: 0;
```

**Ecstasy (today, without scope functions):**
```x
Int length = 0;
if (String n ?= name) {
    length = n.trim().size;
}
```

The `let` version keeps the computation in expression position, avoiding
a mutable variable and an `if` block.

### `also` -- side effects in a chain

**Kotlin:**
```kotlin
val user = createUser()
    .also { log.info("Created user: ${it.name}") }
    .also { analytics.track("user_created", it.id) }
```

**Ecstasy (proposed):**
```x
User user = createUser()
    .also(u -> log.info($"Created user: {u.name}"))
    .also(u -> analytics.track("user_created", u.id));
```

**Ecstasy (today):**
```x
User user = createUser();
log.info($"Created user: {user.name}");
analytics.track("user_created", user.id);
```

The `also` version keeps the chain unbroken, which is valuable in longer
fluent pipelines where introducing a temporary variable would break the flow.

### `apply` -- object configuration

**Kotlin:**
```kotlin
val button = Button().apply {
    text = "Submit"
    isEnabled = true
    setOnClickListener { submit() }
}
```

**Ecstasy (proposed):**
```x
Button button = new Button().apply(b -> {
    b.text      = "Submit";
    b.enabled   = True;
    b.onClick   = () -> submit();
});
```

Note the explicit `b.` prefix -- this is the cost of not having receiver lambdas.
It is slightly more verbose than Kotlin, but still a significant improvement over
the alternative:

**Ecstasy (today):**
```x
Button button = new Button();
button.text    = "Submit";
button.enabled = True;
button.onClick = () -> submit();
```

The `apply` version keeps the configuration visually grouped with the construction
and produces a single expression that can be passed directly to another function.

### `run` -- compute a derived value

**Kotlin:**
```kotlin
val summary = report.run { "$title: $itemCount items, total = $total" }
```

**Ecstasy (proposed):**
```x
String summary = report.run(r -> $"{r.title}: {r.itemCount} items, total = {r.total}");
```

### `with` -- group operations on an object

**Kotlin:**
```kotlin
with(config) {
    database = "prod"
    port = 5432
    maxConnections = 100
}
```

**Ecstasy (proposed):**
```x
with(config, c -> {
    c.database       = "prod";
    c.port           = 5432;
    c.maxConnections = 100;
});
```

### Chaining multiple scope functions

**Kotlin:**
```kotlin
val result = fetchData()
    ?.let { parse(it) }
    ?.also { validate(it) }
    ?.run { transform() }
    ?: defaultValue
```

**Ecstasy (proposed):**
```x
Result result = fetchData()
    ?.let(raw -> parse(raw))
    ?.also(parsed -> validate(parsed))
    ?.run(valid -> transform(valid))
    ?: defaultValue;
```

## Implementation options

### Option 1: Methods on `Object` (recommended)

Add `let`, `run`, `also`, `apply` as default methods on the `Object` interface,
plus `with` as a top-level generic function.

**Pros:**
- Universal availability -- works on every type immediately.
- Familiar call-site syntax: `obj.let(...)`, `obj.also(...)`.
- No new language features required.
- Minimal surface area change (4 methods + 1 function).

**Cons:**
- `also`/`apply` return `Object`, not the concrete type, unless self-types are added
  or the methods are implemented with compiler magic.
- Adds methods to the root interface, which affects every type in the system.
- `let` and `run` are common identifier names and could shadow existing local names
  in user code.

**Estimated effort:** Small. Four short default methods on `Object`, one top-level
function. No parser or type system changes. The main work is deciding on the
self-type strategy for `also`/`apply`.

### Option 2: Top-level generic functions only

Implement all five as standalone generic functions:

```x
<T, R> R    let(T obj, function R(T) block)   = block(obj);
<T, R> R    run(T obj, function R(T) block)   = block(obj);
<T>    T    also(T obj, function void(T) block) { block(obj); return obj; }
<T>    T    apply(T obj, function void(T) block) { block(obj); return obj; }
<T, R> R    with(T obj, function R(T) block)  = block(obj);
```

**Pros:**
- Perfect type preservation -- `also(widget, ...)` returns `Widget`, not `Object`.
- No change to `Object` interface.
- No name collision risk.

**Cons:**
- Call-site is `let(obj, ...)` instead of `obj.let(...)` -- loses the fluent chaining
  that makes scope functions valuable.
- Cannot be combined with `?.` safe-call (e.g., `name?.let(...)` would not work).

**Estimated effort:** Trivial. Five one-line function declarations.

### Option 3: Compiler-supported receiver lambdas (aspirational)

Add support for receiver lambda types (`T.function R()`) to the Ecstasy type system,
then implement scope functions exactly as Kotlin does.

```x
// New function type syntax: T.function R() means "function where this = T"
<Result> Result run(Object.function Result() block) {
    return block(this);
}

// Call site -- 'this' inside the lambda is the widget:
String label = widget.run(() -> $"{name} ({x}, {y})");
```

**Pros:**
- Full Kotlin parity -- unqualified member access inside blocks.
- Enables DSL-style APIs far beyond scope functions.
- Would benefit the entire ecosystem (builders, test DSLs, configuration DSLs).

**Cons:**
- Significant language change: new function type variant, new scoping rules for `this`
  inside lambdas, type inference implications.
- Parser, type checker, and runtime all need changes.
- Potential confusion about what `this` refers to in nested lambdas.

**Estimated effort:** Large. This is a language-level feature requiring design work
on the type system, lambda compilation, and `this` resolution semantics. Worth doing
for the broader DSL ecosystem, but not required just for scope functions.

### Option 4: Hybrid -- methods on `Object` + compiler narrowing (recommended path)

Add the four methods to `Object` (Option 1), but have the compiler treat them
specially for return-type narrowing:

- When the compiler sees `expr.also(block)` or `expr.apply(block)`, it infers
  that the return type is the same as the type of `expr`, not `Object`.
- This is analogous to how the compiler already narrows types after `.is()` checks.

This avoids changing the `Object` interface signature (which stays `Object also(...)`)
while giving callers the correct narrowed type at every call site.

**Pros:**
- Fluent chaining with `?.` support.
- Correct type preservation without self-types.
- Small, well-scoped compiler change (return-type narrowing for known methods).

**Cons:**
- "Magic" methods that behave differently from user-defined methods with the same
  signature. This can be surprising.
- Compiler coupling to specific method names.

**Estimated effort:** Medium. The `Object` methods are trivial; the compiler
narrowing rule is a focused but non-trivial change to type inference.

## Comparison with existing Ecstasy idioms

| Pattern | Today | With scope functions |
|---------|-------|---------------------|
| Null-safe transform | `if (String s ?= name) { len = s.size; }` | `len = name?.let(s -> s.size) ?: 0;` |
| Configure after construct | `val w = new W(); w.x = 1; w.y = 2;` | `val w = new W().apply(w -> { w.x = 1; w.y = 2; });` |
| Side effect in chain | Break chain, assign temp, side-effect, continue | `obj.also(o -> log(o)).transform()` |
| Compute from object | `val r = f(obj.a, obj.b, obj.c);` | `val r = obj.run(o -> f(o.a, o.b, o.c));` |
| Group operations | Repeat `config.` on every line | `with(config, c -> { c.a = 1; c.b = 2; });` |

## Recommendation

**Start with Option 1** (methods on `Object`) for immediate value with zero language
changes. Accept the `Object` return type on `also`/`apply` for now -- in practice,
`also` is most often used in the middle of a chain where the type is already known,
and `apply` is most often used on a freshly constructed object that is immediately
assigned to a typed variable.

**Follow up with Option 4** (compiler narrowing) if the `Object` return type proves
too painful in practice.

**Consider Option 3** (receiver lambdas) as a separate, longer-term language feature
that would benefit far more than just scope functions -- it would unlock Kotlin-style
DSLs, type-safe builders, and configuration blocks across the entire ecosystem.

The five functions are simple enough that the implementation is not the hard part.
The design questions are:

1. **Naming**: `let` and `run` are common words. If they shadow too many existing
   identifiers, alternatives like `letIt`/`runIt` or `pipe`/`chain` could be used,
   though these sacrifice Kotlin familiarity.
2. **Self-types**: How `also`/`apply` preserve the receiver type is the main
   type-system question.
3. **Safe-call integration**: `name?.let(...)` must work for the null-scoping use
   case to be ergonomic. This works naturally if `let` is a method on `Object`.

## Analysis: imperative patterns in the XDK and platform code

A survey of the Ecstasy standard library (`lib_ecstasy`) and the platform project
(`platform/`) reveals recurring imperative patterns that fall into two distinct
categories:

1. **"Lazy Ecstasy"** -- the language already supports a better way, but the code
   was written in a Java-influenced imperative style out of habit.
2. **"Missing construct"** -- the language genuinely lacks a feature (scope functions,
   pipe operators, etc.) and forces an imperative workaround that cannot be improved
   today.

Each example below shows the existing code and a proposed improvement.

---

### Category 1: Lazy Ecstasy -- the language already allows better

These are cases where Ecstasy's existing features (fluent chaining, expression-position
constructs, functional APIs) could already eliminate the imperative boilerplate, but
the developer defaulted to Java-style statement sequences.

#### 1a. Construct-then-populate maps with a single entry

**`platform/common/src/main/x/common/model.x` -- `@Lazy` map factories in test code**

Ecstasy already supports map literals and `Map:[]` syntax. Building a map
imperatively to put a single entry is unnecessary:

```x
// Existing (manualTests/src/main/x/maps.x:322-331)
@Lazy Map<String, String> mapL.calc() {
    Map<String, String> map = new ListMap();
    map.put("1", "L");
    return map;
}
@Lazy Map<String, String> mapH.calc() {
    Map<String, String> map = new HashMap();
    map.put("1", "H");
    return map;
}

// Better today -- use map literal or typed fluent put
@Lazy Map<String, String> mapL.calc() = new ListMap<String, String>().put("1", "L");
@Lazy Map<String, String> mapH.calc() = new HashMap<String, String>().put("1", "H");

// Or even simpler with a map literal (if the @Lazy property type provides inference):
@Lazy Map<String, String> mapL.calc() = ["1"="L"];
@Lazy Map<String, String> mapH.calc() = ["1"="H"];
```

#### 1b. Unnecessary temporary for single-use value

**`lib_ecstasy/src/main/x/ecstasy/text/String.x:217-220`**

```x
// Existing
String last = substring(start);
if (!omitEmpty || !last.empty) {
    results += last;
}

// Better today -- inline the expression
if (!omitEmpty || substring(start).size > 0) {
    results += substring(start);
}
```

(Though here the double evaluation is a valid concern -- this is exactly where
`let` would shine; see Category 2.)

#### 1c. Verbose time formatting with manual zero-padding

**`platform/common/src/main/x/common.x:71-112` -- `appendLogTime()`**

The 40-line manual time formatting function with repeated `if (x < 10) buf.add('0')`
blocks is classic imperative Java thinking. Ecstasy's string templates and formatting
could reduce this significantly:

```x
// Existing (40 lines of manual character-by-character formatting)
static Appender<Char> appendLogTime(Appender<Char> buf, Time? time = Null) {
    // ... 40 lines of buf.add('0'), buf.add(':'), etc.
}

// Better today -- use Ecstasy string templates
static Appender<Char> appendLogTime(Appender<Char> buf, Time? time = Null) {
    if (time == Null) {
        @Inject Clock clock;
        time = clock.now;
    }
    // Ecstasy already has Date/TimeOfDay formatting; use string templates
    val tod = time.timeOfDay;
    buf.addAll($"{time.date} {tod.hour.toString().rightJustify(2, '0')}:{tod.minute.toString().rightJustify(2, '0')}:{tod.second.toString().rightJustify(2, '0')}.{tod.milliseconds.toString().rightJustify(3, '0')}");
    return buf;
}
```

#### 1d. `ControllerConfig.init()` -- manual field-by-field assignment

**`platform/platformUI/src/main/x/platformUI.x:332-341`**

The `ControllerConfig` service has 7 `@Unassigned` fields and a manual `init()`
method that assigns them one by one. This is a Java-style "setter injection"
pattern. Ecstasy supports `construct()` natively:

```x
// Existing (10 lines)
static service ControllerConfig {
    @Unassigned AccountManager accountManager;
    @Unassigned HostManager    hostManager;
    // ... 5 more @Unassigned fields ...

    void init(AccountManager accountManager, HostManager hostManager, ...) {
        this.accountManager = accountManager;
        this.hostManager    = hostManager;
        // ... 5 more assignments ...
    }
}

// Better today -- use a construct or make it a const
// (If it truly must be a singleton service initialized late, @Unassigned + init()
// may be the only option -- but this should be documented as a language limitation,
// not a design choice.)
```

This is actually a borderline case -- it may reflect a genuine language limitation
around late-initialized singleton services (see Category 2).

---

### Category 2: Missing construct -- the language forces imperative code

These are cases where no amount of clever Ecstasy can eliminate the boilerplate.
Scope functions, pipe operators, or other functional constructs would be required.

#### 2a. Construct-then-configure (`apply`)

This is the most common pattern across both the XDK and the platform code.
An object is created, assigned to a variable, configured over multiple statements,
then used or returned.

**`platform/platformUI/src/main/x/platformUI/AppEndpoint.x:169-173` -- JSON stats**

```x
// Existing
JsonObject stats = json.newObject();
stats["deployed"] = True;
stats["active"]   = host.active;
stats["storage"]  = utils.storageSize(host.homeDir).toIntLiteral();
// ... more stats["key"] = value; ...
return stats.makeImmutable();

// With apply
return json.newObject().apply(s -> {
    s["deployed"] = True;
    s["active"]   = host.active;
    s["storage"]  = utils.storageSize(host.homeDir).toIntLiteral();
    // ...
}).makeImmutable();
```

**`platform/platformUI/src/main/x/platformUI/Controller.x:9-12` -- `showConfig()`**

```x
// Existing
JsonObject config = json.newObject();
config["activeThreshold"] = hostManager.activeAppThreshold.toIntLiteral();
return config.makeImmutable();

// With apply -- entire method becomes one expression
return json.newObject().apply(c -> {
    c["activeThreshold"] = hostManager.activeAppThreshold.toIntLiteral();
}).makeImmutable();
```

**`lib_ecstasy/src/main/x/ecstasy/temporal/TimeZone.x:135-144` -- static map init**

```x
// Existing
private static HashMap<Int, TimeZone> Zones = {
    HashMap<Int, TimeZone> map = new HashMap();
    for (Int hour : -12..13) {
        Int picos = hour * PicosPerHour;
        map.put(picos, new TimeZone(picos));
        picos += 30 * PicosPerMinute;
        map.put(picos, new TimeZone(picos));
    }
    return map;
};

// With apply -- no intermediate variable name needed
private static HashMap<Int, TimeZone> Zones = new HashMap<Int, TimeZone>().apply(m -> {
    for (Int hour : -12..13) {
        Int picos = hour * PicosPerHour;
        m.put(picos, new TimeZone(picos));
        m.put(picos + 30 * PicosPerMinute, new TimeZone(picos + 30 * PicosPerMinute));
    }
});
```

**`platform/platformUI/src/main/x/platformUI/AppEndpoint.x:49-57` -- `checkStatus()`**

```x
// Existing
HashMap<String, AppInfo> status = new HashMap();
for ((String deployment, AppInfo appInfo) : accountInfo.apps) {
    status.put(deployment, appInfo.with(active=isActive(deployment)).redact());
}
return status.freeze(inPlace=True);

// With apply
return new HashMap<String, AppInfo>().apply(status -> {
    for ((String deployment, AppInfo appInfo) : accountInfo.apps) {
        status.put(deployment, appInfo.with(active=isActive(deployment)).redact());
    }
}).freeze(inPlace=True);
```

#### 2b. Temporary scoping / transform chain (`let`)

Variables that exist only to be transformed or passed once. The intermediate name
adds no clarity and pollutes the scope.

**`platform/platformUI/src/main/x/platformUI/AppEndpoint.x:118-126` -- Base64 decode**

```x
// Existing
String clientId     = secrets[0 ..< delim];
String clientSecret = secrets.substring(delim+1);
import conv.formats.Base64Format;
clientId     = Base64Format.Instance.decode(clientId).unpackUtf8();
clientSecret = Base64Format.Instance.decode(clientSecret).unpackUtf8();
return ensureAuthProvider(deployment, provider, clientId, clientSecret);

// With let -- no mutable reassignment
return secrets[0 ..< delim]
    .let(raw -> Base64Format.Instance.decode(raw).unpackUtf8())
    .let(clientId ->
        secrets.substring(delim+1)
            .let(raw -> Base64Format.Instance.decode(raw).unpackUtf8())
            .let(clientSecret -> ensureAuthProvider(deployment, provider, clientId, clientSecret))
    );
```

**`platform/kernel/src/main/x/kernel.x:105-110` -- config extraction with `.as()` casts**

```x
// Existing
String   dName     = config.getOrDefault("dName", "").as(String);
String   provider  = config.getOrDefault("cert-provider", names.SelfSigner).as(String);
UInt16   httpPort  = config.getOrDefault("httpPort",  8080).as(IntLiteral).toUInt16();
UInt16   httpsPort = config.getOrDefault("httpsPort", 8090).as(IntLiteral).toUInt16();
String[] proxies   = config.getOrDefault("proxies", []).as(Doc[])
                           .map(addr -> addr.as(String)).toArray();
```

This is a minor case, but the repeated `.as()` casts suggest the `Map<String, Doc>`
API forces untyped access. This is more of an API gap (no typed config accessor)
than a scope function issue, but `let` would help group the extraction:

```x
// With let -- group config extraction as a scoped block
val (dName, provider, httpPort, httpsPort, proxies) = config.let(c -> (
    c.getOrDefault("dName", "").as(String),
    c.getOrDefault("cert-provider", names.SelfSigner).as(String),
    c.getOrDefault("httpPort",  8080).as(IntLiteral).toUInt16(),
    c.getOrDefault("httpsPort", 8090).as(IntLiteral).toUInt16(),
    c.getOrDefault("proxies", []).as(Doc[]).map(addr -> addr.as(String)).toArray(),
));
```

(This depends on Ecstasy supporting tuple destructuring in `val` declarations.)

#### 2c. Side effects in chains (`also`)

Code that breaks a fluent flow to log, validate, or observe an intermediate value.

**`platform/platformUI/src/main/x/platformUI.x:134-177` -- autoStart loop with logging**

The `configure()` method has a long sequence where for each app it creates a host,
logs success/failure, and conditionally updates account state. The logging and
error-handling are interleaved with the creation logic:

```x
// Existing (simplified)
if (hostManager.createDbHost(accountName, appInfo, errors)) {
    reportInitialized(appInfo, "DB");               // side effect
} else {
    accountManager.addOrUpdateApp(accountName, appInfo.with(autoStart=False));
    reportFailedInitialization(appInfo, "DB", errors); // side effect
}

// With also -- side effects don't interrupt flow (hypothetical)
hostManager.createDbHost(accountName, appInfo, errors)
    .also(ok -> { if (ok) reportInitialized(appInfo, "DB"); });
```

This is a weak case because the conditional logic is genuinely complex. But simpler
logging cases abound:

**`platform/kernel/src/main/x/kernel.x:149,213,226,251` -- progress logging**

```x
// Existing -- scattered console.print statements breaking the flow
console.print($"{common.logTime($)} Info : Starting the AccountManager...");
AccountManager accountManager = new AccountManager();

// With also
AccountManager accountManager = new AccountManager()
    .also(_ -> console.print($"{common.logTime($)} Info : Starting the AccountManager..."));
```

#### 2d. Repeated qualification (`with`)

The same object name repeated on many consecutive lines, adding noise without clarity.

**`platform/platformUI/src/main/x/platformUI/AppEndpoint.x:85-98` -- `changeInfo()`**

```x
// Existing -- appInfo repeated 9 times, update flag managed manually
Boolean update = False;
if (autoStart != Null && appInfo.autoStart != autoStart) {
    appInfo = appInfo.with(autoStart=autoStart);
    update  = True;
}
if (appInfo.is(WebAppInfo)) {
    if (useCookies != Null && appInfo.useCookies != useCookies) {
        appInfo = appInfo.with(useCookies=useCookies);
        update  = True;
    }
    if (useAuth != Null && appInfo.useAuth != useAuth) {
        appInfo = appInfo.with(useAuth=useAuth);
        update  = True;
    }
}
```

The `appInfo = appInfo.with(...)` pattern is forced by immutable `const` values --
each mutation creates a new object. This is correct and idiomatic for immutable data,
but the repeated reassignment is noisy. A builder or `with`-block could help:

```x
// Hypothetical -- if const types had a multi-field with()
appInfo = appInfo.with(
    autoStart  = autoStart  ?: appInfo.autoStart,
    useCookies = useCookies ?: appInfo.useCookies,
    useAuth    = useAuth    ?: appInfo.useAuth,
);
```

This is partly an API gap: `WebAppInfo.with()` already accepts nullable overrides,
but calling it once with all fields would eliminate the conditional assignments and
the `update` flag entirely.

#### 2e. Guard-clause chains forced by `conditional` return types

This is a pervasive pattern in the platform endpoint code. Nearly every endpoint
method starts with the same boilerplate:

**`platform/platformUI/src/main/x/platformUI/AppEndpoint.x` -- repeated across
20+ methods**

```x
// Existing -- this exact pattern repeats in almost every endpoint method
AppResponse appInfo = getAppInfo(deployment);
if (appInfo.is(SimpleResponse)) {
    return appInfo;
}
// ... now use appInfo ...
```

This is a language-level limitation. Ecstasy's type narrowing via `is()` requires
an explicit `if` block. In Kotlin, this would be:

```kotlin
val appInfo = getAppInfo(deployment) ?: return SimpleResponse(NotFound)
```

In Ecstasy with `let`, the pattern could at least be tightened, but the real fix
would be a language-level early-return or `?:` with return:

```x
// With let (still verbose, but at least expression-position)
return getAppInfo(deployment).let(info -> {
    if (info.is(SimpleResponse)) return info;
    // ... use info ...
});
```

This points to a potential language enhancement beyond scope functions: a
`guard let` or `?.` with early-return pattern.

#### 2f. Mutable accumulation with `x = f(x)` reassignment

Immutable data structures force the `x = x.operation()` pattern, which is correct
but visually noisy when chained:

**`platform/platformUI/src/main/x/platformUI/AppEndpoint.x:280-282`**

```x
// Existing
Injections injections = appInfo.injections.put(key, value);
appInfo = appInfo.with(injections=injections);
accountManager.addOrUpdateApp(accountName, appInfo);

// With let -- eliminate the intermediate
accountManager.addOrUpdateApp(accountName,
    appInfo.with(injections=appInfo.injections.put(key, value)));
```

This is already possible today (Category 1), but developers don't do it because
the expression gets deeply nested and hard to read. Scope functions would make
the intent clearer:

```x
// With let + also
appInfo.injections.put(key, value)
    .let(inj -> appInfo.with(injections=inj))
    .also(updated -> accountManager.addOrUpdateApp(accountName, updated));
```

#### 2g. Error accumulation and reporting (`also`)

**`platform/common/src/main/x/common.x:33-41` -- `ErrorLog.collectErrors()`**

```x
// Existing
String collectErrors() {
    StringBuffer buf = new StringBuffer();
    for (String message : messages) {
        if (message.startsWith("Error:")) {
            buf.append(message)
               .append("\n");
        }
    }
    return buf.toString();
}

// Better today -- use functional collection operations
String collectErrors() =
    messages.filter(m -> m.startsWith("Error:"))
            .toString(sep="\n", pre="", post="\n");
```

This is a Category 1 case -- Ecstasy's collection API already supports this.

---

### Summary of findings

| Pattern | Count | Category | Fix |
|---------|-------|----------|-----|
| Construct-then-configure | 8+ | Missing (`apply`) | Add `apply` to `Object` |
| Single-use temporaries | 5+ | Missing (`let`) | Add `let` to `Object` |
| Side effects breaking flow | 4+ | Missing (`also`) | Add `also` to `Object` |
| Repeated qualification | 6+ | Missing (`with`) | Add `with` as top-level function |
| Guard-clause boilerplate | 20+ | Language gap | Early-return operator or `guard let` |
| `x = f(x)` reassignment chains | 5+ | Mixed | `let` helps; const builder pattern helps more |
| Java-style imperative habits | 5+ | Lazy Ecstasy | Code review / style guide |

**Key observations:**

1. The platform code is surprisingly well-written for a young language. The `const`
   types with `with()` methods (e.g., `WebAppInfo.with(...)`) are an excellent
   immutable update pattern that many languages lack. The code is clean, readable,
   and type-safe.

2. The most impactful scope function would be **`apply`** -- the construct-then-
   configure pattern appears everywhere (JSON objects, maps, collections, services).

3. The second most impactful would be **`let`** -- not for complex transformations,
   but for the simple case of "compute, then use once" that currently requires
   a named temporary.

4. The **guard-clause boilerplate** (`if (x.is(ErrorType)) return x`) is the single
   most repeated pattern in the platform code and is a language-level issue that
   scope functions alone cannot fully address. A `guard let` or early-return
   operator would have a larger impact than all five scope functions combined for
   this codebase.

5. The "Lazy Ecstasy" cases are relatively few -- the developers generally use the
   language's features well. The main imperative habits are around manual
   `StringBuffer` construction and map population, which are patterns that any
   Java developer would recognize and that Ecstasy's collection APIs could often
   replace.

---

### Beyond scope functions: other language gaps that force imperative patterns

The analysis revealed several language-level limitations that scope functions alone
cannot address, but which contribute to the overall imperative feel of the code.
These are worth noting because they interact with scope functions -- solving these
would make scope functions even more effective.

#### `if` is a statement, not an expression

Ecstasy's `switch` can be an expression, but `if` cannot. This forces `var`
reassignment whenever a conditional affects a pipeline:

```x
// Existing (lib_web/src/main/x/web/Header.x:100-109)
var result = entries.filter(e -> CaseInsensitive.areEqual(e[0], name))
                    .map(e -> e[1]);
if (expandDelim != Null) {
    result = result.flatMap(s -> s.split(expandDelim));
}
return result.map(s -> s.trim(), ToStringArray);

// If `if` were an expression
val result = entries.filter(e -> CaseInsensitive.areEqual(e[0], name))
                    .map(e -> e[1]);
return (if (expandDelim != Null) result.flatMap(s -> s.split(expandDelim)) else result)
    .map(s -> s.trim(), ToStringArray);
```

This forces `var` where `val` would suffice, undermining the immutable-by-default
philosophy. Every language that has adopted expression-based `if` (Kotlin, Scala,
Rust, Swift, Ruby) has found it eliminates a class of mutable temporaries.

#### Explicit collector parameters in `map()`/`filter()`

Collection operations like `map()` often require an explicit collector factory
parameter, which adds noise:

```x
// Existing (lib_web/src/main/x/web/Header.x)
@RO List<String> names.get() = entries.map(e -> e[0], ToStringArray);
return result.map(s -> s.trim(), ToStringArray);

// Ideal -- infer the collection type from the lambda return type
@RO List<String> names.get() = entries.map(e -> e[0]);
```

The `ToStringArray` parameter is boilerplate that the type system should be able
to infer. This is a minor issue individually, but it accumulates across codebases
and discourages developers from using functional collection operations over
imperative loops.

#### No destructuring in lambda parameters

Lambdas cannot destructure tuple or map-entry parameters, forcing manual extraction:

```x
// Existing -- manual tuple element access
.map(kv -> (kv.extract('=', 0, "???").trim(), kv.extract('=', 1).trim()))

// Ideal -- destructure in parameter
.map((key, value) -> (key.trim(), value.trim()))
```

#### Late-initialized singleton services

The `ControllerConfig` pattern (7 `@Unassigned` fields with a manual `init()`
method) appears to be a genuine language limitation around services that must be
singletons but cannot be constructed at module load time. If `service` types
could accept constructor parameters while remaining singletons (perhaps via a
`service.configure()` protocol or lazy `@Inject` with factory), this entire
pattern would disappear.

## The expression problem: `void`, `if`, and the cost of statements

A recurring theme throughout this analysis is that Ecstasy code is forced into
imperative patterns not because the developers lack functional thinking, but because
the language draws a hard line between *statements* and *expressions* in places
where modern functional-influenced languages do not.

This section examines the three related issues: `void` as a non-type, `if` as a
statement-only construct, and the broader question of whether Ecstasy should move
toward "everything is an expression."

### `void` is not a type

In Ecstasy, `void` is a keyword, not a type. A `void` method returns zero values --
at the runtime level, the result is an empty `Tuple<>`, but at the language level
you cannot write `void v = bar()` (compile error) or treat the result of a void
method as a value.

This is inherited from Java and C, where `void` means "no return value." But in
languages that treat everything as an expression (Kotlin, Scala, Rust, Ruby, F#,
Haskell), void's equivalent (`Unit`, `()`) is a real type with a single value.
This seemingly academic difference has practical consequences for fluency and
chaining.

Interestingly, Ecstasy is *almost* there already. The runtime represents void
returns as empty `Tuple<>`. The infrastructure for "void is a value" exists at
the VM level -- it's only the language surface that refuses to expose it. This
means the cost of promoting `void` to a real type is smaller than it appears:
the runtime already handles it correctly; only the compiler's type checker and
the language spec need to change.

### How `void` breaks fluency

#### 1. Void methods cannot be chained

The most immediate impact: any method that "does something" but returns `void`
breaks a fluent chain. The caller must drop out of expression-position into
statement-position.

```x
// Existing -- Console.print() returns void, breaks any chain
console.print("step 1");    // statement
console.print("step 2");    // statement
console.print("step 3");    // statement

// If print() returned Console (like Appender.add() returns Appender)
console.print("step 1")
       .print("step 2")
       .print("step 3");
```

The `Appender` interface in Ecstasy already demonstrates the correct pattern:
`add()` returns `Appender`, enabling fluent chaining. But most of the standard
library does not follow this pattern. Common void methods that break chains:

| Type | Method | Could return |
|------|--------|--------------|
| `Console` | `print()` | `Console` |
| `Map` | `putAll()` | `Map` |
| `List` | `sort()` | `List` |
| `Ref` | `set()` | `Ref` or the value |
| `Timer` | `start()`, `stop()` | `Timer` |
| `Closeable` | `close()` | `void` (legitimately -- nothing to chain after close) |

Some of these are legitimately void (you shouldn't chain after `close()`), but
many are void only because Java convention said so.

This is not hypothetical. Here is a real example from the platform code where
void forces an imperative style:

```x
// Existing (platform/kernel/src/main/x/kernel.x:136-138)
// Three consecutive void calls on `manager` -- each is an isolated statement
@Inject CertificateManager manager;
manager.createSymmetricKey(storeFile, password, names.CookieEncryptionKey);
manager.createSymmetricKey(storeFile, password, names.PasswordEncryptionKey);

// If createSymmetricKey returned CertificateManager
@Inject CertificateManager manager;
manager.createSymmetricKey(storeFile, password, names.CookieEncryptionKey)
       .createSymmetricKey(storeFile, password, names.PasswordEncryptionKey);
```

And from `ErrorLog` in the platform, where `reportAll` must iterate manually
because `print()` returns void:

```x
// Existing (platform/common/src/main/x/common.x:27-30)
void reportAll(Reporting report) {
    for (String msg : messages) {
        report(msg);
    }
}

// If print() returned Console, the caller could write
messages.forEach(console.&print);
// or even
messages.forEach(console.print);
```

The forEach case already works in Ecstasy (void lambdas are valid in forEach),
but the broader principle holds: returning void removes information from the
type system that could enable composition.

#### 2. Void prevents `also`-style side effects

The `also` scope function relies on executing a side-effect block and returning
the original object. But if the side-effect operation returns `void`, the lambda
type must be `function void(T)` rather than the more general `function R(T)`.
This means `also` and `let` need different function signatures, when in a
language with `Unit`-as-a-type they could be the same.

```x
// This works because the lambda is typed as function void(Object)
obj.also(o -> console.print(o));

// But you cannot do this -- void is not a value
obj.let(o -> console.print(o));  // ERROR: print returns void, let expects a value
```

In Kotlin, `println()` returns `Unit` (a real value), so both `let` and `also`
accept it uniformly. The caller doesn't need to care whether the block's return
value is used or discarded.

#### 3. Void forces statement-blocks where expressions would suffice

When you need to perform a side effect inside an expression context (e.g., inside
a lambda, a conditional, or a string template), void methods force you to wrap
them in a block with explicit flow control:

```x
// Existing -- must use a statement block because print() is void
val result = items.map(item -> {
    console.print($"Processing {item}");  // side effect (void)
    return transform(item);               // must use explicit return
});

// If print() returned Unit/Console, the last expression would be the return value
val result = items.map(item -> {
    console.print($"Processing {item}");
    transform(item)                       // implicit return of last expression
});
```

### The `if`-as-statement limitation

Ecstasy's `switch` can be an expression (returning a value), but `if` cannot.
This asymmetry forces mutable variables in cases where an immutable binding would
be natural.

#### The fundamental issue: statements introduce mutation

When `if` is a statement, the only way to conditionally assign a value is to
declare a mutable variable first, then overwrite it:

```x
// Existing -- forced into var because if is a statement
var message = "default";
if (error) {
    message = "something went wrong";
}
```

This code has three problems:
1. `message` must be `var` (mutable), even though it's never reassigned after
   the `if` block.
2. The initial value `"default"` looks like it matters, but it's really just a
   placeholder that may or may not survive.
3. The reader must trace through both branches to know the final value of
   `message` -- it's not declared at its point of definition.

When `if` is an expression, all three problems disappear:

```x
// If `if` were an expression (as in Kotlin, Scala, Rust, Ruby)
String message = if (error) "something went wrong" else "default";
```

Now `message` is immutable, its value is defined at its declaration point, and
no mutation is involved.

#### Real examples from the codebase

This isn't a theoretical concern. The XDK and platform code are full of this
pattern:

```x
// platform/platformUI/src/main/x/platformUI.x:65-70
String baseDomain;
if (Int dot := hostName.indexOf('.')) {
    baseDomain = hostName.substring(dot + 1);
} else {
    throw new IllegalState($"Invalid host address: {hostName.quoted()}");
}

// With if-expression
String baseDomain = if (Int dot := hostName.indexOf('.'))
    hostName.substring(dot + 1)
else
    throw new IllegalState($"Invalid host address: {hostName.quoted()}");
```

```x
// platform/host/src/main/x/host/HostManager.x:191-206
ProxyManager proxyManager;
IPAddress[]  proxyIPs = [];
if (proxies.empty) {
    proxyManager = NoProxies;
} else {
    // ... 15 lines of proxy setup ...
}

// With if-expression (the simple case)
ProxyManager proxyManager = if (proxies.empty) NoProxies else /* ... */;
```

```x
// lib_web/src/main/x/web/Header.x:100-109
var result = entries.filter(e -> CaseInsensitive.areEqual(e[0], name))
                    .map(e -> e[1]);
if (expandDelim != Null) {
    result = result.flatMap(s -> s.split(expandDelim));
}

// With if-expression -- no var needed
val result = if (expandDelim != Null)
    entries.filter(e -> CaseInsensitive.areEqual(e[0], name))
           .map(e -> e[1])
           .flatMap(s -> s.split(expandDelim))
else
    entries.filter(e -> CaseInsensitive.areEqual(e[0], name))
           .map(e -> e[1]);
```

#### The `switch`-expression workaround

Ecstasy's `switch` already works as an expression, so developers *can* avoid
`var` today -- but only by writing awkward two-case switches for binary
conditions:

```x
// Works today -- but unnatural for a binary condition
String message = switch (error) {
    case True:  "something went wrong";
    case False: "default";
};
```

Nobody writes this. Two-case switches are visually heavy and semantically
misleading (they suggest a multi-way dispatch, not a simple binary choice).
Developers default to `if` + `var` because it reads more naturally, even
though it introduces unnecessary mutation.

#### Block expressions: available but verbose

Ecstasy does support *block expressions* via `StatementExpression` using the
`{ return value; }` syntax:

```x
// Block expression -- works today but verbose
String message = {
    if (error) {
        return "bad";
    }
    return "default";
};

// vs. expression-if (not supported)
String message = if (error) "bad" else "default";
```

The block expression requires explicit `return` keywords, curly braces, and
semicolons -- four lines for what is conceptually a one-line conditional
assignment. It works, but it's not concise enough to become idiomatic.

#### The ternary operator question

Some languages solve this with a ternary operator (`condition ? then : else`).
Ecstasy chose not to include one, which is a defensible choice -- ternaries
are notoriously hard to read when nested. But without either ternaries or
expression-`if`, Ecstasy has no concise way to conditionally bind an immutable
variable. The language guides developers toward mutation by omission.

### The case for expressions: what other languages learned

The trend across modern languages is clear and consistent: every language that
started with statement-oriented control flow has moved toward expressions, and
none have moved in the opposite direction.

- **Kotlin** (2011) was designed from scratch with `if`, `when`, and `try` as
  expressions. JetBrains explicitly cited the `var`-for-conditional pattern in
  Java as a key motivation. The Kotlin style guide recommends expression form
  over statement form wherever possible.

- **Rust** (2010) makes *everything* an expression -- `if`, `match`, `loop`,
  and blocks all produce values. The Rust team has stated this was one of their
  best design decisions, as it eliminated entire categories of uninitialized
  and partially-initialized variable bugs.

- **Scala** (2004) followed the same path. Martin Odersky has written that
  expression-oriented `if` and `match` are essential for making functional
  style natural rather than forced.

- **Swift** (2014) started without expression-`if`, then added it in Swift 5.9
  (2023) after years of community demand. The Swift Evolution proposal (SE-0380)
  documented the same patterns seen in the Ecstasy codebase: unnecessary `var`,
  duplicated assignments, and `switch` used awkwardly for binary conditions.

- **Ruby** (1995) has always had expression-`if`. Matz designed it this way
  from the beginning because Smalltalk demonstrated that the statement/expression
  distinction creates accidental complexity.

- **C#** (2000) added expression-bodied members in C# 6, switch expressions in
  C# 8, and pattern-matching `is` in C# 7 -- each step moving toward more
  expression-oriented code. The C# team explicitly noted that each addition
  reduced mutation-related bugs.

The lesson is consistent: **the statement/expression boundary is a source of
accidental complexity, not essential complexity.** Removing it does not make
code harder to read -- it makes it easier, because the reader can see the value
at its definition point rather than tracing mutations through control flow.

#### Counter-arguments and trade-offs

The case for keeping `if` as a statement is not without merit:

1. **Readability of complex branches**: When branches contain many statements,
   expression-`if` can become hard to read. But this is an argument for *not
   forcing* expression form, not for *prohibiting* it. Kotlin and Rust allow
   both forms -- developers use expression-`if` for simple conditionals and
   statement-`if` for complex ones.

2. **Implicit returns are confusing**: Making the last expression in a block
   its return value (Level 3) can surprise developers, especially when a
   method call happens to be the last line and its return value is accidentally
   captured. Rust handles this by requiring a semicolon to explicitly discard
   a value (`foo();` discards, `foo()` returns). This is a real design concern,
   but it applies to Level 3 only, not to expression-`if` (Level 1).

3. **Migration cost**: Existing code doesn't break (expression-`if` is additive),
   but coding conventions, linters, and educational materials all need updates.
   This is a real cost, but it's one-time and amortized over the language's
   lifetime.

4. **Void complicates expressions**: If `if` is an expression, what is the type
   of `if (cond) doSomething()` without an `else`? In Kotlin, it's `Unit`. In
   Ecstasy today, there is no answer because void is not a type. This means
   expression-`if` works best when paired with the `void` → `Unit` change
   (Level 2), though it can be implemented without it by requiring `else` in
   expression position.

### How hard would it be to make everything an expression?

There are three levels of change, from easiest to hardest:

#### Level 1: `if`/`else` as expression (moderate effort)

Make `if`/`else` return the value of their last expression when used in expression
position. This is what Kotlin, Scala, and Rust do.

**Compiler change**: The parser already distinguishes expression vs. statement
context. Add an `IfExpression` AST node (analogous to the existing
`SwitchExpression`) that requires an `else` branch and infers the return type
from both branches.

**Impact**: Eliminates `var` temporaries for binary conditionals. This is the
single highest-value change for reducing imperative boilerplate.

**Compatibility**: Fully backward-compatible. Existing `if` statements continue
to work unchanged. The expression form is only used when `if` appears in
expression position (assignment, return, argument).

```x
// This would become valid
String label = if (active) "running" else "stopped";
return if (cache.contains(key)) cache.get(key) else compute(key);
```

#### Level 2: `void` → `Unit` type (significant effort)

Replace the `void` keyword with a real `Unit` type (or make `Tuple<>` the
canonical unit type). Every void method now returns `Unit`, which is a real
value that can be assigned, passed, and ignored.

**Compiler change**: `void` in return position becomes syntactic sugar for
`Unit`. The empty tuple `Tuple<>` could serve as `Unit`. Method calls that
currently discard void returns now produce a `Unit` value.

**Impact**: This change has cascading benefits beyond just aesthetics:

- **Uniform function types**: `function R(T)` works for all methods, including
  side-effect-only ones. Today, `function void(T)` and `function R(T)` are
  different kinds of types, which means generic code that accepts "any function"
  must special-case void. With `Unit`, there is only one function kind.

- **Scope function unification**: `let` and `also` can use the same function
  signature. `let(function R(T))` works whether the block returns `String`,
  `Int`, or `Unit` -- the caller decides whether to use the return value.

- **Higher-order function composition**: You can pass a void method reference
  where a `function Unit(T)` is expected. `map(console.&print)` would work
  because `print` returns `Unit`, not void. Today this is impossible.

- **Collection pipelines**: forEach, onEach, and similar side-effect operations
  can be unified with map/filter/reduce because they all return typed values.

- **Conditional expressions**: `if (cond) doSomething()` without an `else`
  has type `Unit` (or `Unit?`), which is a real type that the compiler can
  check. Without Unit, expression-`if` must either forbid missing `else`
  branches or produce a special "maybe-void" that doesn't fit the type system.

**Why Ecstasy is well-positioned for this**: The runtime already represents
void returns as `Tuple<>`. The infrastructure exists. The change is primarily
in the compiler's type checker and the language specification. Ecstasy's
`Tuple` system means `Unit` = `Tuple<>` falls out naturally, unlike languages
that need to invent a new type from scratch.

**Compatibility**: Mostly backward-compatible. `void` remains valid syntax
(sugar for `Unit`). The main risk is code that pattern-matches on Tuple
sizes -- `Tuple<>` from void methods would now be observable. In practice,
very little code inspects Tuple arities at runtime.

**What other languages did**:
- Kotlin uses `Unit` as an object type with a single instance `Unit`.
  `void` is only used for Java interop. This works seamlessly.
- Scala uses `Unit` the same way. The transition from Java's `void` was
  one of the smoothest parts of Scala's design.
- Rust uses `()` (the empty tuple) as its unit type -- exactly the same
  approach Ecstasy could take with `Tuple<>`.
- Haskell uses `()` similarly. The entire ML family treats unit as a type.
- Swift has `Void` as a typealias for `()` (the empty tuple). Same approach.

#### Level 3: Last-expression-is-return-value (significant effort)

Make blocks, `if`, `for`, `while`, and `try` all return the value of their
last expression. This is what Rust, Ruby, and Scala do.

**Compiler change**: Every block that ends with an expression (not a statement)
implicitly returns that expression's value. Void blocks return `Unit`.

**Impact**: Eliminates the need for explicit `return` in many lambdas and
single-expression methods. Combined with Level 1 and 2, this makes Ecstasy
a fully expression-oriented language.

**Compatibility**: This is the most disruptive change. Code that accidentally
has an expression as the last line of a void block would now have a different
return type. Requires careful analysis.

### What Ecstasy could do today (Lazy Ecstasy)

Even without language changes, some void-related pain can be reduced:

#### Return `this` instead of `void` in fluent APIs

Any method that mutates and returns `void` should consider returning `this`
instead. The `Appender` interface already demonstrates this pattern. Extending
it to `Console`, `Map.putAll()`, `List.sort()`, and other commonly-chained
types would immediately improve fluency:

```x
// Existing Console interface
void print(Object object = "", Boolean suppressNewline = False);

// Better -- return Console for chaining
Console print(Object object = "", Boolean suppressNewline = False);

// Then you can write
console.print("header")
       .print("body")
       .print("footer");
```

This requires no language changes -- just API changes to return `this` where
void is currently used. The `Appender`/`StringBuffer` classes already prove
this pattern works in Ecstasy.

#### Use block expressions with explicit `return`

Ecstasy already supports `StatementExpression` -- a block `{ ... return val; }`
that evaluates to a value. This can replace some `var` temporaries:

```x
// Instead of var + if
var label = "default";
if (error) {
    label = "bad";
}

// Use block expression (works today)
String label = {
    if (error) {
        return "bad";
    }
    return "default";
};
```

This is more verbose than expression-`if`, but it's available now and keeps
the binding immutable.

### Comparison with other languages

| Feature | Ecstasy | Kotlin | Rust | Scala | Swift | Java |
|---------|---------|--------|------|-------|-------|------|
| `if` as expression | No | Yes | Yes | Yes | 5.9+ | No |
| `switch`/`match` as expression | Yes | Yes | Yes | Yes | 5.9+ | 14+ |
| `void` is a type | No | Yes (`Unit`) | Yes (`()`) | Yes (`Unit`) | Yes (`Void`) | No |
| Last-expr-is-return | No | No* | Yes | Yes | No | No |
| Block as expression | Yes** | Yes | Yes | Yes | No | No |
| Ternary operator | No | No*** | No | No | Yes | Yes |
| Scope functions on all types | No | Yes | Via crate | Yes | No**** | No |

\* Kotlin requires explicit `return` in blocks, but `if`/`when` are expressions.
\** Ecstasy supports `{ return value; }` block expressions with explicit `return`.
\*** Kotlin doesn't need ternary because `if` is an expression.
\**** Swift community uses `with()` free function universally but it's not in stdlib.

Note that Ecstasy currently sits in the same column as Java on four of seven
features. Java has ternary as a partial mitigation; Ecstasy has block expressions.
Neither is as powerful as expression-`if` + `Unit` type, which every other modern
language in the table has adopted.

Also note that Java is actively moving in the expression direction: Java 14 added
switch expressions, Java 21 added pattern matching, and Project Amber continues
to push toward more expression-oriented code. Ecstasy has an opportunity to leap
ahead of Java's incremental migration rather than following the same slow path.

### The philosophical argument: expressions vs. statements

The statement/expression divide is not just a syntactic convenience question --
it reflects a deeper design philosophy about what code *means*.

In a statement-oriented language, code is a sequence of *commands*: "do this,
then do that, then do the other thing." The programmer thinks in terms of
*effects* -- what changes in the world at each step. Variables are mutable by
default because the model assumes state changes over time.

In an expression-oriented language, code is a composition of *values*: "this
value is defined as that computation." The programmer thinks in terms of
*definitions* -- what each name means. Variables are immutable by default
because the model assumes values are computed once and bound permanently.

Ecstasy's design philosophy leans toward the expression-oriented camp: it
has `val` (immutable by default), it discourages mutable state, it supports
functional programming patterns. But its control flow syntax leans toward the
statement-oriented camp: `if` produces no value, `void` is not a type, blocks
require explicit `return`. This creates a tension where the language's *values*
say "be functional" but its *syntax* says "be imperative."

Resolving this tension -- even partially, by adding expression-`if` and
scope functions -- would bring the syntax in line with the philosophy. The
language would stop guiding developers toward mutation in cases where immutable
bindings are the natural choice.

### Recommendation

**Short term** (no language changes):
- Audit the XDK for methods that return `void` but could return `this` for
  fluency. Prioritize `Console.print()` and collection mutation methods.
  The `Appender` interface proves this works; extend the pattern.
- Use block expressions `{ return val; }` in new code where `var` temporaries
  would otherwise be needed.
- Add scope functions (`let`, `run`, `also`, `apply`) as default methods on
  `Object`. This requires no parser or type-system changes and gives immediate
  value for the most common patterns identified in this analysis.

**Medium term** (focused language change):
- Add `if`/`else` as expression. This is the single highest-value change
  for reducing imperative boilerplate in Ecstasy code. Every language that
  has adopted this reports that it eliminates a class of mutable temporaries
  and makes code more concise without sacrificing readability. The existing
  `SwitchExpression` AST node provides a template for `IfExpression`.

**Long term** (language evolution):
- Make `void` an alias for `Unit` (or `Tuple<>`). This unifies the function
  type system, enables uniform scope functions, and makes expression-`if`
  work seamlessly without requiring an `else` branch. The runtime already
  represents void as `Tuple<>`, so the foundation exists.
- Consider last-expression-as-return-value for blocks and lambdas (with
  explicit discard via `;` as in Rust). This is the most invasive change
  but completes the expression-oriented vision.

## Appendix: Kotlin scope function decision tree

For reference, here is the standard Kotlin decision tree for choosing a scope function:

```
Do you need the result of the block, or the original object?
  |
  +-- Block result --> Do you want to refer to the object as 'this' or 'it'?
  |     |
  |     +-- 'this' (unqualified access) --> Is it called on an object or standalone?
  |     |     |
  |     |     +-- Called on object --> run
  |     |     +-- Standalone        --> with
  |     |
  |     +-- 'it' (explicit parameter) --> let
  |
  +-- Original object --> Do you want to refer to the object as 'this' or 'it'?
        |
        +-- 'this' (unqualified access) --> apply
        +-- 'it' (explicit parameter)   --> also
```

In Ecstasy without receiver lambdas, the `this`/`it` axis collapses -- the object is
always an explicit parameter. The decision simplifies to:

```
Do you need the result of the block, or the original object?
  |
  +-- Block result   --> let / run / with
  +-- Original object --> also / apply
```

And between `let`/`run`: signal your intent (`let` = transform, `run` = compute).
Between `also`/`apply`: signal your intent (`also` = observe, `apply` = configure).

## What else would developers expect in 2026?

Beyond scope functions and expression-oriented control flow, there are several
areas where Ecstasy diverges from what developers coming from Kotlin, Rust, Swift,
or modern Java would expect. Some are deliberate design choices worth preserving;
others are gaps that could be closed.

### Sealed types: exhaustive hierarchies as a programming tool

#### What developers expect

In Kotlin, Rust, Swift, and Java 17+, `sealed` types restrict who can extend a
class or interface. The compiler then uses this closed set to enforce exhaustive
pattern matching -- if you handle all subtypes, no `default` branch is needed,
and adding a new subtype produces compile errors everywhere the type is matched.

```kotlin
// Kotlin
sealed class Result<out T> {
    data class Success<T>(val value: T) : Result<T>()
    data class Failure(val error: Throwable) : Result<Nothing>()
}

// Exhaustive -- compiler knows all cases, warns if one is missing
when (result) {
    is Result.Success -> handleSuccess(result.value)
    is Result.Failure -> handleFailure(result.error)
    // no 'else' needed -- compiler knows this is complete
}
```

This is one of the most impactful features in modern language design because it
turns the type system into a verification tool: the compiler proves your match
is complete, and when you add a new variant, it tells you every place that needs
updating.

#### What Ecstasy has today

Ecstasy has **enums** with exhaustive switch checking -- the compiler rejects
a switch on an enum that doesn't cover all values. This is the same mechanism
that sealed types generalize.

Ecstasy also has `const` classes (immutable value types) and union types
(`String | Int`), but neither provides the sealed guarantee. Any class can be
extended unless it's explicitly `@Final`. There is a `@Sealed` annotation
mentioned in the `Const.x` documentation as a concept for "Superposed types,"
but it does not exist as an implemented feature.

Ecstasy's module system is closed-world by design -- after compilation, no new
types can be added. This is a stronger guarantee than sealed types at the
deployment level, but it doesn't help at the *programming* level: inside a
module, the developer still can't tell the compiler "these are the only
subtypes, please check my matches."

#### How it could look in Ecstasy

```x
// Proposed: sealed interface restricts implementations to this module/file
sealed interface Result<Value> {
    const Success<Value>(Value value) implements Result<Value>;
    const Failure(Exception error)    implements Result<Nothing>;
}

// Exhaustive switch -- compiler knows all cases
String describe(Result<String> result) = switch (result.is(_)) {
    case Success: result.value;
    case Failure: $"Error: {result.error.message}";
    // no default needed -- compiler proves this is complete
};

// Adding a new case (e.g., Pending) would produce compile errors at every
// switch that doesn't handle it -- the same safety net that enums provide,
// extended to class hierarchies.
```

This fits naturally with Ecstasy's existing features:
- `const` types already provide immutable value semantics.
- `switch (x.is(_))` already does type dispatch.
- Enum exhaustiveness checking already exists in the compiler.
- The closed-world module system means the compiler has full knowledge at
  build time.

#### Why it matters

Without sealed types, developers who want exhaustive matching must either:
- Use enums (which can't carry different data per variant).
- Use union types (`Success | Failure`) but lose exhaustiveness checking.
- Use an interface hierarchy and accept that the compiler can't verify
  completeness -- requiring a `default` branch that may silently swallow
  new variants.

Sealed types close this gap. They are the natural generalization of enums to
types that carry data, and every modern language that has adopted them reports
that they dramatically reduce "forgot to handle the new case" bugs.

**Effort estimate**: Medium. The compiler already has exhaustiveness checking
for enums. Extending it to sealed class hierarchies requires tracking which
types are permitted subtypes and wiring that information into the switch
analysis. The `@Sealed` concept already appears in the Const.x documentation,
suggesting this has been considered.

---

### Pattern matching: beyond `switch` and `.is()`

#### What developers expect

Kotlin's `when`, Rust's `match`, and Scala's `match` support nested
destructuring, guard clauses, and binding in a single construct:

```kotlin
// Kotlin -- nested destructuring + guard
when (val result = parse(input)) {
    is Success -> when {
        result.value.isEmpty() -> "empty"
        else -> "got: ${result.value}"
    }
    is Failure if result.error is TimeoutException -> "timed out"
    is Failure -> "failed: ${result.error}"
}
```

```rust
// Rust -- deeply nested pattern matching with binding
match event {
    Event::Click { x, y } if x > 100 => handle_right(y),
    Event::Click { x, y }            => handle_left(x, y),
    Event::Key(Key::Enter)            => submit(),
    Event::Key(key)                   => buffer(key),
}
```

#### What Ecstasy has today

Ecstasy's `switch` supports:
- Value matching: `case 1:`, `case "hello":`
- Range matching: `case 1..5:`
- Type dispatch: `switch (x.is(_)) { case Int: ... case String: ... }`
- Tuple matching: `switch (a, b) { case (1, 2): ... }`
- Wildcard in tuples: `case (_, Int):`
- Switch as expression (returns a value)

What's missing:
- **No destructuring in case branches**: You can match on a type but not
  bind its fields in the same step. You must match, then cast and extract.
- **No guard clauses**: `case X if condition:` is not supported. You must
  nest an `if` inside the case body.
- **No nested patterns**: `case Success(value):` is not supported. You
  must match the outer type, then match the inner value in a nested switch.

#### How it could look

```x
// Today -- verbose type dispatch with manual extraction
switch (event.is(_)) {
    case ClickEvent: {
        ClickEvent click = event.as(ClickEvent);
        if (click.x > 100) {
            handleRight(click.y);
        } else {
            handleLeft(click.x, click.y);
        }
    }
    case KeyEvent: {
        KeyEvent key = event.as(KeyEvent);
        if (key.code == Enter) {
            submit();
        } else {
            buffer(key.code);
        }
    }
}

// Proposed -- destructuring + guards
switch (event) {
    case ClickEvent(x, y) if x > 100: handleRight(y);
    case ClickEvent(x, y):             handleLeft(x, y);
    case KeyEvent(Enter):              submit();
    case KeyEvent(code):               buffer(code);
}
```

The existing switch infrastructure (expression form, type dispatch, tuples)
provides a solid foundation. Destructuring in case branches and guard clauses
would extend it without introducing a new keyword or construct.

**Effort estimate**: Significant. Destructuring in patterns requires the
compiler to understand which types support positional extraction (const types
with known field order are natural candidates). Guard clauses are simpler --
they're syntactic sugar for a nested `if` that falls through to the next case.

---

### Result types: structured error handling without exceptions

#### What developers expect

Rust's `Result<T, E>`, Swift's `Result<Success, Failure>`, and Kotlin's
`Result<T>` provide a way to represent fallible operations in the type system
without throwing exceptions. The caller is forced to handle both success and
failure at the type level.

```rust
// Rust -- error handling is in the type, not the control flow
fn parse(input: &str) -> Result<Config, ParseError> { ... }

// Caller MUST handle both cases -- compiler enforces it
let config = parse(input)?;  // ? propagates error, returns early
```

#### What Ecstasy has today

Ecstasy's `conditional` return type is a creative solution to a similar problem:

```x
conditional Config parse(String input) {
    if (valid) {
        return True, config;
    }
    return False;
}

// Caller
if (Config config := parse(input)) {
    // success
} else {
    // failure -- but no error information available
}
```

This is elegant for "found or not found" cases, but it has a key limitation:
**the failure case carries no information.** You get `False` but no error
message, no error code, no typed error. For operations where the *kind* of
failure matters (network timeout vs. parse error vs. permission denied), the
caller either gets nothing or the function must throw an exception.

#### How a Result type could complement conditional returns

```x
// Proposed: Result type for when failure carries information
const Result<Value, Error> {
    // Success variant
    const Success<Value>(Value value) implements Result<Value, Nothing>;

    // Failure variant with typed error
    const Failure<Error>(Error error) implements Result<Nothing, Error>;
}

// Usage
Result<Config, ParseError> parse(String input) { ... }

// With sealed types + pattern matching, handling is clean
Config config = switch (parse(input)) {
    case Success(value): value;
    case Failure(error): {
        log.warn($"Parse failed: {error.message}");
        return defaultConfig;
    }
};
```

Note how this builds on sealed types and pattern matching -- the three features
form a cohesive package. Sealed types define the closed hierarchy, pattern
matching destructures it, and Result types apply the pattern to error handling.

Ecstasy's `conditional` returns should remain for the common "found/not found"
case -- they're more concise than `Result` when the failure case is simple.
`Result` is for when failure is as informative as success.

**Effort estimate**: Small for the type itself (just a `const` class).
Depends on sealed types and pattern matching for full ergonomic benefit.
Without those, it's just a class with `.is()` checks -- no better than what
you could write today.

---

### Extension methods: ad-hoc method addition

#### What developers expect

Kotlin, Swift, Rust, and C# allow adding methods to existing types without
modifying their source or using inheritance:

```kotlin
// Kotlin -- add a method to String
fun String.isEmail(): Boolean = this.contains('@') && this.contains('.')

// Now available on all Strings
if (input.isEmail()) { ... }
```

This is used pervasively for domain-specific APIs, library interop, and
keeping core types lean while allowing contextual extensions.

#### What Ecstasy has today

Ecstasy has **mixins** which can add methods to types:

```x
mixin EmailValidation into String {
    Boolean isEmail() = this.contains('@') && this.contains('.');
}
```

But mixins require declaration at the type level -- they must be incorporated
by the target type or applied via annotation. You cannot ad-hoc add a method
to `String` in your own module without modifying `String`'s declaration.

This is a deliberate design choice (explicit is better than implicit), and it
avoids the "where did this method come from?" confusion that plagues Kotlin
and C# extension methods. But it means that the common patterns Kotlin uses
extensions for -- utility methods, domain adapters, DSL construction -- require
either top-level functions (losing the fluent `obj.method()` syntax) or mixins
declared on the target type (requiring source access).

#### Trade-off

This is a genuine design tension with no clearly right answer. Extension
methods are powerful but can make code harder to navigate. Ecstasy's explicit
mixin approach is safer but more verbose. Whether to add extension methods
is a philosophical choice that depends on how much the language wants to
prioritize discoverability vs. composability.

If Ecstasy chose to add them, a reasonable design would be module-scoped
extensions (visible only within the importing module), avoiding the
global-pollution problems of Kotlin and C#:

```x
// Hypothetical: module-scoped extension
extends String {
    Boolean isEmail() = this.contains('@') && this.contains('.');
}
// Only visible within this module
```

---

### Collection factory functions

#### What developers expect

Kotlin, Scala, and modern Java (via `List.of()`, `Map.of()`) provide concise
factory functions for creating collections:

```kotlin
val list = listOf("a", "b", "c")           // immutable
val mutableList = mutableListOf("a", "b")   // mutable
val map = mapOf("key" to "value")           // immutable
```

#### What Ecstasy has today

Ecstasy has **array literals** (`[1, 2, 3]`) and **map literals** (`["a"=1]`)
which cover the most common cases well. For specific collection types, you
must use constructors:

```x
// Array literal -- works well
Int[] nums = [1, 2, 3];
Map<String, Int> m = ["a"=1, "b"=2];

// But for specific types, verbose
ListMap<String, Int> ordered = new ListMap<String, Int>();
ordered.put("a", 1);
ordered.put("b", 2);
```

Ecstasy's literal syntax is already better than Java's. The gap compared to
Kotlin is minor -- it's mainly the "specific collection type with initial data"
case that's verbose, and scope functions (`apply`) would largely solve it:

```x
// With apply (proposed)
ListMap<String, Int> ordered = new ListMap<String, Int>().apply(m -> {
    m.put("a", 1);
    m.put("b", 2);
});
```

This is a low-priority gap that scope functions would mostly address.

---

### Summary: what's missing vs. what's deliberate

| Feature | Status | Priority | Builds on |
|---------|--------|----------|-----------|
| **Sealed types** | Conceptualized but not implemented | High | Existing enum exhaustiveness |
| **Pattern matching with destructuring** | Partial (type dispatch, tuples) | High | Existing switch expression |
| **Result type** | Not present | Medium | Sealed types + pattern matching |
| **Extension methods** | Not present (mixins are alternative) | Low | Deliberate design choice |
| **Collection factories** | Mostly solved by literals | Low | Scope functions fill the gap |
| **`if` as expression** | Not present | High | See Part III |
| **`void` as `Unit` type** | Not present | Medium | See Part III |
| **Scope functions** | Not present | High | See Part I |
| **Pipe operator** | Not present | Low | Scope functions are sufficient |
| **Raw strings** | Not present | Low | Nice to have |

The three highest-impact additions would be **sealed types**, **pattern matching
with destructuring**, and **scope functions** (covered in Part I). These three
features form a mutually reinforcing package:

- Sealed types define closed hierarchies.
- Pattern matching destructures them exhaustively.
- Scope functions chain the results fluently.

Together they would move Ecstasy from "powerful but occasionally verbose" to
"expressive and concise" -- matching the expectations of developers coming from
Kotlin, Rust, or modern Java in 2026.
