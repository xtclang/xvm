# Bugs that fell out of letting the compiler demand completeness

Every defect below was found the same way: **the compiler was given enough type information to
decide whether a set of cases was complete, and it immediately named cases that were not.** None was
found by reading code, and none by testing. They appeared as compile errors on the first build after
the change.

Two mechanisms did this, and they are worth separating because they port to master differently.

| mechanism | what it does | branch state |
| --- | --- | --- |
| **`sealed` + exhaustive switch** | closes a class hierarchy so a `switch` over it must handle every subtype | **50 hierarchies sealed on this branch; master has 0** |
| **`abstract` on a base** | removes a default implementation so every subclass must supply its own | applied to `xRTDelegate`'s storage protocol |

Both replace a silent fallback - a `default:` branch, or an inherited implementation written for a
different subclass - with a compile error. The bugs are the ones that fallback had been hiding.

---

# Part 1 - what sealing surfaced

Master models the constant hierarchy, the AST trees, `Component`, the composition subtree,
`MethodBody`'s target payload and `RegisterInfo` as **open** hierarchies, dispatched over with
`instanceof` chains and `switch` statements carrying a silent `default`. This branch made those
`sealed ... permits` and converted the dispatch sites to exhaustive pattern switches.

The headline trees were the ConstantPool's constants (`TypeConstant` and its ~20 subtypes,
`IdentityConstant`/`ValueConstant`, the condition/pseudo/frame-dependent families) and the AST
nodes (`BinaryAST` plus the parser AST). See E2 in the enhancement list for the full port spec.

### Bug 19 - implicit-identity cache written from concurrent service threads

**Filed. Category A.** `ConstantPool.f_implicits` is a plain `HashMap`, verbatim on master, written
from concurrent service threads: all `ServiceContext`s of one container share one pool over the
shared multi-thread executor. Reachable in **any** program whose services resolve implicit names -
no parallel containers required, the same exposure bucket as the timer-callback rows.

### Bug 20 - delegation synthesis publishes half-built method code

**Filed. Category A.** Runtime-lazy delegation synthesis publishes method code *before* assembling
it (`publish` at master `:2958`/`:3009`, assembly at `:2980`/`:3135`). The optimized-chain build is
lazy and per-TypeInfo-view, so two services dispatching a delegating class's member race the same
host `ClassStructure`. Single container, ordinary scheduling.

### Bug 11 (part) - `MethodBody`'s target payload had no coherent hash/equality contract

**Filed. Category A.** The target payload was untyped, and had no consistent `equals`/`hashCode`
until it was modelled as a sealed union (`MethodBody.Target`, with `Narrowing permits BySignature,
ByNestedId`). Java collections assume those describe the value; they did not.

### Silent-`default` dispatch sites that never handled several real subtypes

**Fixed in place by the conversion**, not filed separately - they were incompleteness rather than
misbehaviour, and each was corrected as the build named it. The conversion waves are recorded as
"retire the remaining silent-default identity dispatch sites" and "close the last three non-sealed
hatches".

**Why these were invisible before.** In an `instanceof`/`default` style, *a missing case is
indistinguishable from a deliberate fall-through*. The compiler cannot tell them apart, so neither
can a reviewer. Sealing removes the ambiguity: every gap becomes an error, and each one is then
triaged as a potential bug rather than silenced.

---

# Part 2 - what `abstract` surfaced

The same principle without any language feature newer than 1.0: `xRTDelegate` supplied storage
implementations that were only correct for the object-array case. Declaring them `abstract` made
`javac` name every delegate that had been inheriting them.

### Bug 36 - `deleteAll(range)` on byte-backed and String arrays

**Filed, fixed, PR #563 (merged).** Making `deleteRangeImpl` abstract failed the build:

```
xRTStringDelegate.java: error: xRTStringDelegate is not abstract and does not override
  abstract method deleteRangeImpl(DelegateHandle,long,long) in xRTDelegate
```

```
Int8  [1,2,3,4,5].deleteAll(1..2)  ->  [1, 3, 4]   expected [1, 4, 5]
UInt8 [1,2,3,4,5].deleteAll(1..2)  ->  0x010304    expected 0x010405
String[...].deleteAll(1..2)        ->  ClassCastException
```

Two defects: `ByteBasedDelegate` copied from `nIndex + 1` where the generic implementation uses
`nIndex + nDelete` (correct one line above, in `deleteElementImpl`), and `xRTStringDelegate` had no
implementation at all. Only a MULTI-element range reaches either, which is why it survived.

### Bug 37 - comparing two slices or two views by reference crashes

**Filed, fixed, PR #564 (approved).** The same change named four classes at once:

```
xRTSlicingDelegate.java:23: error: xRTSlicingDelegate is not abstract and does not override
  abstract method compareIdentity(ObjectHandle,ObjectHandle) in xRTDelegate
xRTViewToBit.java:21 / xRTViewFromByte.java:18 / xRTViewFromBit.java:16 - same
```

```
Int[] a = [1,2,3,4,5];
&a[1..3] == &a[1..3]                 // ClassCastException
&b.asBitArray() == &b.asBitArray()   // same, ByteBasedBitView$ViewHandle
```

Reproduced on clean master `145f12f51`.

### Bug 38 - the same crash in the other direction, in seven more templates

**Filed, fixed, folded into PR #564.** Found by *asking* after bug 37, not by the compiler - which
is the point. `xRef.CompareReferents` resolves the template from the first handle and hands both to
it; the second is arbitrary, and seven templates cast it. Verified individually against clean
master, one module per element type:

```
Char  String  Float64  Int8  Int  Int128  Object      all: master=CRASH  fixed=ok
```

### Not filed - `Expression.testFitAsType`

**Latent hazard, recorded in E32.** `StageMgr` requiring a non-null `ErrorListener` failed an xdk
build; `testFitAsType` passed its caller's listener into a *speculative* staging step, where the
adjacent `validateAsType` correctly passes `BLACKHOLE`. Behaviour therefore depended on the caller:
null meant silence, a real listener meant fit-test staging failures surfaced as compile errors. No
repro produced for the second branch, so it is recorded rather than filed.

---

# What the pattern is worth

Six filed defects and one latent hazard, across two mechanisms, in subsystems that had been working
for years. They share a shape: **a fallback that made "nobody handled this" indistinguishable from
"this was handled deliberately."** A `default:` branch. An inherited implementation written for a
sibling.

The cost asymmetry is the argument. Bugs 19, 20 and 36 were named by the compiler with a file and a
line. Bug 38 had to be found by hand - notice a base method casting to one subclass's handle,
enumerate the delegates that are not that subclass, work out which of five affected methods are
reachable from Ecstasy at all (four are not), then build a repro for the one that is. Same class of
defect; one found in seconds, one in an afternoon, and only the first came with any confidence the
sweep was exhaustive.

## Filing notes

- **19, 20, 11** are already on the master bug list from the sealing work. They are Category A -
  reachable in ordinary single-container programs, not exotic concurrency.
- **36, 37, 38** are filed and fixed; #563 merged, #564 approved.
- **The `testFitAsType` inconsistency** is worth an issue even as a latent hazard: the fix is one
  line and is already written on the adjacent method.
- **The technique** ports independently of the fixes. E2 carries the sealing port spec; master's
  Java level already supports `sealed` and pattern switch, so no toolchain change is required.
  Expect the port's yield to *be* the compile errors, each triaged as a potential bug rather than
  silenced with a `default`.
