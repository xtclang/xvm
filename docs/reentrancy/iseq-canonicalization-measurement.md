# Does canonicalizing the op's type collapse the `ensureClass` case?

Companion to [`iseq-identity-hit-rate.md`](iseq-identity-hit-rate.md), which measured the
`TypeConstant.callEquals` selection split and left one question open. **E49** said not to build
either design until this number existed. It now does.

## The question

`callEquals` selects a composition three ways:

```java
TypeComposition clz = clz1.getType().equals(this) ? clz1
                    : clz2.getType().equals(this) ? clz2
                    : ensureClass(frame);
```

The prior pass found the third branch dominant and the identity check effectively dead (0.20%),
because an op's constants come from the module it was compiled into while a composition's come from
the executing container's pool - always equal, never identical. The proposal was to **canonicalize
the op's type into the container's pool once at resolution**, so the existing identity check fires:
no cache, no composite key, no override hazard, no new state on a shared op.

That certainly removes the equal-but-not-identical case. The open question was whether it also
removes the both-miss case. Split it:

- **c1** - the comparison type is genuinely neither operand's type. Canonicalization cannot help.
- **c2** - a mismatch canonicalization would erase.

## Result: c2 is zero

Counters in `callEquals`, driven by a small module doing string, integer, enum and object-typed
comparisons in a loop (7,811 selections):

| | count | share |
| --- | ---: | ---: |
| (d) same handle, exits before selection | 234 | 3.00% |
| (a) identity hit | 23 | 0.29% |
| (b) equal but not identical | 7,046 | 90.21% |
| (c) both miss, `ensureClass` runs | 508 | 6.50% |
| **of (c): would a canonicalized compare have matched?** | **0** | **0.00%** |

**Not one of the 508 misses was a pool artifact.** The exemplars show why, and the mechanism is
plain once seen - the comparison type is a SUPERTYPE or an ENUM TYPE while the operands are
subtypes or enum values:

| n | comparison type | operand 1 | operand 2 |
| ---: | --- | --- | --- |
| 266 | `Object` | `Int` | `String` |
| 91 | `Array.Mutability` | `Array.Mutability.Constant` | `Array.Mutability.Fixed` |
| 42 | `Array.Mutability` | `Array.Mutability.Mutable` | `Array.Mutability.Constant` |
| 12 | `Var<Array.ArrayDelegate<...>>` | `Var<_native:...RTDelegate...>` | `Var<_native:...RTDelegate...>` |
| 7 | `Signum` | `Signum.Positive` | `Signum.Zero` |

Comparing an `Int` against a `String` through `Object`, or two enum values through their enum type,
is not a pool mismatch and no amount of canonicalizing fixes it. `TypeConstant.equals` already
compares structurally through `compareDetails` rather than by pool identity, so a cross-pool but
structurally equal type lands in (b), never in (c) - which is exactly what the zero confirms.

The identity-hit rate corroborates the prior pass independently: **0.29% here against 0.20% there**,
on a completely different workload.

## Verdict: build the cache, not the canonicalization

- **Canonicalization fixes (b) only.** It cannot touch (c), measured at 0% recoverable.
- **A per-op-site cache fixes both**, because it memoizes the SELECTION OUTCOME and skips the
  comparison chain entirely - a hit costs nothing whichever branch would have been taken.

So the cache is strictly more general, and canonicalization is a partial optimization that could be
layered on later for (b) if it proves worth it on its own. E49's design constraints stand unchanged:
the key must carry the frame-resolved type and the container (a composition-pair key returned a
stale composition 0.045% of the time), and override receivers still need a guard.

## What this does NOT establish

**The (b)/(c) ratio is workload-dependent, and mine is not representative.** This run reads 90.21%
(b) / 6.50% (c); the prior pass read 9.71% / 89.77% - inverted. A synthetic loop over strings,
enums and boxed objects is not the manualTests sweep, and the absolute shares should be taken from
the prior document, not this one.

That does not weaken the verdict, because the verdict rests on a **structural** finding rather than
on a ratio: the (c) population is "comparison through a supertype or enum type", which is a property
of how the code was written, not of how often it runs. c2 = 0 in a workload where (c) is 6.5%, and
the exemplars explain the mechanism; there is no reason a different mix would manufacture pool
artifacts that structural equality already absorbs.

**Confidence:** high on c2 = 0 and on the mechanism, which is visible in the exemplars and follows
from `equals` being structural. Low on the (b)/(c) ratio from this workload. Not measured: whether
`ensureClass` returns a stable composition per site for the (c) subset - the prior pass put a
one-entry cache at 99.87% overall, and that number was not re-derived here.

**Method:** temporary `LongAdder` counters in `callEquals` plus an exemplar histogram, one module
compiled and run through `XtcEngine`, counters dumped at teardown. All instrumentation reverted;
this document is the only artifact.
