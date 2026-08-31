# Bugs that fell out of letting the compiler demand completeness

Every defect below was found the same way: **a base class stopped supplying a default
implementation, and `javac` was made to name every subclass that had been relying on it.** None was
found by reading code, and none by testing. The compiler named the file and line on the first build
after the change.

## A note on the technique, because the name matters

This was **not** Java `sealed`/`permits`. The mechanism was ordinary `abstract`: remove the wrong
default from the base, declare the method abstract, and let the compiler enumerate the subclasses
that never implemented it. The principle is the one sealing serves - the compiler, not a human,
decides whether the set of cases is complete - but the change is a keyword every Java developer has
used since 1.0.

That distinction is worth keeping when these are filed. The findings do not depend on adopting a
newer language feature, and nothing here requires sealing anything.

---

## Master bug 36 - `deleteAll(range)` on byte-backed and String arrays

**Status:** filed, fixed, PR #563 (merged).

**How it surfaced.** `xRTDelegate.deleteRangeImpl` was made abstract as part of separating the
object-array implementation from the shared base. The build immediately failed with:

```
xRTStringDelegate.java: error: xRTStringDelegate is not abstract and does not override
  abstract method deleteRangeImpl(DelegateHandle,long,long) in xRTDelegate
```

`xRTStringDelegate` had never implemented it and had been silently inheriting the *object-array*
implementation, which casts the handle to `GenericArrayDelegate`.

**What it actually did.**

```
Int8  [1,2,3,4,5].deleteAll(1..2)  ->  [1, 3, 4]   expected [1, 4, 5]
UInt8 [1,2,3,4,5].deleteAll(1..2)  ->  0x010304    expected 0x010405
String[...].deleteAll(1..2)        ->  ClassCastException
```

Two distinct defects: `ByteBasedDelegate` copied from `nIndex + 1` where the generic implementation
uses `nIndex + nDelete` (correct one line above, in `deleteElementImpl`), and `xRTStringDelegate`
had no implementation at all. Only a MULTI-element range reaches either; a single-element delete
takes a different branch, which is why it survived.

---

## Master bug 37 - comparing two slices or two views by reference crashes

**Status:** filed, fixed, PR #564 (approved, mergeable).

**How it surfaced.** Continuing the same split, the remaining storage methods were made abstract.
The compiler then named four classes at once:

```
xRTSlicingDelegate.java:23: error: xRTSlicingDelegate is not abstract and does not override
  abstract method compareIdentity(ObjectHandle,ObjectHandle) in xRTDelegate
xRTViewToBit.java:21:     error: xRTViewToBit ...
xRTViewFromByte.java:18:  error: xRTViewFromByte ...
xRTViewFromBit.java:16:   error: xRTViewFromBit ...
```

**What it actually did.**

```
Int[] a  = [1,2,3,4,5];
Int[] s1 = a[1..3];
Int[] s2 = a[1..3];

s1 == s2      // True - fine
&s1 == &s2    // Unhandled exception: java.lang.ClassCastException

UInt8[] b = [1,2,3,4];
&b.asBitArray() == &b.asBitArray()   // same crash, ByteBasedBitView$ViewHandle
```

A slice holds `f_hSource`/`f_ofStart`; a view holds its source. Neither is a
`GenericArrayDelegate`, and neither overrode `compareIdentity`, so both inherited a cast that
cannot succeed. Reproduced on clean master `145f12f51`.

---

## Master bug 38 - the same crash in the other direction, in seven more templates

**Status:** filed, fixed, folded into PR #564.

**How it surfaced.** Not by the compiler - by asking, after bug 37, whether the *reverse* direction
was also broken. It was, in different classes.

```
Char[] a = ['a','b','c','d','e'];
Char[] s = a[1..3];

&s == &a    // bug 37 - xRTSlicingDelegate
&a == &s    // this   - xRTCharDelegate
```

`xRef.CompareReferents` resolves the template from the **first** handle and hands both to it.
Handle 1 is safe by construction: it chose the template. Handle 2 is arbitrary. Seven templates cast
both - `xRTCharDelegate`, `xRTStringDelegate`, `xRTFloat64Delegate`, `ByteBasedDelegate`,
`LongBasedDelegate`, `LongLongDelegate`, `xRTDelegate` - so any pair of same-element-type arrays
with different storage crashed in whichever direction put the concrete delegate first.

Each was verified individually against clean master, one module per element type so the first
failure could not mask the rest:

```
Char  String  Float64  Int8  Int  Int128  Object      all: master=CRASH  fixed=ok
```

---

## Not a bug, but found the same way - `Expression.testFitAsType`

**Status:** latent hazard, recorded in E32, deliberately NOT filed as a bug.

**How it surfaced.** `StageMgr`'s constructors stopped accepting a null `ErrorListener`. An ordinary
xdk build then failed:

```
java.lang.NullPointerException: errs
    at StageMgr.<init>(StageMgr.java:45)
    at Expression.testFitAsType(Expression.java:475)
    at RelOpExpression.testFit(RelOpExpression.java:304)
```

`testFitAsType` passed its caller's listener into a **speculative** staging step. The correct
behaviour was already written on the very next method: `validateAsType` stages the same expression
with an explicit `BLACKHOLE`, because staging something you are only speculating about must not
report errors.

So the method's behaviour depended on its caller: a null listener meant silence, a real one meant
staging failures from a *fit test* surfaced as real compile errors. **Classified as latent, not a
bug**, because reaching the second branch needs a non-null listener, a `typeRequired.isTypeOfType()`
and a type expression that fails staging, and no repro has been produced. It is recorded rather than
filed until one is.

---

## What the pattern says

Three shipped bugs and one latent hazard, from two changes, in a subsystem that had been working for
years. They share a shape:

**A base class supplied an implementation that was only correct for one of its subclasses.** Every
other subclass inherited it silently. "This class never implemented this method" is exactly the
question a compiler answers for free, for every subclass at once, on every build - and it was being
answered instead by a `ClassCastException` in a user's program, years later, and only for the
handful of delegates that happen to store something different.

The cost asymmetry is the argument. Bug 36 was named by the compiler with a file and line. Bug 38
had to be found by hand: notice a base method casting to one subclass's handle, enumerate the
delegates that are not that subclass, work out which of five affected methods are reachable from
Ecstasy at all (four are not), then build a repro for the one that is. Same class of defect, one
found in seconds and one in an afternoon, and only the first comes with any confidence that the
sweep was exhaustive.

None of this needs sealing, pattern matching, or any language feature newer than `abstract`. It
needs a base class not to guess on behalf of its subclasses.

## Suggested filing order

1. **Bug 38** - already fixed in PR #564 alongside 37; nothing further needed.
2. **The `testFitAsType` inconsistency** - worth an issue even as a latent hazard, since the fix is
   one line and is already written on the adjacent method.
3. **The technique itself** - the remaining `xRTDelegate` storage methods are now abstract on the
   working branch. Anyone repeating this on another base class in this tree should expect the same
   yield.
