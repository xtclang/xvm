---
name: ecstasy
description: Practical notes for writing Ecstasy code and wiring new libraries into the XDK build. Use when authoring or modifying .x files, adding/modifying lib_* modules, or touching the gradle config for XTC modules in this repo.
---

# Ecstasy / XDK working notes

Practical notes about Ecstasy syntax and the XDK build, captured while iterating on actual code.
Not exhaustive — entries are what's been observed firsthand. Verify against the language
spec before relying on anything subtle.

## Module structure

- Module names follow the convention `<name>.xtclang.org` (e.g. `metrics.xtclang.org`).
- The module file lives at `src/main/x/<name>.x` and declares dependencies as package
  imports inside the module body:
  ```
  module metrics.xtclang.org {
      package agg import aggregate.xtclang.org;
  }
  ```
  Types from a dependency are then referenced as `agg.Sum`, etc.
- Source files for the module live under `lib_<name>/src/main/x/<name>/...` matching the module name
  (e.g. `lib_metrics/src/main/x/metrics/TimeSeries.x`).

## Adding a new lib to the XDK

Wiring a fresh `lib_<name>` requires touching four places:

1. `lib_<name>/build.gradle.kts` — uses `alias(libs.plugins.xtc)` and declares deps via
   `xtcModule(libs.xdk.<dep>)`.
2. `xdk/settings.gradle.kts` — add `"lib_<name>"` to the subprojects list.
3. `gradle/libs.versions.toml` — add `xdk-<name> = { group = "org.xtclang", name = "lib-<name>" }`.
4. `xdk/build.gradle.kts` — add `xtcModule(libs.xdk.<name>)` to the dependencies block.

Underscores in directory names become dashes in Gradle project and published artifact names:
`lib_metrics` is built as `lib-metrics`. The remap happens in `xdk/settings.gradle.kts`.

## Argument-type-aware name resolution

When a parameter's type is known, you can drop the type qualifier on its constants/statics.
Example: a parameter of declared type `Duration` accepts `Minute` instead of `Duration.Minute`.
This applies anywhere the compiler can infer the target type — method calls, returns,
assignments to typed variables.

## Null-checking with type narrowing

The `?=` operator binds a non-null value into a fresh name when the right-hand side is
non-null:
```
if (T name ?= nullableExpr) {
    // `name` is non-null and typed T here
}
```

`if` accepts comma-separated conditions, so a narrow-and-test in one step:
```
if (Bucket b ?= buckets[slot], b.index == i) { ... }
```

## Assertions

`assert <condition> as "<message>";` — the `as` clause supplies the failure message and is
a normal `String` expression (template literals like `$"..."` work).

## Common idioms

- Prefer range-based `for` over C-style: `for (Int i : 0..<n)` instead of
  `for (Int i = 0; i < n; i++)`. Use `lo..hi` for inclusive upper, `lo..<hi` for exclusive.
- Arrays of nullable elements: `new Element?[capacity]` is auto-`Null`-initialized.
- `Int.minOf(a, b)` / `Int.maxOf(a, b)` — static helpers on `Int`. Instance equivalents
  exist: `a.minOf(b)` and `a.maxOf(b)`. For clamp-style code, `a.notLessThan(b)` reads
  more naturally than `Int.maxOf(a, b)` (lower-bound clamp).
- `public/private` property declaration makes a field publicly readable but only privately
  writable: `public/private Duration resolution;`.
- Compact const declaration with positional fields:
  `private const Bucket(Int index, Value value);` — one-line, no body needed.
- A nested *non-static* `const` (or `class`) can reference the enclosing class's type
  parameters; a `static` nested type cannot.
- Try to avoid double-negative: use "if (a==b) {f2()} else {f1();}" instead of "if (a!=b) {f1()} else {f2();}"
- Use `if (Int offset := text.indexOf("needle"))` for substring checks; do not assume a
  Java-like `String.contains()`.
- Interfaces and classes can be placed inside of the method if that method is the only user of the interface/class.

## Aggregator API (`ecstasy.collections.Aggregator`)

- `Aggregator<Element, Result>` is the core interface.
- `init(Int capacity = 0)` returns an `Accumulator`, which is a typedef for
  `Appender<Element>`.
- Caller pushes elements into the accumulator via `.add(value)`.
- `reduce(accumulator)` produces the final `Result`.

The `aggregate.xtclang.org` module ships ready-made impls: `Sum`, `Min`, `Max`, `Average`,
`MinMax`. `Sum`/`Min`/`Max` have `Element == Result`, which makes them composable as both
intra-bucket and cross-bucket folds. `Average` does not (it is generic, e.g. `Int` → `Dec`), so it
cannot be chained the same way.

## Time and Duration (`ecstasy.temporal`)

- `Time` carries `Int128 epochPicos` and a `TimeZone`.
- `Duration` is `Int128 picoseconds`.
- Static instances on `Duration`: `Nanosec`, `Microsec`, `Millisec`, `Second`, `Minute`,
  `Hour`, `Day`.
- Conversion constants: `Duration.PicosPerNano`, `PicosPerMicro`, `PicosPerMilli`,
  `PicosPerSecond`, `PicosPerMinute`, `PicosPerHour`, `PicosPerDay`.
- Bucket-by-time math: `epochPicos / resolution.picoseconds` gives an epoch-aligned bucket
  index in `Int128`; call `.toInt64()` when storing it.

## Miscellaneous

- Add succinct comments when code intent is not obvious; avoid comments that simply restate the
  code.
- Prefer typedef aliases for complex/repeated Ecstasy types.
- Do not change existing comments/x-doc without explicit permission.
- Each file that is modified should end with a line terminator so that there is a blank line at the
  end of the source file, line feeds are allowed but all carriage returns must be removed, and all
  trailing tabs and spaces must be removed from each line.

## Line width

- Source line limit is **100 characters**, including indent and any comment-prefix (`     * `).
- Doc-comment paragraphs should be reflowed to fill the available width — do not wrap
  conservatively at ~80 cols. Aim each line near 100 to minimize line count.

## X-Doc
- In x-doc, there should be one blank line before and after (1) the group of `@param` lines, (2) the
  group of `@return` lines, and (3) the group of `@throws` lines, but the last line of the x-doc
  must never be a blank line.
