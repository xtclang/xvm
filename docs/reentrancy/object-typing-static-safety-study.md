# Object-Typing And Static Safety: A Design Study

Date: 2026-08-28
Branch: `lagergren/lazy-instance` @ `35895971a`
Scope: `javatools/src/main/java/org/xvm` (802 files, 287,916 lines)
Status: **analysis only — no production code was changed by this study**

All counts were re-run against `35895971a` after HEAD advanced mid-study (the working tree moved
under this analysis when an `ObjectHandle` migration commit landed). The aggregate figures —
2,965 casts, 1,576 `instanceof`, 1,436 handle downcasts, 561 `(X) hTarget`, 170 positional
`ahArg[N]`, 222 `getComponent()` downcasts — are stable across that move.

This document answers one question: where does the codebase promote a type distinction that
Java could have checked into a runtime cast, a string, an `int`, or an `Object`, and what
would it cost to give that distinction back to the compiler?

It deliberately does **not** re-litigate sealing. That question is already settled in
[`sealed-hierarchy-audit.md`](sealed-hierarchy-audit.md), whose verdicts this study accepts
and builds on. The complementary question — *"what is the thing being switched on, and does
it have a type?"* — is the subject here.

---

## 0. Executive summary

### The five highest-value changes, ranked

| # | Change | Sites | Why it is #n |
|---|---|---:|---|
| **1** | **Retire the `Format`-string-concatenation dispatch in constant folding.** `IntConstant.apply`, `LiteralConstant.apply`, `ByteConstant.apply`, `DecimalConstant.apply`, `DecimalAutoConstant.apply` each `switch` on a **runtime-concatenated `String`** built as `getFormat().name() + op.TEXT + that.getFormat().name()`. | **5 methods, 1,279 `case "…"` labels** | Highest defect density per line in the repo. A mistyped label is undetectable; a *wrong* match folds a wrong constant into the compiled program. `IntConstant.apply` alone is 609 lines (`IntConstant.java:332-940`). This is the purest instance of the complaint and nothing in E2/E11 covers it. |
| **2** | **Type the nested-identity union (`Object nid`).** `IdentityConstant.getNestedIdentity()` returns `Object` whose real variants are `String \| Integer \| SignatureConstant \| NestedIdentity \| null`, and it is used as a **map key** in the compiler's TypeInfo/composition layer. | **79 `Map<Object, …>` declarations; 64 `get/resolveNestedIdentity` call sites** | This is the single largest surviving `Object` union, it is load-bearing for `equals`/`hashCode` (bug-list row 11), and `NestedIdentity` is a *non-static inner class* that captures its enclosing `IdentityConstant` — an ownership hazard hiding inside an untyped key. The `MethodBody.Target` conversion already proved the technique on this exact shape. |
| **3** | **Close the `Format`-enum / class-hierarchy drift with a generated or compiler-checked mapping.** `Constant.Format` has **107** values against **89** `Constant` subclasses; `Component.Format` has **16** against **9**. The tag is finer than the type, so sealing alone cannot fix it — the *mapping* needs to be total. | **~35 `switch (format)` sites; 141 `getFormat()` switches overall** | This study found **two live drift bugs** here (§6.1, §6.2) in ninety minutes of reading. The drift is systemic, not incidental. |
| **4** | **Make `ValueConstant` generic: `ValueConstant<V>`.** | **1 declaration, ~26 overrides, and exactly 6 affected call sites** | Verified: only **six** sites in the entire codebase consume `getValue()` through a statically-`ValueConstant`-typed receiver, and all six are in `CaseManager.covers()`. Verified: the change is **bytecode-identical** — javac already emits a synthetic `Object getValue()` bridge in every subclass. This is the cheapest real win available. |
| **5** | **Narrow `IdentityConstant.getComponent()` covariantly** in the concrete identity constants (§5.11). | **8 overrides remove 222 downcasts** | The best effort-to-yield ratio measured in this study. `IdentityConstant` is already sealed and every subclass already knows what it names; the code just throws that fact away and 222 call sites cast it back. Bytecode-identical (bridge methods), and it is in `asm`, so it pays on both execution engines. |

**Honourable mention (rank 6):** `ClassTemplate<H extends ObjectHandle>` (§5.6) collapses **561
`(X) hTarget` casts into 5 bridges** invoked from 10 `CallChain.java` sites — a larger absolute
number than any of the above. It is ranked below them only because of the finding immediately
following.

### The single most surprising finding

**The largest cast population is on the path with the lowest strategic value.** `runtime/template/**`
holds 1,354 of the repo's 2,965 casts (46%) and 1,196 of the 1,436 handle casts (83%) — but
[`jit-implications.md:17-40`](jit-implications.md) establishes that the JIT uses *none* of
`Frame`, `ObjectHandle`, `ClassTemplate`, `TypeComposition`, or `Container`, while it uses
`org.xvm.asm` heavily. A cast eliminated in `asm/` pays on both execution engines; a cast
eliminated in `runtime/template/` pays only on the one the README calls "a proof-of-concept
interpreted runtime". **Ranking families by cast count gets the priority order exactly
backwards.** The staged plan in §7 is ordered by `asm`-first for this reason.

### What I would explicitly NOT do

- **Do not seal `Op`, `ObjectHandle`, `ClassTemplate`, or the root `Constant`.** Already
  measured and decided in `sealed-hierarchy-audit.md:167-177` and `:1039-1070`. I re-checked
  the `Op` reasoning independently and agree with it: `Op` dispatches on an `int` opcode
  (824 sites) versus 14 `instanceof` checks, so `permits` buys nothing. §5.5 proposes a
  different mechanism for `Op` that is not sealing.
- **Do not attempt a big-bang re-typing of `runtime/template/**`.** See above.
- **Do not add `Class<T>` token parameters as the general answer.** The codebase already has
  three (`Frame.getConstant(int, Class<T>)` at `Frame.java:1408`, `Component.getChild(Constant,
  Class<T>)` at `Component.java:1464`, `Component.getChild(String, Class<T>)` at `:1682`).
  They move the failure from the caller's cast to the callee's `Class::cast` — a better error
  message, but still a runtime failure. They are a *transitional* device, not a design.
- **Do not annotate the `Object` unions `@Nullable` and call it done.** As
  `nullness-annotation-audit.md:50-60` already argues, making a value total beats documenting
  that it is not. `getNestedIdentity()` returning `null` for a non-nested identity is the
  exact case.

---

## 1. Method

Every number below was produced by a command recorded in Appendix A and re-run against the
working tree on 2026-08-28. Every code claim carries a `file:line`. Where I could not
establish something, I say so in the text rather than rounding it into a fact.

Three claims in this document were verified by *execution* rather than reading, and are marked
**[executed]**: the `getValue()` bridge-method claim (§5.3), the `AstNode` dead-guard claim
(§6.3), and the `Op` opcode-switch drift (§6.1).

---

## 2. Inventory with counts

### 2.1 Aggregate, by category

| Category | Count | Command |
|---|---:|---|
| (a) `Object` as a method return type or parameter type | 68 returns / 167 params | A.1, A.2 |
| (a) fields declared `Object` | 110 | A.3 |
| (b) explicit downcasts to a named type | **2,965** | A.5 |
| (c) `instanceof` occurrences | **1,576** | A.6 |
| (d) raw/unparameterized generic declarations | 25 (10 raw `Class`) | A.7 |
| (e) `Object[]` occurrences | 47 (mostly log varargs — see §4.1) | A.4 |
| `switch` arms that are `default -> throw` | 149 arrow-form + 528 colon-form | A.9 |
| `throw new IllegalStateException` | **860** | A.10 |
| `throw new UnsupportedOperationException` | 265 | A.10 |
| `@SuppressWarnings("unchecked"/"rawtypes")` | 13 | A.8 |
| `case "…"` string case labels | **2,737** | A.11 |

The 860 `IllegalStateException` throws are the cost line. They are, overwhelmingly, the
codebase paying at runtime for facts it declined to state at compile time.

### 2.2 Downcasts by package

| Package | Casts | `instanceof` | `case "…"` |
|---|---:|---:|---:|
| `runtime/template` | **1,354** | 259 | 751 |
| `compiler/ast` | 417 | **516** | 120 |
| `asm/constants` | 396 | 316 | **1,323** |
| `asm` | 295 | 190 | 42 |
| `asm/op` | 183 | 27 | 11 |
| `runtime` | 166 | 157 | 38 |
| `javajit` | 73 | 57 | 280 |
| `asm/ast` | 23 | 8 | 2 |
| `compiler` | 22 | 14 | 52 |
| `javajit/builders` | 8 | 17 | 113 |
| `tool` | 21 | 9 | 5 |
| `type` | 6 | 3 | 0 |
| `api` | 1 | 3 | 0 |

### 2.3 `instanceof` pressure by family

| Family | Occurrences |
|---|---:|
| `*Constant` | 570 |
| `*Expression` / `*Statement` (compiler AST) | 243 |
| `*Handle` | 170 |
| `Component` subtypes | 126 |

### 2.4 Top files

Casts: `ConstantPool.java` 136, `NameExpression.java` 71, `TerminalTypeConstant.java` 71,
`ClassTemplate.java` 60, `xRTType.java` 50, `TypeConstant.java` 50, `xArray.java` 49,
`xConstrainedInteger.java` 47, `ByteConstant.java` 45.

`instanceof`: `NameExpression.java` 76, `TypeConstant.java` 53, `CaseManager.java` 48,
`OwnershipDiagnostics.java` 47, `InvocationExpression.java` 44, `BuildContext.java` 34.

String case labels: `LiteralConstant.java` 563, `IntConstant.java` 550, `Builder.java` 106,
`ByteConstant.java` 102, `NumberSupport.java` 81.

---

## 3. What is already done, and what is genuinely new

This branch has already landed a large sealing + exhaustive-dispatch wave. `sealed` now
appears in **56 files** (84 occurrences, 2 `non-sealed`), against a documented baseline of
**zero** (`sealed-hierarchy-audit.md:249-260`). `Component` is fully sealed with 9 permitted
subclasses (`Component.java:107-110`); `ValueConstant`, `IdentityConstant`, `PseudoConstant`,
`TypeConstant`, `ConditionalConstant`, `FrameDependentConstant` are all sealed.
`MethodBody.Target` was converted from a five-shape `Object` payload to five records plus a
`Narrowing` sub-union.

**Against that baseline, this study's contribution splits three ways:**

| | Family | Status |
|---|---|---|
| **Already specified by E2** | seal `TypeConstant`/`IdentityConstant`/`ValueConstant`/`Component`/`BinaryAST`/parser AST; retire silent-`default` dispatch | Done on branch; E2 is the master port. **Not repeated here.** |
| **Already specified by E11** | `MethodBody` target union; `DefiningConstant` union; `TerminalTypeConstant` format switches; nullness annotations | Done on branch. **Not repeated here.** |
| **Already listed in `generics-api-audit.md` but unfixed** | `ValueConstant` base type (`:757`); native argument casts (`:825`); `Token` values (`:705`); nested identity APIs (`:951`); op-info cache (`:230`); raw `Class` in JIT (`:550`); ConstantPool locator tables (`:368`) | The audit sketched "after" shapes. This study supplies the **feasibility numbers, the blast radius, the performance answer, and a landing order** those entries lack. |
| **Genuinely new here** | §5.1 `apply()` string-concatenation dispatch; §5.5 the `Op` `int` argument-space collisions; §5.9 the `AstNode` reflection child model; §5.4 the `Argument` union; §6 three live bugs | Not mentioned in E2, E11, `generics-api-audit.md`, or `sealed-hierarchy-audit.md`. I searched each for these terms before claiming novelty. |

**One point where this study qualifies E2.** E2 says "every subtype declaration gains a
`permits`, every dispatch site becomes a pattern switch", and lists the `Format` switches
among the things sealing fixes. That is not quite right, and the numbers say why:
`Constant.Format` has **107** values against **89** classes, and `Component.Format` has
**16** against **9**. The tag enum is a strictly *finer* partition than the class hierarchy
(8 `Component.Format` values all map to `ClassStructure`; `Int8`…`UIntN`, `Bit`, `Nibble` all
map to `IntConstant`/`ByteConstant`). Sealing makes *class* switches exhaustive; it does
nothing for *format* switches, which remain 107-way and unwritable by hand. §5.2 proposes the
mechanism that actually closes that gap. E2 should be amended to say so, because a reviewer
reading E2 today would expect the format switches to fall out of the sealing work, and they
will not.

---

## 4. What cannot be typed, and why

Stating this first, so the rest of the document is not read as maximalism.

### 4.1 Genuinely heterogeneous, correctly `Object`

- **Diagnostic message parameters.** `Object[] aoParam` in `Lexer.java:2501`, `:2508`,
  `Parser.java:5618`, `ModuleInfo.java:1051` and friends — these are `MessageFormat`-style
  substitution arguments. Their only contract is `toString()`. `Object[]` is correct.
  (Java records would let you name them, but that is a readability change, not a safety one.)
- **`OwnershipDiagnostics`' reflective graph walker** (`OwnershipDiagnostics.java:403-427`,
  a 14-branch `instanceof` chain over `Container`, `ServiceContext`, `ObjectHandle`,
  `ConstantPool`, `XvmStructure`, `Reference`, `AtomicReference`, `Optional`, `Map`,
  `Iterable`, `Map.Entry`, `CompletableFuture`). This walks arbitrary JVM object graphs by
  design. It is a debugger. `Object` is the correct parameter type and the chain is the
  correct shape.
- **`Xvm.java:241` `private final Object[] locks = new Object[61]`** — a lock stripe. The
  elements are intentionally identity-only.

### 4.2 Deserialization, where the type is not known until the tag is read

`ConstantPool.disassemble` (`ConstantPool.java:2971-3300`) reads a format byte off the wire
and must construct the right class. **The tag-to-class step is irreducibly dynamic** — it is
the boundary where untyped bytes become typed objects. What *is* fixable is that the mapping
is a hand-maintained 103-case `switch` with `default: throw new IOException(...)`
(`ConstantPool.java:3298-3299`) that has **already drifted** (§6.2). The dynamism is
essential; the hand-maintenance is not.

The same applies to `Op.instantiate(int, DataInput, Constant[])`
(`Op.java:1351-1599`, 215 cases) and `Component.Format.instantiate`
(`Component.java:2556-2588`, 9 arms).

### 4.3 Metadata-predicated casts: the same slot legitimately holds two types

Three families where the Java type genuinely varies at the same storage location, gated by a
*side table*, not by the value's own type:

1. `(RefHandle) f_ahVar[nVar]` guarded by `VarInfo.isDynamicVar()` — `Frame.java:851`,
   `:1569-1577`, `:1806`. A register slot holds either a value or a `Ref` to a value.
2. `((GenericHandle) hTarget).getField(frame, field)` then `assert hTarget instanceof RefHandle`
   guarded by `FieldInfo.isInflated()` — `ClassTemplate.java:1038-1042`. Note the `assert`:
   under `-da` this is unchecked, and `PresentCondition.java:69` records that exactly this
   pattern already produced *"a ClassCastException (the old assert ladder vanished under -da)"*.
3. String-keyed field reads: `(BooleanHandle) hRange.getField(null, "lowerExclusive")` —
   `JumpVal.java:214-215`, `:345-346`. 50 such `(X) …getField(…)` casts overall.

Family 1 is fixable by representation change (a `Slot` sum type per register), at real cost.
Families 2 and 3 are fixable only by changing how Ecstasy object layout is modelled in Java —
out of scope for anything incremental.

### 4.4 Cross-value comparability: `CaseManager.covers()`

Answering the seed question directly. `CaseManager.covers()` (`CaseManager.java:906-978`)
compares two constants for switch-case coverage. The comparison is legitimately dynamic
**because the Ecstasy type system, not Java's, decides which two constants are comparable** —
an `Int64` case and an `IntLiteral` case are comparable in Ecstasy but have unrelated Java
carrier types (`PackedInteger` in both here, but `Char` carries `Integer`, enums carry a
`Constant`). No Java generic signature can express "these two independently-obtained values
happen to share a carrier".

That is the honest limit. It does **not** excuse the current shape, which is wrong in two
specific ways (§5.3).

---

## 5. Per-family analysis

Families are numbered by the order they appear, not by priority. The priority order is §0 and
the landing order is §7.

---

### 5.1 `Format`-string-concatenation dispatch in constant folding — **rank 1**

**Verified shape.** Five methods dispatch by concatenating two `enum` names and an operator's
text into a `String`, then switching on it:

```java
// IntConstant.java:332-336
@Override
public Constant apply(Token.Id op, Constant that) {
    switch (that == null
                ?                           op.TEXT + this.getFormat().name()
                : this.getFormat().name() + op.TEXT + that.getFormat().name()) {
    // TODO .is() / .as()

    case "+Int":
    case "+UInt":
    ...
        return validate(this.getValue().add(((IntConstant) that).getValue()));
```

| Site | `case "…"` labels | Extent |
|---|---:|---|
| `LiteralConstant.java:729` | 563 | |
| `IntConstant.java:332` | 550 | 609 lines (`:332-940`) |
| `ByteConstant.java:249` | 102 | |
| `DecimalConstant.java:119` | 48 | |
| `DecimalAutoConstant.java:85` | 16 | |
| **total** | **1,279** | |

Unmatched input falls through to `Constant.apply` (`Constant.java:285-292`), which throws
`UnsupportedOperationException`.

**Why this is the worst family in the repo.**

1. The dispatch key does not exist as a type. `"UInt128>>>Int"` is a string assembled at
   runtime. Nothing checks that it is spelled the same in the case label as the enum will
   produce.
2. The cast inside each arm — `((IntConstant) that).getValue()` — is justified *only* by the
   string having matched. There is no `instanceof`, no `assert`.
3. **Renaming a `Constant.Format` enum constant silently disables constant folding** for every
   arm mentioning it. Java's rename refactoring does not touch string literals. The compiled
   program stays *correct* (folding is optional) but silently slower — the worst class of
   regression, because no test fails.
4. A *wrong* match folds a wrong constant. `"Int-Int"` versus `"Int<Int"` differ by one
   character; `op.TEXT` for `SUB` is `"-"` and for `COMP_LT` is `"<"`. The failure is a
   miscompiled program with no diagnostic anywhere.
5. It is also the largest single readability liability in the codebase: `IntConstant.apply` is
   609 lines with no structure a reader can navigate.

**Redesign.** The operand pair is a two-dimensional dispatch (`Format` × `Op` × `Format`), and
the arms fall into a small number of *semantic groups*. The type that is missing is the
carrier group:

```java
// NEW: asm/constants/NumericDomain.java
/**
 * The value-carrier domain of a numeric constant format. Every {@code Constant.Format} that
 * carries an arithmetic value belongs to exactly one domain; folding is only defined between
 * two constants in the same domain.
 */
public enum NumericDomain {
    SIGNED_INTEGER, UNSIGNED_INTEGER, BINARY_FP, DECIMAL_FP, CHARACTER, BYTE_SIZED;

    /** @return the domain of the format, or empty if the format carries no foldable value */
    public static Optional<NumericDomain> of(Constant.Format format) { ... }
}
```

**What the 550 labels actually encode.** I decomposed them, because "just delete the switch" is
not an argument. Every label has the shape `<lhsFormat><op><rhsFormat>` (or `<op><lhsFormat>` for
unary), and:

- The **legal operand pairing** is exactly two rules: same format on both sides, or
  `IntLiteral` on the right. Every one of the 277 cross-format labels pairs some integer
  format with `IntLiteral` (verified: the only distinct right-hand formats appearing opposite
  `Int` are `Int` and `IntLiteral`), and **all 277 share a single one-line body** at
  `IntConstant.java:580`:
  `return apply(op, ((LiteralConstant) that).toIntConstant(getFormat()));`
  That is 277 labels expressing one `if (that instanceof LiteralConstant lit)`.
- The **operator set** is 22 distinct operators (`+ - * / % & | ^ << >> >>> == != < <= > >= <=>
  .. ..< >.. >..<`), all of which are already `Token.Id` enum constants.
- The **per-format range behaviour** (`Int16` wraps differently from `Int64`) is *not* in the
  labels at all — it is already factored into `validate(PackedInteger)`
  (`IntConstant.java:304-316`), which reads `byteSize()`.

So the 550 labels carry two bits of real information — "same format, or literal on the right" —
multiplied out across 25 formats × 22 operators. That multiplication is what the type system
should be doing.

The folding itself becomes a pattern switch over *types*, not strings:

```java
// AFTER — IntConstant.apply
@Override
public Constant apply(Token.Id op, Constant that) {
    // 277 of the old string labels collapse into this one coercion
    if (that instanceof LiteralConstant lit) {
        return apply(op, lit.toIntConstant(getFormat()));
    }
    if (!(that instanceof IntConstant thatInt) || getFormat() != thatInt.getFormat()) {
        return super.apply(op, that);
    }
    var lhs = this.getValue();
    var rhs = thatInt.getValue();
    return switch (op) {
        case ADD      -> validate(lhs.add(rhs));
        case SUB      -> validate(lhs.sub(rhs));
        case MUL      -> validate(lhs.mul(rhs));
        case DIV      -> validate(lhs.div(rhs));
        case MOD      -> validate(lhs.mod(rhs));
        case BIT_AND  -> validate(lhs.and(rhs));
        case BIT_OR   -> validate(lhs.or(rhs));
        case BIT_XOR  -> validate(lhs.xor(rhs));
        case SHL      -> validate(lhs.shl(rhs));
        case SHR      -> validate(lhs.shr(rhs));
        case USHR     -> validate(lhs.ushr(rhs));
        case COMP_EQ, COMP_NEQ, COMP_LT, COMP_LTEQ, COMP_GT, COMP_GTEQ, COMP_ORD
                      -> translateOrder(lhs.cmp(rhs), op);
        default       -> super.apply(op, that);
    };
}
```

That is roughly 30 lines replacing 609, and every one of the 550 removed string labels becomes
either a compiler-checked `Token.Id` enum constant or a `Format` equality test.

**The `NumericDomain` enum above is needed only if the same-format rule turns out to be too
strict** — I verified it holds for `IntConstant`, but I did **not** verify it for
`ByteConstant`, `DecimalConstant`, or `LiteralConstant`, whose label sets are structured
differently. Decomposing those three the same way is the first task of stage 3, and it is where
the real design judgement lives. `LiteralConstant` (563 labels spanning
`IntLiteral`/`FPLiteral`/`Date`/`Time`/`Duration`/`Path`/`RegEx`) is the one I would expect to
resist a single rule.

**Feasibility.**

| | |
|---|---|
| Width | 5 files, ~1,350 lines deleted, ~200 added. Zero call-site changes (`apply` keeps its signature). |
| Risk | **Medium-high, but bounded and testable.** Constant folding is semantically observable, so a behaviour change is a miscompile. |
| Mechanical? | No — it requires reading each of the five switches and confirming the semantic grouping. `IntConstant` and `ByteConstant` are the easy ones (a clean `PackedInteger` domain); `LiteralConstant` is the hard one (563 labels spanning Date/Time/Duration/Path/RegEx/IntLiteral/FPLiteral, i.e. genuinely different carriers). |
| Incremental? | **Yes, per file.** `DecimalAutoConstant` (16 labels) first as a proof, then `ByteConstant`, `DecimalConstant`, `IntConstant`, `LiteralConstant` last. |
| Gate | A differential-folding test: enumerate every `(Format, Token.Id, Format)` triple, call `apply` on both implementations, assert identical results including identical `UnsupportedOperationException` behaviour. This is a finite space (107 × ~40 × 107) and cheap to exhaust — it makes the refactor a *proof* rather than a hope. **Build this gate before touching any of the five files.** |

**Would this have caught a real bug?** Not one on the current bug list. But see §6.2: the
`Format.TimeZone` bug lives in the same "hand-maintained format table" style, in the same
file, and would be caught by the same exhaustive-triple gate.

---

### 5.2 The `Format`-enum / class-hierarchy drift — **rank 3**

**Verified shape.** Two enums shadow two class hierarchies at different granularities:

| Enum | Values | Classes it shadows | Declared at |
|---|---:|---:|---|
| `Constant.Format` | **107** | 89 `Constant` subclasses | `Constant.java:769-900` |
| `Component.Format` | **16** | 9 `Component` subclasses | `Component.java:2487` |

Both are recovered by ordinal from an `int` — `Constant.Format.valueOf(int)` indexes a
`FORMATS[]` array, `Component.Format.valueOf(int)` likewise (`Component.java:2624`), and
`Component.Format.fromFlags(int)` (`Component.java:2512`) extracts it from packed bit-flags.

`Component.Format.instantiate` (`Component.java:2556-2588`) shows the tag-and-cast pattern at
its clearest — one enum tag driving *two* parallel casts:

```java
case MODULE  -> new ModuleStructure (xsParent, nFlags, (ModuleConstant)  constId, condition);
case PACKAGE -> new PackageStructure(xsParent, nFlags, (PackageConstant) constId, condition);
case INTERFACE, CLASS, CONST, ENUM, ENUMVALUE, ANNOTATION, MIXIN, SERVICE
             -> new ClassStructure  (xsParent, nFlags, (ClassConstant)   constId, condition);
...
default      -> throw new IllegalStateException("uninstantiable format: " + this);
```

The `(ModuleConstant) constId` cast is checked by nothing but the caller having passed a
matching `Format`. `Component` is *fully sealed* and this switch still has a throwing
`default`, because the switch is over the **enum**, not the class.

**The distribution of what happens on an unhandled tag** is already measured in
`sealed-hierarchy-audit.md:210-224` over 167 discriminator switches: 102 throw, 26 have no
`default` at all, 15 return a value, 9 silently `break`, 15 assign and continue. **At 65 of
167 sites (39%) a forgotten tag produces an answer rather than a failure.**

**Redesign.** Since the tag is finer than the type, the fix is not sealing — it is making the
tag↔class relation *total and compiler-checked* by moving the per-format data onto the enum:

```java
// BEFORE: Component.Format is a bare enum; instantiate() is a switch with a throwing default.
public enum Format { INTERFACE, CLASS, CONST, ..., FILE }

// AFTER: each constant carries its own factory; there is no default arm to forget.
public enum Format {
    INTERFACE (ComponentFactory.CLASS),
    CLASS     (ComponentFactory.CLASS),
    CONST     (ComponentFactory.CLASS),
    ...
    MODULE    (ComponentFactory.MODULE),
    PACKAGE   (ComponentFactory.PACKAGE),
    TYPEDEF   (ComponentFactory.TYPEDEF),
    RSVD_D    (ComponentFactory.UNINSTANTIABLE),
    FILE      (ComponentFactory.UNINSTANTIABLE);

    Format(ComponentFactory factory) { this.factory = factory; }

    private final ComponentFactory factory;

    Component instantiate(XvmStructure parent, Constant id, int flags, ConditionalConstant cond) {
        return factory.create(parent, id, flags, cond);   // total by construction
    }
}
```

The payoff is not aesthetic. **Adding a `Format` constant becomes a compile error** (the
constructor requires a factory), which is precisely the property the `default:` arm removes.
The same treatment applies to `Constant.Format` — with 107 constants the constructor argument
list is the *only* place where a new format cannot be forgotten.

For the finer-grained per-format behaviour that cannot live on a shared factory (range limits,
byte sizes, type lookups), attach it as further enum constructor arguments rather than as
`switch (this)` methods. `Constant.Format` already has four such methods
(`isTypeable`, `getType`, and two more), each with a `default`.

**Feasibility.**

| | |
|---|---|
| Width | `Component.Format`: 1 file, 4 switches, 16 constants. `Constant.Format`: 1 file for the enum plus ~35 switch sites across `asm/constants/**`. |
| Risk | Low for `Component.Format` (16 constants, 9 targets, fully enumerable). Medium for `Constant.Format` (the `disassemble` switch is the deserialization boundary; a mistake there is a corrupt-module bug). |
| Mechanical? | Mostly. The judgement calls are which formats are genuinely uninstantiable and which are drift (§6.2 shows those are not the same set today). |
| Incremental? | **Yes** — `Component.Format` first as a small reviewable proof, `Constant.Format` in a second slice. |
| Gate | An enumeration test over all 107 `Constant.Format` and all 16 `Component.Format` values asserting each is either instantiable or explicitly declared uninstantiable, with no third answer. **That test alone, added today with no refactor, catches §6.2.** |

---

### 5.3 `ValueConstant.getValue()` returns `Object` — **rank 4**

**Verified shape.** `ValueConstant.java:44` declares `public abstract Object getValue();`. The
class is `abstract sealed` with 21 permitted subclasses (`ValueConstant.java:11-18`), 27
transitive. Every leaf narrows covariantly:

| Subclass | Return type |
|---|---|
| `IntConstant` (`:156`) | `PackedInteger` |
| `StringConstant` (`:70`), `RegExConstant` (`:66`), `LiteralConstant` (`:189`) | `String` |
| `ByteConstant` (`:231`), `CharConstant` (`:72`) | `Integer` |
| `DecimalConstant` (`:111`), `DecimalAutoConstant` (`:77`) | `Decimal` |
| `Float64Constant` (`:76`) | `Double` |
| `FloatConstant` (`:32`, abstract) and its four leaves | `Float` |
| `UInt8ArrayConstant` (`:93`), `FPNConstant` (`:118`), `Float128Constant` (`:100`) | `FrozenByteArray` |
| `ArrayConstant` (`:158`), `RangeConstant` (`:272`) | `Constant[]` |
| `MapConstant` (`:177`) | `Map<Constant, Constant>` |
| `SingletonConstant` (`:104`), `FSNodeConstant` (`:277`), `EnumValueConstant` (`:91`) | `Constant` |
| `FileStoreConstant` (`:104`) | `FSNodeConstant` |
| **`MatchAnyConstant` (`:73`)** | **`Object` — returns the string `"_"`** |

**Answering the seed question directly: would `ValueConstant<T>` work? Yes, for 25 of 26
overriders, and the blast radius is six lines.**

The measurement that settles it: I searched for every `getValue()` call whose *static receiver
type* is `ValueConstant` (i.e. the only calls that would need a type argument or a cast after
the change). There are **six**, all in `CaseManager.covers()`:

```
CaseManager.java:930   oThisLo = valLo.getValue();
CaseManager.java:931   oThisHi = valHi.getValue();
CaseManager.java:939   oThisLo = oThisHi = ((ValueConstant) constThis).getValue();
CaseManager.java:954   oThatLo = valueLo.getValue();
CaseManager.java:955   oThatHi = valueHi.getValue();
CaseManager.java:963   oThatLo = oThatHi = ((ValueConstant) constThat).getValue();
```

Every other one of the 893 `.getValue()` calls in the tree has a *concrete* receiver and
already gets the narrowed type. The 31 sites that cast a `getValue()` result
(`Builder.java:268/274/280`, `LiteralExpression.java:*`, `Parser.java:*`, …) cast the result of
`Token.getValue()` or `DecimalConstant.getValue()` to a *sub*type — a different problem (§5.8
and below), untouched by this change.

**The two genuine obstacles, stated honestly:**

1. **`MatchAnyConstant` has no value.** Its own comment says so: *"there is no correct answer
   to this question, although null is tempting"* (`MatchAnyConstant.java:73-76`), and it
   returns `"_"`. Typing it `ValueConstant<String>` would be a lie that type-checks. The right
   answer is that `MatchAnyConstant` **is not a `ValueConstant`** — it is a wildcard marker,
   and `CaseManager.covers()` already special-cases it *first*, at `CaseManager.java:908` and
   `:913`, before any `getValue()` call. Moving it out of the `permits` list is a one-line
   change plus whatever `instanceof ValueConstant` sites it currently satisfies.
2. **`EnumValueConstant.getValue()` can return `null`** (`EnumValueConstant.java:91-97`, when
   `getPresumedOrdinal() < 0`). `ValueConstant<Constant>` does not express that. Per
   `nullness-annotation-audit.md`, the fix is to make it total, not to annotate it.

**Redesign.**

```java
// BEFORE
public abstract sealed class ValueConstant extends Constant permits ... {
    public abstract Object getValue();
}

// AFTER
public abstract sealed class ValueConstant<V> extends Constant permits ... {
    /** @return the value of the constant */
    public abstract V getValue();
}

public final class IntConstant extends ValueConstant<PackedInteger> { ... }
public final class MapConstant extends ValueConstant<Map<Constant, Constant>> { ... }
public sealed class SingletonConstant extends ValueConstant<Constant> { ... }
```

And, for the ordering that `CaseManager` actually needs, a narrower interface rather than a
raw `Comparable` test:

```java
/**
 * A {@link ValueConstant} whose carrier admits a total order, so two constants of the same
 * carrier can be compared for switch-case range coverage.
 */
public sealed interface OrderedValueConstant<V extends Comparable<? super V>>
        permits IntConstant, ByteConstant, CharConstant, DecimalConstant, DecimalAutoConstant,
                FloatConstant, Float64Constant, StringConstant, LiteralConstant,
                SingletonConstant {
    V getValue();
}
```

**And now `CaseManager.covers()`.** The seed question asks whether it genuinely needs
heterogeneity. The answer is **yes for the cross-carrier comparison, no for anything else** —
and the current code is wrong in two ways that typing does fix:

```java
// CaseManager.java:942-943 — raw Comparable, which silently disables generic checking
if (!(oThisLo instanceof Comparable cmpThisLo &&
      oThisHi instanceof Comparable cmpThisHi)) {

// CaseManager.java:970-978 — and then this
try {
    return cmpThisLo.compareTo(cmpThatLo) <= 0 && cmpThisHi.compareTo(cmpThatHi) >= 0;
} catch (Exception e) {
    return false;
}
```

`catch (Exception e) { return false; }` catches the `ClassCastException` that `Comparable`'s
own bridge method throws on mismatched carriers. **The code knows the cast can fail and has
decided that a failure means "not covered".** For a `switch` coverage analysis, "not covered"
is the answer that *suppresses a diagnostic* — so a carrier mismatch silently turns off
duplicate-case and unreachable-case detection for that pair. That is a real, if minor,
correctness consequence, not a style complaint. It is also exactly the "frivolous runtime
exception being swallowed" shape.

The typed replacement makes incomparability a **value** rather than an exception:

```java
/**
 * Compare two case values for range coverage.
 *
 * @return the comparison result, or empty if the two constants have unrelated carriers and
 *         are therefore not comparable
 */
private static OptionalInt compareValues(OrderedValueConstant<?> lhs, OrderedValueConstant<?> rhs) {
    return lhs.getClass() == rhs.getClass()
            ? OptionalInt.of(compareSameCarrier(lhs, rhs))
            : OptionalInt.empty();
}

@SuppressWarnings("unchecked")   // guarded by the same-class test above; the only unchecked
                                 // cast in the family, and it is one line
private static <V extends Comparable<? super V>> int compareSameCarrier(
        OrderedValueConstant<V> lhs, OrderedValueConstant<?> rhs) {
    return lhs.getValue().compareTo((V) rhs.getValue());
}
```

One `@SuppressWarnings("unchecked")` on a three-line helper, guarded by an explicit class
test, replacing a `catch (Exception)` around a raw `Comparable`. That is the honest end state:
the heterogeneity is real, but it is now *one* named, guarded, documented site instead of an
exception handler that also swallows `NullPointerException`, `ArithmeticException`, and
anything else `compareTo` might throw.

**Feasibility.**

| | |
|---|---|
| Width | 1 base class + 26 subclass declarations + 1 caller method. `MatchAnyConstant` leaves the hierarchy. |
| Risk | **Low.** The change is source-compatible for 887 of 893 call sites and bytecode-identical (below). |
| Mechanical? | The `ValueConstant<V>` half is fully mechanical. The `CaseManager` half needs one design decision (what "incomparable" means), which the code already answers implicitly. |
| Incremental? | **No — the generic parameter must land atomically** across `ValueConstant` and its 26 subclasses, because a raw `ValueConstant` reference in any of them would erase the whole thing. But it is one commit of purely declarative edits. The `CaseManager` rewrite can and should land separately, after. |
| Performance | **Free — verified [executed].** `javap -p` on the built `IntConstant.class` shows both `public org.xvm.util.PackedInteger getValue()` and a `public java.lang.Object getValue()` marked `ACC_BRIDGE, ACC_SYNTHETIC`. javac *already* emits exactly the bridge it would emit for a generic superclass. The bytecode after this change is byte-for-byte what exists today. |

---

### 5.4 The `Argument` union is real but unsealed — new

**Verified shape.** `Argument` (`Argument.java:11`) has exactly **three** implementors:
`Constant` (`Constant.java:73-75`), `Register` (`Register.java:18-19`), and
`StatementBlock.TargetInfo` (`StatementBlock.java:1527-1528`). 87 `instanceof Register` /
`instanceof Constant` sites depend on that union being closed.

`NameExpression.getMeaning()` (`NameExpression.java:2940-2984`) is the flagship consumer, and
it is *already written as an exhaustive pattern switch* over the union — with two fully
exhaustive nested switches over the sealed `IdentityConstant` and `PseudoConstant` trees, and a
comment explaining why that is better than the old format switch. And then:

```java
// NameExpression.java:2982
default -> throw new IllegalStateException("arg=" + m_arg);
```

That `default` exists solely because `Argument` is not sealed. It is the residue.

**Why it is not sealed:** `sealed-hierarchy-audit.md:282` records the blocker — `Constant` and
`Register` are in `org.xvm.asm`, `TargetInfo` is in `org.xvm.compiler.ast`, and there is no
`module-info.java` anywhere in the build (`javatools/build.gradle.kts:193-195` deliberately
strips `module-info.class` from the fat jar), so the unnamed-module rule requires all direct
permitted subtypes in the same package. **I confirm that assessment.**

**Redesign — the option the audit did not evaluate.** `TargetInfo` is 1 of 3 implementors, is
a nested class of `StatementBlock`, and is described as *"the information learned when
resolving a name to a multi-method, property or outer 'this'"* (`StatementBlock.java:1524-1526`).
It is a compiler-side value with no `asm` dependency except the `Argument` interface itself.
**Move `TargetInfo` into `org.xvm.asm` as a top-level record**, then:

```java
public sealed interface Argument permits Constant, Register, TargetInfo {
    TypeConstant getType();
    boolean isEffectivelyFinal();
    Argument registerConstants(Op.ConstantRegistry registry);
}
```

`NameExpression.getMeaning()` drops its `default` arm, and 87 `instanceof` sites become
exhaustively checkable.

**Feasibility.**

| | |
|---|---|
| Width | 1 class move + 1 `permits` clause. `TargetInfo` is referenced by name in `NameExpression.java` (5 `instanceof`, 2 casts) and `StatementBlock.java`. |
| Risk | **Low.** A package move with no behaviour change. |
| Mechanical? | Yes, apart from deciding whether `TargetInfo` belongs in `asm` conceptually. It arguably does — it implements an `asm` interface and its fields are `asm` types. |
| Incremental? | Yes, standalone. |
| Blocked by | Nothing. This is the cheapest un-taken sealing win in the repo, and it is *not* on E2's list. |

---

### 5.5 `Op` argument encoding: three `int` protocols sharing one range — new

**This section is not a proposal to seal `Op`.** `sealed-hierarchy-audit.md:1039-1070` measured
that and recommended against it; I independently agree (824 opcode-dispatch sites versus 14
`instanceof` checks). The problem with `Op` is elsewhere.

**Verified shape — the `int` argument space is triply overloaded.**

Every op argument is one `int` whose *sign* selects a namespace (`Op.java:2139-2224`,
decoded at `Frame.java:618`, `:1392`, `:1548`):

| id | meaning |
|---|---|
| `>= 0` | frame register index into `Frame.f_ahVar[]` |
| `-1 … -15` | pre-defined argument (`A_STACK` … `A_TUPLE`) |
| `<= -16` | constant-pool index, encoded `CONSTANT_OFFSET - i` |

Three collisions, all verified in source:

1. **`A_LABEL == CONSTANT_OFFSET == -16`** (`Op.java:2218` and `Op.java:2224`). Constant #0
   encodes to exactly the bit pattern of `A_LABEL`. The javadoc resolves it by *convention*:
   *"This id is used by the compiler, but is not used by the Op codes or binary AST."* There is
   no type-level guard, and no assertion.
2. **`R_NEXT … R_RESET` (-1 … -10)** (`Op.java:2232-2281`) occupy the *same integers* as
   `A_STACK … A_STRUCT`, with entirely different meanings. `Op.process(Frame, int)`
   (`Op.java:100`) returns an `int` that is either a new PC or an `R_*` code — also
   undistinguished.
3. **`Register.UNKNOWN = 1_000_000_000`** (`Register.java:828`) is a fourth sentinel outside
   the scheme entirely.

**Verified shape — the two-field "either raw int or resolved Argument" idiom.** Across
`asm/op/**` and the 18 `asm/Op*.java` bases:

| | `asm/op/**` | bases | total |
|---|---:|---:|---:|
| `int m_…` argument-id fields | 96 | 38 | **134** |
| `int[] m_…` | 36 | 2 | **38** |
| `Argument m_…` | 65 | 26 | **91** |
| `Argument[] m_…` | 27 | 2 | **29** |

**84 files declare an `Argument` field; 101 declare an `int m_…` field; 69 declare both.** The
discriminator between the two representations is **field nullity**, not a type:

```java
// Invoke_11.java:59-70 — write() reconciles the two representations by null check
if (m_argValue != null) {
    m_nArgValue = encodeArgument(m_argValue, registry);
    m_nRetValue = encodeArgument(m_argReturn, registry);
}
```

`OpInvocable.java:521-529` declares both sets side by side, and adds a *third* discriminator
(`isMultiReturn()`, `OpInvocable.java:94`) selecting between the scalar and array forms.

`Argument.toIdString(Argument arg, int nArg)` (`Argument.java:40-56`) is the union's
consumer — a 4-way `instanceof`/range cascade over one object plus one int. It carries a
comment recording that this exact ambiguity already caused a bug:

> *"PURE: a constant referenced only by index needs a frame to resolve. Do NOT read the ambient
> ServiceContext/fiber — under a debugger that is whatever frame happens to be current on the
> observing thread, usually NOT this op's frame, so it indexed an unrelated constant array
> (AIOOBE silently swallowed) or printed misleading text."*

That is a real, already-fixed bug whose root cause is that `int` does not say which array it
indexes.

**Redesign — and the honest limit.** The `int[]` encoding *in the serialized form* is
non-negotiable: it is the on-disk `.xtc` bytecode format. What is negotiable is the in-memory
representation between deserialization and execution.

The right shape is a value type that keeps the `int` but names its namespace:

```java
/**
 * An op argument reference. The wire form is a single {@code int}; this type keeps that
 * encoding but makes the namespace explicit so a register id can never be read as a
 * constant-pool index.
 */
public sealed interface ArgId {
    record Reg(int index)          implements ArgId { }   // index >= 0
    record Predefined(Op.Predef p) implements ArgId { }    // A_STACK .. A_TUPLE
    record Const(int poolIndex)    implements ArgId { }    // CONSTANT_OFFSET - id

    /** @return the wire-form encoding of this argument reference */
    int encode();

    static ArgId decode(int id) {
        return id >= 0                    ? new Reg(id)
             : id > Op.CONSTANT_OFFSET    ? new Predefined(Op.Predef.byId(id))
             :                              new Const(Op.CONSTANT_OFFSET - id);
    }
}
```

with `A_STACK`…`A_TUPLE` becoming an `enum Predef` (removing collision 1 by construction,
since `A_LABEL` is compiler-only and does not belong in the runtime enum at all), and `R_*`
becoming a separate `enum Result` (removing collision 2).

**Feasibility — and my recommendation.**

| | |
|---|---|
| Width | Full conversion: 233 op classes, 134 `int` fields, 38 `int[]` fields, plus `Frame`'s decode paths. **Very wide.** |
| Risk | High. This is the interpreter's innermost loop. |
| Performance | **This is where a typed redesign genuinely costs.** `ArgId.decode` allocates unless escape analysis eliminates it, and `f_ahVar[m_nArgValue]` becomes `f_ahVar[argId.index()]` through an interface. On the interpreter's hot path this is not free. See §6.4. |
| **Recommendation** | **Do the `enum` split, not the record union.** Separating `R_*` into its own `enum Result` and `A_*` into `enum Predef` removes both collisions, is a pure declaration change with no allocation, and costs nothing at runtime because the values stay `int`-backed at the array-index sites. **Do not** convert the 134 `int` fields to `ArgId` records — the cost is real and the benefit accrues to an execution engine that the JIT is intended to replace. |
| What I would also do today | Add an assertion or a distinct constant for the `A_LABEL`/`CONSTANT_OFFSET` collision, and delete `OP_NEWC_T`/`OP_NEWCG_T` (§6.1). Both are minutes of work. |

---

### 5.6 `ObjectHandle` and the runtime handle hierarchy — rank 6

**Verified shape.**

| Metric | Value |
|---|---:|
| `ObjectHandle` subtypes | **93** (88 named + 5 anonymous), max depth 5 |
| Handle interfaces | **0** — the hierarchy is 100% single-inheritance `extends` |
| Handle downcasts, whole tree | **1,436** |
| — in `runtime/template/**` | 1,196 (83%) |
| — in `asm/**` | 144 |
| — in `compiler/**` and `javajit/**` | **0** |
| `(X) hTarget` | **561** (39% of all handle casts) |
| `(X) hArg` | 175 |
| positional `(X) ahArg[N]` | 170 |
| `(X) popStack()` / `peekStack()` | 84 |
| `catch (ClassCastException)` under `runtime/template/**` | **0** |
| generic declarations over handles | **1** (`ConstHeap.java:167`) |

`ObjectHandle` is not sealed, and per `sealed-hierarchy-audit.md` it should not be — native
templates mint handle kinds by design. **I agree.** The mechanism here is generics, not
sealing.

**The dominant shape.** Every native method's first statement re-narrows `hTarget`:

```java
// xString.java:144-160 (invokeNativeNN, "indexOf")
StringHandle hThis  = (StringHandle) hTarget;
ObjectHandle hValue = ahArg[0];
ObjectHandle hStart = ahArg[1];

int ofStart = hStart == ObjectHandle.DEFAULT
        ? 0
        : (int) ((JavaLong) hStart).getValue();
```

Three untyped reads in five lines, plus a sentinel-value test (`ObjectHandle.DEFAULT`, 43 such
sentinel comparisons tree-wide).

**Crucially, the `hTarget` cast is safe by construction.** `xString.invokeNativeNN` is reached
only via `CallChain` dispatch on a handle whose `TypeComposition` points back at `xString`.
The invariant exists; it just is not written down. That is what makes this family worth fixing
and *not* the same problem as the positional `ahArg[N]` casts.

**Redesign.** All narrowing funnels through **10 call sites** in `CallChain.java` (`:231`,
`:232`, `:274`, `:324`, `:325`, `:387`, `:450`, `:452`, `:453`, `:517`). So:

```java
// BEFORE
public abstract class ClassTemplate {
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn) { ... }
}
public class xString extends ClassTemplate {
    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        StringHandle hThis = (StringHandle) hTarget;      // × 561 across the tree
        ...
    }
}

// AFTER
public abstract class ClassTemplate<H extends ObjectHandle> {
    /**
     * Erased entry point used by {@link CallChain}. This is the single place where a handle is
     * narrowed to a template's handle type; the invariant is that a handle's TypeComposition
     * names the template that receives it.
     */
    @SuppressWarnings("unchecked")
    final int invokeNative1Erased(Frame frame, MethodStructure method,
                                  ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        return invokeNative1(frame, method, (H) hTarget, hArg, iReturn);
    }

    protected int invokeNative1(Frame frame, MethodStructure method,
                                H hTarget, ObjectHandle hArg, int iReturn) {
        return frame.raiseException("Unknown native(1) method: \"" + method + "\" on " + this);
    }
}
public class xString extends ClassTemplate<StringHandle> {
    @Override
    protected int invokeNative1(Frame frame, MethodStructure method,
                                StringHandle hThis, ObjectHandle hArg, int iReturn) {
        ...                                               // no cast at all
    }
}
```

**561 unguarded casts collapse into 5 `@SuppressWarnings("unchecked")` bridges** (one per
arity, plus `invokeNativeGet`), invoked from 10 `CallChain` sites.

**Feasibility.**

| | |
|---|---|
| Width | 173 `ClassTemplate` subtypes must each declare their handle type; the ~8 intermediate bases (`xConst`, `xService`, `xEnum`, `xRef`, …) must thread the parameter (`public class xConst<H extends GenericHandle> extends ClassTemplate<H>`). |
| Risk | **Medium.** Mostly declarative, but templates that legitimately handle several handle kinds must use the least upper bound, and finding those requires reading each. |
| Mechanical? | Per-file yes; the LUB decisions are not. |
| Incremental? | **Yes, and this is its best property.** Add the type parameter with a default of `ObjectHandle` for every subtype in commit 1 (`class xString extends ClassTemplate<ObjectHandle>` — zero behaviour change, zero casts removed), then narrow one template per commit. Each narrowing commit *deletes* casts and cannot compile if the narrowing is wrong. |
| Performance | Free. Erasure means the emitted bytecode is the checkcast that is already there, moved. |
| **Caveat** | This is the change I would do **last**, precisely because of §0's surprise: it pays only on the interpreter. It is listed as an honourable mention because it is unusually cheap per unit of casts removed, not because the path matters most. |

**What this does NOT fix**, and should not be claimed to: the 170 positional `ahArg[N]` casts
(§5.7), the 50 string-keyed `getField` casts, and the metadata-predicated `RefHandle` casts
(§4.3).

---

### 5.7 Native method dispatch by `String` name — new (partially E11)

**Verified shape.** `ClassTemplate.invokeNativeN` (`ClassTemplate.java:570-596`) dispatches
`switch (ahArg.length)` then `switch (method.getName())`. Across `runtime/`, there are **132
`switch (method.getName())` / `switch (sMethod)` / `switch (sName)` sites in 84 files**, and
**789 `case "…"` string labels**. Property access is worse: `invokeNativeGet(Frame, String
sPropName, ObjectHandle, int)` (`ClassTemplate.java:1110`) and `invokeNativeSet(…, String
sPropName, …)` (`:1131`) take the property *name* as a `String`.

**The failure mode.** A method renamed in the Ecstasy `.x` source, or a typo in the Java case
label, produces `frame.raiseException("Unknown native(1) method: …")` — a *runtime* Ecstasy
exception in whatever program happens to call it. Nothing in the Java build notices.

**Redesign, and the honest constraint.** The names come from the Ecstasy source, so the
mapping fundamentally crosses a language boundary; some string must appear somewhere. But the
*dispatch* need not be a per-invocation string switch. The natural shape is to resolve
name→handler **once**, at template initialization, into a map keyed by `MethodStructure`
identity:

```java
// in ClassTemplate<H>, called once from initNative()
protected void registerNative(String name, int arity, NativeMethod<H> handler) { ... }

@FunctionalInterface
protected interface NativeMethod<H extends ObjectHandle> {
    int invoke(Frame frame, H target, ObjectHandle[] args, int[] returns);
}
```

with `registerNative` **failing loudly at startup** if the named method does not exist on the
template's `ClassStructure`. That converts a silent per-call miss into a deterministic
initialization failure — and `native-template-startup-safety.md` in this same directory
already establishes that startup is the right place for such checks.

**Feasibility.**

| | |
|---|---|
| Width | 84 files, 132 switches, 789 labels. **Very wide.** |
| Risk | Medium-high: it changes the native dispatch mechanism, which every Ecstasy program exercises. |
| Mechanical? | No. Each switch arm becomes a lambda or method reference; the arity nesting has to be unwound. |
| Incremental? | Yes, per template — the base class can support both mechanisms during migration (check the map, fall back to the virtual `invokeNative*`). |
| Performance | **Likely a small win.** A Java `switch` on `String` compiles to `hashCode()` + `equals()` + `tableswitch`, executed on *every native call*. An `IdentityHashMap<MethodStructure, NativeMethod>` lookup, or better an index cached on the `CallChain`, is cheaper. This is one of the few places where the typed design is faster, not merely safer. |
| **Recommendation** | **Do the startup validation first, without the dispatch change.** A test that walks every `ClassTemplate`, extracts its string case labels (or better: a `registerNative`-style declaration it must also provide), and asserts each names a real method on the template's structure, catches the entire failure class for a fraction of the cost. Only then consider the dispatch rewrite — and weigh it against §0's observation that this is interpreter-only code. |

---

### 5.8 `Token.getValue()` returns `Object` — listed in `generics-api-audit.md:705`, unfixed

**Verified shape.** `Token.java:813` declares `private final Object m_oValue;`, exposed by
`Token.java:135 public Object getValue()`. `Token.Id` has 188 constants, of which a handful
carry a payload. The carrier type is fully determined by the `Id`:

| `Id` | carrier | example cast site |
|---|---|---|
| `IDENTIFIER`, `LIT_STRING`, `LIT_DATE`, `LIT_TIME`, `LIT_TIMEZONE`, `LIT_DURATION`, `LIT_PATH` | `String` | `LiteralExpression.java:244`, `TypeCompositionStatement.java:288` |
| `LIT_CHAR` | `Character` | `LiteralExpression.java:240`, `:419` |
| `LIT_INT` and friends | `PackedInteger` | `LiteralExpression.java:152`, `xIntLiteral.java:393` |
| `LIT_DEC` and friends | `BigDecimal` | `LiteralExpression.java:162` |
| `LIT_VERSION` | `Version` | `LiteralExpression.java:52`, `:371` |
| `TEMPLATE` | **`Object[]` whose elements are `Token[]` or `Token`** | `Parser.java:3609-3620` |

25 casts of a token value across the tree. `Token.isSpecial()` (`Token.java:153`) and
`isContextSensitive()` (`:164`) both do `(String) getValue()` inside the class itself.

**Redesign.** Typed accessors that assert the `Id` invariant once:

```java
public final class Token {
    /**
     * @return the token's text value
     * @throws IllegalStateException if this token's id does not carry a text value
     */
    public String textValue() { return require(String.class); }
    public char characterValue() { return require(Character.class); }
    public PackedInteger integerValue() { return require(PackedInteger.class); }
    public BigDecimal decimalValue() { return require(BigDecimal.class); }
    public Version versionValue() { return require(Version.class); }

    private <T> T require(Class<T> carrier) {
        var value = m_oValue;
        if (!carrier.isInstance(value)) {
            throw new IllegalStateException(
                    m_id + " token carries " + (value == null ? "null" : value.getClass().getSimpleName())
                    + ", not " + carrier.getSimpleName());
        }
        return carrier.cast(value);
    }
}
```

This is still a runtime check — the honest ceiling, because `Id` is an enum and Java cannot
correlate an enum constant with a type parameter. But it turns 25 scattered
`ClassCastException`s into one message naming the token id, the actual carrier, and the
expected carrier.

`TEMPLATE`'s `Object[]` of `Token[] | Token` is the one element that should become a real
type: `record TemplatePart(Token[] expression, Token literal)` or a two-case sealed interface.
`Parser.java:3609-3620` is the only consumer.

**Feasibility.** Width: 1 file plus 25 call sites. Risk: low. Mechanical: yes. Incremental:
yes, accessor by accessor. **This is the best "warm-up" commit in the whole study** — small,
self-contained, obviously correct, and it establishes the typed-accessor idiom that §5.10 and
§5.11 reuse.

---

### 5.9 The `AstNode` reflection-based child model — new

**Verified shape.** The compiler AST does not model its children. It looks them up **by
reflection on field-name strings**:

```java
// IfStatement.java:400
private static final Field[] CHILD_FIELDS = fieldsForNames(IfStatement.class, "conds", "stmtThen", "stmtElse");

// InvocationExpression.java:3092
private static final Field[] CHILD_FIELDS = fieldsForNames(InvocationExpression.class, "expr", "args");
```

**65 `fieldsForNames(...)` call sites across 57 files; 122 `CHILD_FIELDS` references.**
`fieldsForNames` (`AstNode.java:1963`) takes a **raw `Class`**. Traversal
(`ChildIteratorImpl.prepareNextField`, `AstNode.java:2079-2110`) reads each field reflectively
and dispatches on `instanceof List` / `instanceof Collection` / `assert next instanceof AstNode`.

This is the most literal instance of "the Python thing" in the codebase: **field access by
string name, at runtime, with the type checked by `assert`.** Renaming an AST field is
invisible to javac, invisible to IDE refactoring, and breaks tree traversal silently.

It also contains **two verified defects** — see §6.3.

**Redesign.** The correct model is that each node declares its children:

```java
// BEFORE
private static final Field[] CHILD_FIELDS =
        fieldsForNames(IfStatement.class, "conds", "stmtThen", "stmtElse");

// AFTER
@Override
protected void forEachChild(Consumer<AstNode> action) {
    conds.forEach(action);
    if (stmtThen != null) { action.accept(stmtThen); }
    if (stmtElse != null) { action.accept(stmtElse); }
}
```

with `remove()` support — which the reflection model provides via `Field.set(node, null)` —
handled by an explicit `replaceChild(AstNode old, AstNode replacement)` override, or by
keeping the iterator but backing it with a per-node array of `Function<Node, AstNode>` getters
and `BiConsumer` setters (method references, not strings).

**Feasibility.**

| | |
|---|---|
| Width | 57 files, 65 declarations. But each is 3-6 lines and entirely local. |
| Risk | **Low-medium.** Traversal semantics (order, `remove()`, `replaceWith()`) must be preserved exactly. A differential test comparing old and new traversal over a parsed corpus makes this provable. |
| Mechanical? | Yes — the field names are right there in the existing string arrays. |
| Incremental? | **Yes.** Keep `children()` dispatching to `getChildFields()` by default; override `forEachChild` per node; delete `fieldsForNames` when the last one is converted. |
| Performance | **A win.** Reflective `Field.get` on every child access, per node, per traversal, on every compile. Direct field reads are faster and JIT-inlinable. |
| **Recommendation** | **Do it**, and do it early — it is one of the few families that is simultaneously safer, faster, and shorter. It is also on E2's "backlog only" list (`sealed-hierarchy-audit.md` calls the parser AST "very large permits lists, many subclasses and inner classes, **reflection-based child iteration**") — removing the reflection is a *prerequisite* for that sealing work, not an alternative to it. |

---

### 5.10 The `Object nid` nested-identity union — rank 2

**Verified shape.** `IdentityConstant.getNestedIdentity()` (`IdentityConstant.java:278-282`)
returns `Object`. The complete variant set, from the four overrides:

| Variant | Produced by |
|---|---|
| `String` (a member name) | `PropertyConstant.java:464-470`, `IdentityConstant.getPathElement()` (`:141-143`) |
| `Integer` (a lambda ordinal) | `MethodConstant.getPathElement()` (`:412-416`): `isLambda() ? Integer.valueOf(m_iLambda) : m_constSig` |
| `SignatureConstant` (a method signature) | same line |
| `NestedIdentity` (an **inner** class) | `IdentityConstant.getCanonicalNestedIdentity()` (`:304`), `resolveNestedIdentity` (`:292-298`) |
| `null` | `IdentityConstant.java:279-281`, when the identity is not nested |

**64 `get/resolveNestedIdentity` call sites; 79 `Map<Object, …>` declarations** — including
the compiler's and runtime's most important tables:

- `Map<Object, ParamInfo>` — `TypeConstant.java:2231`, `RelationalTypeConstant.java:542`,
  `DifferenceTypeConstant.java:294-301`
- `Map<Object, FieldInfo>` — `TypeComposition.java:218`, `ClassComposition.java:444`, `:490`,
  `:623`, `:723`, `:920`, `PropertyComposition.java:304`, `DelegatingComposition.java:78`
- `Map<Object, CallChain>` — `ClassComposition.java:994`, `PropertyComposition.java:380`
- `CallChain getMethodCallChain(Object nidMethod)` — `TypeComposition.java:171` and its three
  implementations

**Three concrete hazards, all verified:**

1. **`NestedIdentity` is a non-static inner class** (`IdentityConstant.java:405`), so every
   instance captures its enclosing `IdentityConstant` — and therefore that constant's
   `ConstantPool` and owner. An untyped map key silently retains an owner reference. This is
   directly in scope for the reentrancy work and is not called out anywhere I could find.
2. **`NestedIdentity.hashCode()`** (`IdentityConstant.java:445-459`) mixes
   `oPath.hashCode()` where `oPath` is `String | Integer | SignatureConstant`. Bug-list row 11
   is precisely about hash/equality contracts on this metadata layer; the untyped key is why
   the contract is hard to state.
3. **`null` is a legal key.** `getNestedIdentity()` returns `null` for a non-nested identity,
   and the maps are `HashMap`/`ListMap` (which accept it). Any future move to `Map.of` or a
   `ConcurrentHashMap` — the latter being exactly what bug-list row 19 did to `f_implicits` —
   would turn that into an NPE.

**Redesign.** The `MethodBody.Target` conversion already proved the technique on this shape
(`sealed-hierarchy-audit.md:1096-1100`: *"five records + a Narrowing sub-union replaced the
five-shape `Object` payload convention — constructor assert-switch deleted, all 11 payload
casts and the raw `MethodInfo[2]` union gone, mispairing loud on every JVM, and javac itself
enumerated the untyped call sites the greps had missed"*).

```java
/**
 * Identifies a member relative to the class within which it nests. The variants are the
 * complete set produced by {@link IdentityConstant#getNestedIdentity()}.
 */
public sealed interface Nid {
    /** A member identified by simple name (property, or a method whose parent is not nested). */
    record Named(String name) implements Nid { }

    /** A lambda identified by its ordinal within its enclosing method. */
    record Lambda(int ordinal) implements Nid { }

    /** A method identified by its full signature. */
    record Signed(SignatureConstant signature) implements Nid { }

    /** A member of a nested (or generically resolved) namespace. */
    record Nested(NestedIdentity identity) implements Nid { }
}
```

and `getNestedIdentity()` returns `Optional<Nid>`, removing the `null` variant. Every
`Map<Object, X>` becomes `Map<Nid, X>`.

**Feasibility.**

| | |
|---|---|
| Width | 4 override sites, 64 call sites, 79 map declarations. **Wide** — but javac enumerates every one, exactly as it did for `MethodBody.Target`. |
| Risk | **Medium.** The keys participate in `equals`/`hashCode` across the whole TypeInfo layer; a change in key identity changes cache behaviour. The `TypeComparisonCorpusTest` gate (E12) is the right instrument, since it already pins "interning identity, order-sensitive parameterized-type equality, `isA` relations". |
| Mechanical? | The record definitions are; the `Optional` return needs a decision at each of the 64 call sites (does `null` mean "not nested" or "error"?). |
| Incremental? | **Partly.** The `Nid` type and the four producers can land first, with a compatibility bridge (`Object asLegacyKey()`), then the maps convert one at a time. The final `Object`→`Nid` map-key flip must be atomic per map. |
| Performance | Neutral. The records wrap values that are already boxed (`Integer`, `String`, object references); `Lambda(int)` is *cheaper* than `Integer.valueOf` beyond the cache range. `hashCode`/`equals` become record-generated and monomorphic per variant, which helps rather than hurts. |
| **Would it have caught a real bug?** | **Yes — bug-list row 11.** The row's own text says: *"The `MethodBody` piece is the deeper type-safety problem… This is where the code should lean on explicit key types or sealed identity shapes rather than deferring meaning to runtime graph walks and casts."* `MethodBody.Target` was the first half. `Nid` is the second half of the *same* finding, in the same layer, and it is still open. |

---

### 5.11 `Component`/`XvmStructure` child lookup — **rank 5** (E2 covered the sealing, not this)

**Verified shape.** `Component` **is** already sealed (`Component.java:107-110`, 9 subclasses,
`ClassStructure` itself sealed over `ModuleStructure`/`PackageStructure`). And yet:

**394 unambiguous downcasts** to `Component` subtypes remain (422 including javadoc
false-positives): `(ClassStructure)` 219, `(MethodStructure)` 66, `(PropertyStructure)` 58,
`(ModuleStructure)` 33, `(MultiMethodStructure)` 17, `(Component)` 11, `(PackageStructure)` 8,
`(FileStructure)` 8, `(TypedefStructure)` 2. Concentrated in `compiler/ast` (104),
`asm/constants` (102), `asm` (101), and `runtime/template/_native/reflect` (60).

**Sealing did not remove them, and could not have**, because the pressure is not on *dispatch*
— it is on *lookup return types*:

| Method | Line | Returns |
|---|---|---|
| `getChild(Constant)` | `Component.java:1444` | `Component` |
| `getChild(String)` | `Component.java:1642` | `Component` |
| `getChildByPath(String)` | `Component.java:1509` | `Component` |
| `getChildByNameMap()` | `Component.java:743` | `Map<String, Component>` |
| `children()` | `Component.java:1706` | `Collection<? extends Component>` |
| `IdentityConstant.getComponent()` | `IdentityConstant.java:628` | `Component` |

`IdentityConstant.getComponent()` is the hottest source, and the class downcasts its own result
three lines later: `((ClassStructure) this.getComponent()).extendsClass(clzSuper)`
(`IdentityConstant.java:646`).

**The creation side is already typed** — `createClass → ClassStructure`
(`Component.java:1136`), `createMethod → MethodStructure` (`:1264`), `createProperty →
PropertyStructure` (`:1187`), and five more. So the asymmetry is precisely: *you know what you
made, and forget it the moment you look it up*.

**Redesign.** The existing `Class<T>` overloads (`Component.java:1464`, `:1682`) are the
transitional shape. The *design* shape is to type the lookup by what the caller has:

**Measured:** `222` of the ~400 `Component`-subtype downcasts are applied directly to a
`getComponent()` result (113 of them `(ClassStructure)`):

```bash
rg -c --pcre2 '\((ClassStructure|MethodStructure|PropertyStructure|ModuleStructure|PackageStructure|TypedefStructure|MultiMethodStructure)\)\s*[A-Za-z0-9_.]*getComponent\(\)' \
   $SRC --glob '*.java' | awk -F: '{s+=$2} END{print s}'      # -> 222
```

```java
// BEFORE — 222 sites like this
ClassStructure clz = (ClassStructure) idClass.getComponent();

// AFTER — the id already knows what it identifies
public abstract sealed class IdentityConstant extends ... {
    /** @return the component this identity names */
    public Component getComponent() { ... }
}
public sealed class ClassConstant extends NamedConstant {
    @Override
    public ClassStructure getComponent() { ... }        // covariant narrowing
}
public final class MethodConstant extends NamedConstant {
    @Override
    public MethodStructure getComponent() { ... }
}
public sealed class PropertyConstant extends NamedConstant {
    @Override
    public PropertyStructure getComponent() { ... }
}
```

**Covariant return narrowing on `getComponent()` is the single highest-yield change here**, and
it costs nothing: `IdentityConstant` is already sealed (`IdentityConstant.java:35`), its
subclasses already know their component type, and javac generates the bridge. **Eight
declarative overrides address 222 cast sites** — the best ratio in this study.

**Feasibility.** Width: 4 override sites (`IdentityConstant.java:628`, `ModuleConstant.java:185`,
`DecoratedClassConstant.java:89`, `PureIdentityConstant.java:95`) plus a `ClassConstant`,
`MethodConstant`, `PropertyConstant`, `PackageConstant`, `TypedefConstant` override each; then
delete casts opportunistically. Risk: **low** — it is pure covariant narrowing, source- and
binary-compatible. Mechanical: **yes**. Incremental: **yes, one constant type per commit,
deleting casts as they become redundant**. Performance: free (bridge methods, as §5.3).

**This is the highest cast-removal-per-line-changed ratio in the study** and it is not on E2's
list, because E2 treated `Component` as a *sealing* problem and it is a *return type* problem.

---

### 5.12 Remaining families — summary table

These are real but either already documented with a plan, or lower-yield. Listed for
completeness with counts, no redesign sketch.

| Family | Count | Status | My recommendation |
|---|---:|---|---|
| `ServiceContext.getOpInfo(Op, Enum) → Object` with a **raw `Enum`** key (`ServiceContext.java:230`, `:246`); consumed as `(CallChain) context.getOpInfo(this, Category.Chain)` (`OpInvocable.java:131-132`) with two *different* `Category` enums (`OpInvocable.java:532`, `OpVar.java:316`) | 2 API methods, ~12 consumers | `generics-api-audit.md:230` "Must Fix" | **Do it** — a `record OpInfoKey<T>(Op op, String category, Class<T> type)` or per-op typed fields. Small and self-contained. |
| Raw `Class` in JIT/loader (`ModuleLoader.java:88`, `JitConnector.java:91`, `:106`, `:109`, `:110`, `:119`) | 10 raw `Class` declarations | `generics-api-audit.md:550` | **`Class<?>` today, one commit.** Genuinely erased (generated classes), so `Class<?>` is the honest ceiling. |
| `ConstantPool` implicit-import table: `Map<String, String[]> s_implicits` (`:4292`), looked up by string (`getImplicitlyImportedIdentity(String)`, `:1561`), with **112 `(ClassConstant) getImplicitlyImportedIdentity("Name")` casts** at `:2532-2645`. The package-vs-class decision is made by **testing whether a path segment's first character is uppercase** (`:1581-1582`) | 112 casts, 1 string table | `generics-api-audit.md:368` "Must Fix"; the map itself is bug-list row 19 | **Generate the accessors.** The ~112 names are a closed set known at build time; an annotation processor or a generated `ImplicitName` enum removes both the strings and the casts. Medium effort, high clarity. Note the `s_implicits` table is parsed from `implicit.x` at class-init — genuinely dynamic *input*, but a closed set of *consumers*. |
| `AstNode.getDumpChildren() → Map<String, Object>` with raw `Map`/`Collection`/`Iterator` patterns (`AstNode.java:1772-1852`) | 1 method | not documented | **Leave it.** Debug-only rendering. Fix the raw types (`case Map<?,?> map`) in passing; nothing more. |
| `xRTServer.HttpServerHandle.f_aoNative = new Object[3]` (`xRTServer.java:935`) | 1 field | not documented | **Convert to a record.** The comment at `:932-934` explains the array exists so cloning does not splinter state — a `record NativeServerState(Router router, HttpServer server, Executor executor)` in a single final field does the same thing with names. |
| `TypeComposition` root unsealed (5 implementations, one test fake) | — | `sealed-hierarchy-audit.md` "must consider, with a test adapter" | Agree with the existing verdict; nothing to add. |

---

## 6. Bugs this study found

Four defects, all in the families under study, all verified against the working tree. **None
appears in `master-issue-submissions.md`.** They are offered as evidence for the thesis, not as
a filing recommendation — each needs a red proof before filing, per the discipline in
`README.md` for this directory.

### 6.1 `Op.toName` is missing 16 opcodes; `Op.toString()` throws on them **[executed]**

`Op.instantiate(int, …)` (`Op.java:1351-1599`) has **215** `case OP_*` labels.
`Op.toName(int)` (`Op.java:1603-1806`) has **201**. They are hand-maintained and have drifted:

```bash
comm -23 <(awk 'NR>=1353 && NR<=1600' Op.java | grep -oE 'case OP_[A-Z0-9_]+' | sed 's/case //' | sort -u) \
         <(awk 'NR>=1604 && NR<=1806' Op.java | grep -oE 'case OP_[A-Z0-9_]+' | sed 's/case //' | sort -u)
```

**Constructible but unnameable (16)** — `OP_IIP_AND`, `OP_IIP_MOD`, `OP_IIP_OR`, `OP_IIP_SHL`,
`OP_IIP_SHR`, `OP_IIP_USHR`, `OP_IIP_XOR`, `OP_PIP_AND`, `OP_PIP_DIV`, `OP_PIP_MOD`,
`OP_PIP_MUL`, `OP_PIP_OR`, `OP_PIP_SHL`, `OP_PIP_SHR`, `OP_PIP_USHR`, `OP_PIP_XOR`.

These are live opcodes. `OP_PIP_MUL = 0xC4` (`Op.java:2065`) is constructed at
`Op.java:1502` as `new PIP_Mul(in, aconst)`, and `javatools/src/main/java/org/xvm/asm/op/PIP_Mul.java`
exists. **`Op.toString()` (`Op.java:451-453`) is `return toName(getOpCode());`** — so calling
`toString()` on any of these 16 op types throws
`IllegalStateException("op=" + byteToHexString(nOp))` from `Op.java:1806-1807`.

Worse, the exception *masks* the error it was called to report. `OpInPlaceAssign.java:127`,
`:224`, `OpGeneral.java:291`, `:352`, `:360`, `OpIndex.java:291`, `:317`, `OpVar.java:259`,
`:264` all do `throw new UnsupportedOperationException(toName(getOpCode()))` — for these 16
opcodes the argument evaluation throws `IllegalStateException` first, and the real diagnostic
is lost. `BuildContext.java:605` builds a JIT error message the same way.

**Nameable but unconstructible (2)** — `OP_NEWC_T` (`Op.java:1925`, named at `:1793`) and
`OP_NEWCG_T` (`Op.java:1929`, named at `:1797`). No `NewC_T.java` / `NewCG_T.java` exists;
these are dead constants occupying opcode values.

**What a typed design would have done.** These are two hand-maintained `int` switches over the
same opcode space. A single table — `enum OpCode { NOP(0x00, "NOP", Nop::new), … }` — makes the
two views one declaration and the drift impossible.

**Confidence:** the static gap is certain (verified by `comm`, and by reading
`Op.toString()`). I did not execute an Ecstasy program that reaches these ops, so "this throws
in production" is an inference from the call graph, not an observation.

### 6.2 `Constant.Format.TimeZone` is produced by the compiler and rejected by the pool

`Constant.Format` has 107 values; `ConstantPool.disassemble` (`ConstantPool.java:2971-3300`)
handles 103. The four absent are `TimeZone`, `ResponseSender`, `DeferredValue`, `CastType`.
`DeferredValue` and `CastType` are compile-time-only and legitimately absent. `TimeZone` is not.

The full path is present in source:

1. `Lexer.java:1016-1023` — a `TimeZone`-prefixed literal lexes via `eatTimeZone(lInitPos)`.
2. `Lexer.java:1853` / `:1879` — which returns a `Token` with `Id.LIT_TIMEZONE`.
3. `Parser.java:3600` — `case LIT_TIMEZONE: return new LiteralExpression(current());`, i.e. a
   standalone timezone literal is a legal primary expression.
4. `LiteralExpression.java:364-365` —
   `return pool.ensureLiteralConstant(Format.TimeZone, (String) literal.getValue());`
5. `ConstantPool.java:697-764` — `ensureLiteralConstant(Format, String, Object)` lists
   `IntLiteral, FPLiteral, Date, TimeOfDay, Time, Duration, Path, RegEx` (`:699-706`) and
   **not `TimeZone`**, falling through to
   `default: throw new IllegalStateException("unsupported format: " + format);` (`:762-763`).

So compiling a standalone `TimeZone` literal appears to crash the compiler with an
`IllegalStateException` rather than producing a diagnostic — and even if it did construct one,
`disassemble` could not read it back. `Date`, `TimeOfDay`, `Time`, and `Duration` are all
handled in both places (`ConstantPool.java:701-704` and `:2977-2980`); `TimeZone` was
forgotten in both, in the same way.

**Confidence:** every link in the chain is verified by reading. I did not compile an `.x` file
containing a timezone literal, so the end-to-end failure is high-confidence inference. **A red
proof is one three-line test**, and it is the exact test §5.2 recommends building anyway:
enumerate all 107 formats and assert each is either constructible or explicitly declared not
to be.

### 6.3 `AstNode.fieldsForNames` — a dead validation guard and a wrong null check **[executed]**

Both in `AstNode.java:1963-1998`.

**(a) The type validation never fires.** `AstNode.java:1976`:

```java
if (!field.getType().isInstance(AstNode.class) && field.getType().isInstance(List.class)) {
    throw new IllegalStateException("unsupported field type " + ...);
}
```

`field.getType()` is a `Class<?>`; `AstNode.class` and `List.class` are `Class` *objects*. So
this asks "is the object `AstNode.class` an instance of the field's declared type?" — which is
true only for fields declared `Object`, `Class`, `Type`, `Serializable`, `AnnotatedElement`,
`GenericDeclaration`, or `TypeDescriptor`. The intent was plainly
`!AstNode.class.isAssignableFrom(t) && !List.class.isAssignableFrom(t)`.

Verified by execution over four representative field types:

```
exprField  type=Expression           guardFires=false
listField  type=List                 guardFires=false
objField   type=Object               guardFires=false
strField   type=String               guardFires=false
```

**The guard is unreachable for every field type**, including `String`, which is exactly what it
exists to reject. A wrongly-named child field of a non-node type reaches
`ChildIteratorImpl.next()` (`AstNode.java:2055-2061`), where
`return (AstNode) ((Iterator) value).next();` throws `ClassCastException` during traversal —
far from the declaration that caused it.

**(b) The not-found path checks the wrong variable.** `AstNode.java:1985-1991`:

```java
} catch (NoSuchFieldException e) {
    if (eOrig == null) { eOrig = e; }
    clzTry = clzTry.getSuperclass();
    if (clz == null) { throw new IllegalStateException(eOrig); }   // <-- clz, not clzTry
}
```

`clz` is the method parameter and is never reassigned, so this `throw` is dead. When a named
field is not found on the class or any superclass, the `while` exits with `clzTry == null`,
`fields[i]` stays **`null`**, and `fieldsForNames` returns an array with a null hole. That
surfaces later as `NPE` inside `prepareNextField` (`AstNode.java:2085-2088`), caught and
rethrown as `IllegalStateException("class=" + … + ", field=" + iField)` — **an index, not the
field name**.

Net effect: **renaming an AST child field is a silent break**, reported (if at all) as an
integer index during traversal instead of a `NoSuchFieldException` naming the field at class
initialization. That is precisely the cost of the reflection model in §5.9.

**Confidence:** (a) verified by execution. (b) verified by reading; whether any of the 65 call
sites currently passes a bad name is not something I checked, so this is a latent defect, not
an active one. Its significance is that it makes a *future* rename silently wrong.

### 6.4 `A_LABEL` and `CONSTANT_OFFSET` are the same integer

`Op.java:2218` defines `A_LABEL = -16`; `Op.java:2224` defines `CONSTANT_OFFSET = -16`.
Constant #0 therefore encodes to the same value as the compiler's label marker. The javadoc at
`Op.java:2213-2217` resolves the ambiguity by convention only. Separately,
`R_NEXT … R_RESET` (-1 … -10, `Op.java:2232-2281`) reuse the integers assigned to
`A_STACK … A_STRUCT` (-1 … -10, `Op.java:2139-2184`) with entirely different meanings.

No bug is demonstrated here — the conventions appear to hold — but this is the shape from
which the already-fixed `Argument.toIdString` bug came (the comment at `Argument.java:47-53`
describes an `AIOOBE` from indexing an unrelated constant array). Recording it because §5.5
recommends the two-`enum` split that removes it for free.

---

## 7. The performance question

Taken seriously, because the runtime dispatches through these paths.

### 7.1 Where a typed redesign is exactly free

**Covariant return narrowing** (§5.3 `ValueConstant<V>`, §5.11 `getComponent()`). **Verified by
`javap` [executed]:** the built `IntConstant.class` already contains

```
public org.xvm.util.PackedInteger getValue();
public java.lang.Object getValue();
    flags: (0x1041) ACC_PUBLIC, ACC_BRIDGE, ACC_SYNTHETIC
```

javac emits the identical bridge whether the supertype declares `Object getValue()` or
`V getValue()`. The bytecode after these changes is **byte-for-byte identical**. There is no
performance question to answer.

**Generic type parameters generally** (§5.6 `ClassTemplate<H>`). Erasure means the `checkcast`
that today sits in the method body moves to the bridge. Same instruction, same count, one
frame's difference that the JIT inlines. Free.

### 7.2 Where a typed redesign is a measurable win

**String dispatch removal** (§5.7). A Java `switch (String)` compiles to `String.hashCode()`
(cached, but still a field read and branch) + `String.equals()` (a real comparison) +
`tableswitch` + a second `tableswitch`. This runs on **every native method invocation** —
`CallChain.java:231-232` is the interpreter's native-call hot path. Replacing it with a
per-`CallChain` cached handler reference removes the string work entirely. **This is faster,
not merely safer.**

**Reflection removal** (§5.9). `Field.get()` per child, per node, per traversal, on every
compile. Direct field access is inlinable; `Field.get` is not (beyond the JDK's own
`MethodHandle` fast path, which `Field.get` does not take). **Faster.**

**Sealed hierarchies and devirtualization.** Worth stating precisely, because it is easy to
overclaim: HotSpot's C2 devirtualizes on *observed receiver profiles* (CHA plus type profiles),
not on `permits` clauses. A `sealed` declaration does not currently feed the JIT's inlining
decisions. What sealing *does* help is that exhaustive pattern switches over a sealed type
compile to a `typeSwitch` invokedynamic with a bootstrap that HotSpot handles well, versus a
chain of `instanceof` + `checkcast` — and it removes the unreachable `default` branch, which
shrinks the method and improves inlining eligibility. **Real but second-order.** I would not
justify any of these changes on JIT grounds.

### 7.3 Where a typed redesign genuinely costs

**Boxing an `int` register id into an `ArgId` record** (§5.5). `Frame.f_ahVar[m_nArgValue]`
becomes `f_ahVar[argId.index()]` through a sealed interface. If the `ArgId` is a field (created
once at deserialization), the cost is one pointer chase per argument read on the interpreter's
innermost loop, plus a megamorphic-ish interface call unless the profile is biased. If it is
created per read, it is an allocation that escape analysis may or may not eliminate.

**This is the one place in the study where I recommend against the typed design on performance
grounds.** §5.5's recommendation is the `enum` split (free) and not the record union (not free).

**Flat `ObjectHandle[]` register files and field arrays.** `Frame.f_ahVar`
(`Frame.java:88`) and `GenericHandle.m_aFields` (`ObjectHandle.java:454-457`) are flat arrays
indexed by `int`. `ObjectHandle[]` occurs 685 times tree-wide, 290 in `runtime/`. These are the
interpreter's object model. **Do not type them.** A per-slot sum type would add an indirection
to every register read and every field access.

### 7.4 The strategic point, restated

Per §0: `runtime/template/**` holds 46% of the repo's casts and none of the JIT's code path.
`asm/**` holds 30% and is used by both engines. **Effort spent typing `asm` compounds; effort
spent typing `runtime/template` does not.** Every ranking in §0 and §7 reflects that.

---

## 8. Staged landing order

Each stage is independently reviewable and independently revertible. The rationale column says
what it unlocks.

| Stage | Work | Effort | Unlocks |
|---|---|---|---|
| **0** | **Gates first.** (a) Format-totality test: enumerate all 107 `Constant.Format` and 16 `Component.Format` values, assert each is instantiable-or-declared-uninstantiable. (b) Opcode-consistency test: assert `Op.instantiate` and `Op.toName` cover the same set. (c) Differential constant-folding harness over all `(Format, Token.Id, Format)` triples. | Small, additive | **Everything.** (a) and (b) turn §6.1 and §6.2 from assertions into red tests today, with no refactor. (c) makes stage 3 a proof rather than a hope. Matches E12's philosophy exactly. |
| **1** | `Token` typed accessors (§5.8). `TargetInfo` package move + `sealed interface Argument` (§5.4). Raw `Class` → `Class<?>` in JIT (§5.12). | Small, mechanical | Establishes the typed-accessor idiom. Removes `NameExpression.getMeaning()`'s `default` arm. Zero risk, immediate reviewer confidence. |
| **2** | `ValueConstant<V>` + `MatchAnyConstant` leaves the hierarchy (§5.3). Covariant `getComponent()` narrowing on the identity constants (§5.11). | Small-medium, one atomic commit each | Removes the largest block of casts per line changed. Both are bytecode-identical. `getComponent()` narrowing then lets stage 5's cast deletions be purely mechanical. |
| **3** | The `apply()` string-concatenation retirement (§5.1), one file at a time, smallest first: `DecimalAutoConstant` → `ByteConstant` → `DecimalConstant` → `IntConstant` → `LiteralConstant`. | Medium-high | Deletes ~1,350 lines and 1,279 string labels. **Requires stage 0(c).** |
| **4** | `Format`-as-data: `Component.Format` first (16 constants), then `Constant.Format` (107). Fix `TimeZone` (§6.2) and the opcode drift (§6.1) as part of this. | Medium | Makes "add a format" a compile error. **Requires stage 0(a)/(b).** |
| **5** | The `Nid` sealed union (§5.10), with the `Object`→`Nid` map flips one map at a time. | Wide, javac-guided | Closes the second half of bug-list row 11. **Best done after stage 2**, because narrowed `getComponent()` removes noise from the same call sites. Gate: `TypeComparisonCorpusTest`. |
| **6** | `AstNode` reflection removal (§5.9), one node per commit. | Wide, mechanical | Faster compiles, safe renames, and it is a **prerequisite** for E2's backlogged parser-AST sealing. |
| **7** | `ClassTemplate<H extends ObjectHandle>` (§5.6): parameterize-with-`ObjectHandle` in one commit, then narrow one template per commit. | Wide, mechanical | 561 casts → 5 bridges. **Deliberately last**, per §7.4 — interpreter-only. |
| **not scheduled** | Native string-dispatch rewrite (§5.7); `Op` `ArgId` records (§5.5); `Frame`/`GenericHandle` array typing (§7.3) | — | Recommended against, for the reasons in each section. |

**Interaction with E2/E11.** Stages 1, 2, 4, 5 are `asm`-side and should be sequenced *after*
the E2/E11 master port, not before — they build on sealed hierarchies that master does not yet
have. Stages 0, 3, 6 are independent of the port and could land on master first. Stage 7 is
independent of everything.

---

## Appendix A: commands

All run from the repository root against `SRC=javatools/src/main/java/org/xvm` on
branch `lagergren/lazy-instance`, 2026-08-28.

```bash
SRC=javatools/src/main/java/org/xvm

# A.1  methods returning Object                                             -> 68
rg -n --pcre2 '^\s*(?:@\w+\s+)*(?:public|protected|private|static|final|abstract|synchronized|default|native|\s)*\bObject\b(?!\s*\.)\s+\w+\s*\(' $SRC --glob '*.java' | wc -l

# A.2  parameters typed Object                                              -> 167
rg -n --pcre2 '\(\s*(?:final\s+)?Object\s+\w+|,\s*(?:final\s+)?Object\s+\w+' $SRC --glob '*.java' | wc -l

# A.3  fields typed Object                                                  -> 110
rg -n --pcre2 '^\s*(?:public|protected|private|static|final|transient|volatile|\s)*Object\s+\w+\s*(?:=|;)' $SRC --glob '*.java' | wc -l

# A.4  Object[]                                                             -> 47
rg -n 'Object\[\]' $SRC --glob '*.java' | wc -l

# A.5  explicit downcasts to a named type                                   -> 2965
rg -n --pcre2 '\([A-Z][A-Za-z0-9_$.]*(?:<[^\n()]*>)?\)\s*[A-Za-z_$(]' $SRC --glob '*.java' | wc -l

# A.6  instanceof                                                           -> 1576
rg -n '\binstanceof\b' $SRC --glob '*.java' | wc -l

# A.7  raw generic declarations                                             -> 25 (10 raw Class)
rg -n --pcre2 '\b(?:List|Map|Set|Iterator|Comparable|Class|Collection)\s+[a-z_]\w*\s*(?:=|;|\)|,)' $SRC --glob '*.java' | wc -l
rg -n --pcre2 '(?<![.\w])Class\s+[a-z]\w*\s*[=;,)]' $SRC --glob '*.java' | wc -l

# A.8  unchecked/rawtypes suppressions                                      -> 13
rg -n 'SuppressWarnings\("unchecked"\)|SuppressWarnings\(\{?"?rawtypes' $SRC --glob '*.java' | wc -l

# A.9  throwing switch defaults                                             -> 149 arrow, 528 colon
rg -c 'default\s*->\s*throw' $SRC --glob '*.java' | awk -F: '{s+=$2} END{print s}'
rg -c 'default:\s*$|default:\s*throw' $SRC --glob '*.java' | awk -F: '{s+=$2} END{print s}'

# A.10 hand-written "impossible" throws                                     -> 860 / 265
rg -c 'throw new IllegalStateException'       $SRC --glob '*.java' | awk -F: '{s+=$2} END{print s}'
rg -c 'throw new UnsupportedOperationException' $SRC --glob '*.java' | awk -F: '{s+=$2} END{print s}'

# A.11 string case labels                                                   -> 2737
rg -c 'case\s+"' $SRC --glob '*.java' | awk -F: '{s+=$2} END{print s}'

# A.12 per-package rollups (casts / instanceof / string cases)
rg -c <PATTERN> $SRC --glob '*.java' | awk -F: '{n=$2;p=$1;
  sub("javatools/src/main/java/org/xvm/","",p); split(p,a,"/");
  k=(a[2]~/\.java$/)?a[1]:a[1]"/"a[2]; t[k]+=n}
  END{for(k in t) printf "%-24s %5d\n",k,t[k]}' | sort -k2 -rn

# A.13 instanceof by family                                                 -> 570 / 243 / 170 / 126
for p in 'instanceof [A-Za-z0-9_.]*Constant\b' \
         'instanceof [A-Za-z0-9_.]*(Expression|Statement)\b' \
         'instanceof [A-Za-z0-9_.]*Handle\b' \
         'instanceof (ClassStructure|MethodStructure|PropertyStructure|MultiMethodStructure|TypedefStructure|PackageStructure|ModuleStructure|FileStructure|Component)\b'; do
  rg -c --pcre2 "$p" $SRC --glob '*.java' | awk -F: '{s+=$2} END{print s}'
done

# A.14 handle downcasts                                                     -> 1436
grep -rEon '\((ObjectHandle|[A-Z][A-Za-z0-9_]*Handle|JavaLong|GenericHandle)\)\s*[a-zA-Z_(]' \
  --include='*.java' $SRC | wc -l

# A.15 positional argument casts                                            -> 170
grep -rEon '\([A-Za-z0-9_.]*(Handle|JavaLong)\)\s*ahArg\[[0-9]+\]' --include='*.java' $SRC | wc -l

# A.16 Map<Object, ...> declarations                                        -> 79
rg -n 'Map<Object,' $SRC --glob '*.java' | wc -l

# A.17 nested-identity call sites                                           -> 64
rg -n 'getNestedIdentity\(|resolveNestedIdentity\(' $SRC --glob '*.java' | wc -l

# A.18 native string dispatch                                               -> 132 switches, 84 files
rg -c --pcre2 'switch\s*\(\s*(method\.getName\(\)|sMethod|sName|idProp\.getName\(\))\s*\)' \
  $SRC/runtime --glob '*.java' | awk -F: '{s+=$2} END{print s}'

# A.19 AST reflective child declarations                                    -> 65 sites, 122 refs
rg -c 'fieldsForNames\(' $SRC --glob '*.java' | awk -F: '{s+=$2} END{print s}'
rg -c 'CHILD_FIELDS'     $SRC/compiler/ast --glob '*.java' | awk -F: '{s+=$2} END{print s}'

# A.20 the getValue() erased-receiver census                                -> 6, all in CaseManager
rg -n 'ValueConstant\)\s*\w+\)\.getValue\(\)|val(ue)?(Lo|Hi)\.getValue' $SRC --glob '*.java'

# A.21 opcode-switch drift (executed; see 6.1)
F=$SRC/asm/Op.java
comm -23 <(awk 'NR>=1353 && NR<=1600' $F | grep -oE 'case OP_[A-Z0-9_]+' | sed 's/case //' | sort -u) \
         <(awk 'NR>=1604 && NR<=1806' $F | grep -oE 'case OP_[A-Z0-9_]+' | sed 's/case //' | sort -u)

# A.22 bridge-method verification (executed; see 5.3 / 7.1)
javap -p -v javatools/build/classes/java/main/org/xvm/asm/constants/IntConstant.class \
  | grep -A3 'public java.lang.Object getValue'

# A.23 apply() string-concatenation dispatch sites                          -> 5
rg -n 'getFormat\(\).name\(\)\s*\+' $SRC --glob '*.java'

# A.24 decomposition of IntConstant.apply labels             -> 550 total, 277 cross-format
rg -o 'case "([A-Za-z0-9]+)([^A-Za-z0-9"]+)([A-Za-z0-9]*)":' -r '$1|$2|$3' \
   $SRC/asm/constants/IntConstant.java | awk -F'|' '$3!="" && $1!=$3' | wc -l
# ...and the single shared body those 277 labels fall through to:
sed -n '580p' $SRC/asm/constants/IntConstant.java
```

### Cross-references

- [`plans/master-enhancement-submissions.md`](plans/master-enhancement-submissions.md) — E2 (`:129`), E11 (`:609`), E12 (`:637`)
- [`plans/master-issue-submissions.md`](plans/master-issue-submissions.md) — bug row 11 (`:908`), row 19 (`:1430`)
- [`sealed-hierarchy-audit.md`](sealed-hierarchy-audit.md) — hierarchy verdicts (`:249`), discriminator-switch census (`:189`), `Op` verdict (`:1039`), cascade wave (`:1071`)
- [`generics-api-audit.md`](generics-api-audit.md) — `ValueConstant` (`:757`), native args (`:825`), tokens (`:705`), nested identities (`:951`), op-info cache (`:230`), JIT raw `Class` (`:550`), pool locator tables (`:368`)
- [`nullness-annotation-audit.md`](nullness-annotation-audit.md) — "null means too many things" (`:50`)
- [`jit-implications.md`](jit-implications.md) — JIT/interpreter class-usage split (`:17`)
- [`jit-runtime-report.md`](jit-runtime-report.md) — replacement-intent evidence (`:77`)
