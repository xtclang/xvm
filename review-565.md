# Review: PR #565 — "add support for immutable xvm structures"

`+1029 / −84` across 21 files, by cpurdy. Adds a read-only mode to `XvmStructure` and its
subclasses, plus `XvmStructureTest` (344 lines).

This is a review in the light of what this branch's audit has repeatedly found painful in this
codebase: mutable fields with no publication guarantee, `null` used as a sentinel, identity
relationships re-established by copying, and invariants that exist only because every call site
remembered to check them.

---

## 1. What it does

- `XvmStructure` gains `private boolean m_fReadOnly`, `isReadOnly()`, `verifyMutable()`,
  `markReadOnly()`, `ensureReadOnly()`, `ensureMutable()`, and an abstract `findThisIn(FileStructure)`.
- ~30 mutators across `Component`, `MethodStructure`, `PropertyStructure`, `ClassStructure`,
  `ModuleStructure`, `Parameter` and others call `verifyMutable()` first.
- Freezing cascades: `markReadOnly()` walks `getContained()`, and for `Component`s also walks the
  sibling chain.
- `ensureMutable()` on a frozen structure copies the **entire containing `FileStructure`** and
  returns the counterpart via `findThisIn`.
- Collections held by `Contribution` are copied on freeze and wrapped `unmodifiable` on read.

## 2. What is right about it

Worth saying plainly, because the rest of this document is critical.

- **`verifyMutable()` is called as a plain statement, not inside `assert`.** I checked all ~30 call
  sites. Enforcement therefore survives `-da`, which is not something this codebase can be assumed
  to get right — `Contribution.getTypeParams()` a few lines away still does
  `assert (map = Collections.unmodifiableMap(map)) != null;`, an assignment inside an assert that
  silently stops protecting anything when assertions are off.
- **Copy-on-freeze neutralises escaped references.** `Contribution.markReadOnly()` replaces
  `m_listInject` and `m_mapParams` with *copies*. So a caller who obtained the raw list while the
  structure was mutable ends up holding the old object, which the frozen structure no longer uses.
  That is a genuinely thoughtful detail and it is not obvious.
- The cascade correctly handles `Component` sibling chains, which a naive `getContained()` walk
  would miss.
- It ships a test.

## 3. The blocking problem: this is not thread-safe, and the stated purpose is sharing

The PR body says this is for "bundles, efficient caching, etc." Caching means one structure reached
by more than one thread. The implementation has no memory-model story at all:

```java
private boolean m_fReadOnly;          // not volatile, not final
```

In a 2422-line diff, `volatile`, `synchronized`, `Thread`, `concurrent` and `happens-before` occur
**twice in total**.

Two distinct failures follow.

**(a) The flag itself is not safely published.** Thread A calls `markReadOnly()`; thread B calls
`isReadOnly()` and may legally observe `false` indefinitely. It then calls a mutator, `verifyMutable()`
passes, and it writes to a structure another thread believes is frozen. The guard is exactly as
strong as an unsynchronised boolean, which is to say not at all.

**(b) Worse: the *contents* are not safely published either.** Even if B sees `m_fReadOnly == true`,
nothing establishes a happens-before edge between A's writes to the structure's fields and B's reads
of them. B can see a frozen structure containing stale or torn field values. Freezing an object does
not publish it; only a final field, a volatile write, or a lock does.

This is the same defect class as `ConstantPool.f_implicits` (PR #566) and the callback registry
(PR #571) — an unsynchronised field on a path the runtime genuinely reaches from several threads.
The difference is that here it is the *foundation* of the feature rather than an oversight in one
map.

### 3a. Steelmanning the plain `boolean` — when it would be fine

The field on its own is not automatically wrong, and it is worth being precise about when it is not,
because that determines the right fix.

**Java's safe-publication rule does the work here.** If an object is published through a `final`
field, a `volatile` write, a lock, or a concurrent collection, then *everything reachable from it at
that moment* is safely published too. So if the lifecycle is:

> build on one thread → freeze → **then** publish

then `m_fReadOnly` does not need to be `volatile` at all. The publish supplies the happens-before
edge, and every reader sees both the flag and the contents. That is a completely respectable design,
it is cheaper than `volatile`, and if that is the author's model then this field is fine.

**The problem is that nothing states or enforces that ordering, and the API invites the opposite.**

- `ensureReadOnly()` is an operation on a *live* structure, callable at any time by anyone holding a
  reference. Nothing prevents publish-then-freeze, which is the unsafe order.
- `markReadOnly()` is itself a mutation. "Make this immutable" is a write, and it needs the same
  publication guarantees as any other write — but it is the one write the design treats as special.
- `ensureMutable()` and `clone()` mint a **new mutable structure from a frozen one at an arbitrary
  later time**, which is precisely a publish-after-the-fact path.

So the sharpest form of the criticism is not "the field should be volatile". It is:

> **The design has no stated rule about when freezing may happen relative to sharing, and the API
> makes the unsafe order as easy to write as the safe one.**

That is answerable in three ways, and the author gets to choose:

1. **State the contract and enforce it.** "A structure must be frozen before it is shared; freezing a
   shared structure is a programming error." Then the plain boolean is correct — but something has to
   check it, because a rule nobody enforces is a comment.
2. **Make `volatile` the belt to that braces.** One word, no measurable cost on a field read this
   cold, removes the whole question. Cheap enough that arguing about it costs more than doing it.
3. **Freeze by construction** — `toImmutable()` returns a new instance with `final` fields. Then the
   ordering cannot be got wrong, because a frozen structure never existed in a mutable state under
   that identity.

**Which of these applies is a question for the author, not an assumption for a reviewer**, and it is
the question this review would most like answered: *what is the intended lifecycle — is a structure
ever frozen after it has been handed to another thread?* The PR body cites "bundles, efficient
caching", and both of those are scenarios where a structure outlives the thread that built it, which
is what makes the question pressing rather than academic.

**What would fix it.** If the answer is "yes, or I don't know": freeze must be a publication event,
not a flag flip. The options, in
increasing order of honesty:

1. `volatile boolean m_fReadOnly` — cheap, fixes (a), and gives (b) for everything written *before*
   the volatile write. This is the minimum and should be considered mandatory.
2. Freeze once, at construction of an immutable *copy*, so the frozen instance's fields are `final`
   and safe publication is automatic. This is what `Container.f_errs` and `ErrorList` ended up doing
   on this branch and it is strictly better, because it cannot be got wrong later.
3. A separate immutable type (see §6).

### 3b. Why the ordering question is not hypothetical for these exact classes

Setting intent aside — which a reviewer should not guess at — there is an objective fact that makes
the question pressing rather than academic.

**`MethodStructure` is already known to be written by one thread and read by others.** Open PR #549
exists specifically to make `MethodStructure.m_fNative` and `m_code` `volatile`, on the grounds that
they are "written by one thread and read by others, with no happens-before edge between them", and
that a racing reader can pair a stale flag with missing state and fail hard in `getOps()`.

`MethodStructure` is one of the classes this PR adds `m_fReadOnly` to (133 lines changed). So the PR
adds a **non-volatile** flag governing mutability to a class we are simultaneously arguing needs
`volatile` on its other flags, for the same reason. Whatever the intended lifecycle, that is
internally inconsistent, and the two PRs should agree.

The same applies more broadly: `ConstantPool.register` is explicitly concurrent, `TypeInfo` building
is `synchronized` and copes with concurrent callers, and the runtime schedules services on a
`ThreadPoolExecutor` sized to `availableProcessors()`. Component structures are read on service
threads during ordinary execution. This is not a single-threaded object graph.

### 3c. What the PR body implies about intent, stated as inference not fact

The body says this is a building block for "bundles, efficient caching", and adds:

> there are going to now be opportunities to efficiently replace things like array based APIs with
> e.g. `List`, now that we have mutable vs immutable in the structures.

That is worth reading closely, because it identifies the *purpose* of the immutability: **a licence
to hand out references to internal state instead of copying it.** That is the right reason to want
immutability. It is also the reason the publication question matters more here, not less — the whole
point is that more code will hold references to structures it does not own, for longer.

I do not know whether the author has a single-threaded lifecycle in mind; the PR does not say, and
the diff contains no concurrency vocabulary at all. That is a question to ask, not to assume the
answer to. But note that "the flag is only ever set on one thread" would not be sufficient on its
own — what matters is whether a structure can be frozen *after* it has been shared, and
`ensureReadOnly()` on a live object makes that expressible.

## 4. `clone()` — the PR adds one, and it is shallow

```java
@Override
protected XvmStructure clone() throws CloneNotSupportedException {
    XvmStructure that = (XvmStructure) super.clone();
    that.m_fReadOnly = false;
    return that;
}
```

`Object.clone()` is a **shallow** copy. So the returned "mutable" structure shares every referenced
sub-object with the frozen original, and the only thing distinguishing them is a boolean. Any state
not explicitly deep-copied by a subclass is now reachable and writable through the clone while the
original claims to be immutable.

That is precisely the hazard already raised in this PR's pending review ("owners end up in a
different pool or container"). It is not theoretical here: this branch found four separate instances
of the same shape — `Parameter` elements shared between a super method and a short-hand override,
`Contribution` bodies keeping a hidden outer owner, `MethodStructure.Source` likewise, and five
handle view-clone lifecycle desyncs.

**Recommendation:** a copy constructor per type, with a covariant return type. It cannot silently
share what the author forgot about, it survives adding a field (the compiler does not warn you that
`clone()` now shares one more thing), and it does not force a cast from `Object`.

## 5. `ensureMutable()` hides an enormous cost behind a small name

```java
protected XvmStructure ensureMutable() {
    return isReadOnly()
            ? findThisIn(new FileStructure(getFileStructure()))
            : this;
}
```

Calling a method named `ensureMutable` on one `Parameter` copies the **whole module** and then
searches the copy for the counterpart. Nothing at the call site suggests that. A lazy getter, a
display path, or a loop that touches many structures will do this once per element.

**Recommendations:** name it for what it does (`copyFileStructureAndFindThis()` is ugly and honest);
or return an explicit result type that makes the copy visible; or provide a bulk entry point so a
caller converting many structures pays once. At minimum, javadoc the cost in the first sentence,
because the current first sentence is "Obtain a mutable version of this XVM structure."

## 6. The deep problem: immutability is advisory, not structural

`isReadOnly()` is a runtime question. Nothing in the *type system* distinguishes a frozen structure
from a mutable one, so:

- every mutator must remember to call `verifyMutable()` — ~30 sites in this PR, and every future
  mutator, forever. Missing one is silent, and there is no compiler help;
- a caller cannot express "I require an immutable structure" in a signature. It has to accept
  `Component` and hope, or check at run time and throw;
- `findThisIn` must be implemented per subclass to re-establish identity across a copy. This is the
  same identity-mapping problem that produced several of the bugs cited in §4.

This is the "Python in Java" pattern this branch has been documenting: a type distinction promoted
to a runtime check, so the compiler cannot prove a use is correct, and a case the author did not
consider compiles cleanly.

**The future-safe shape**, in rough order of cost:

- **`sealed` + a distinct immutable type.** `Component` sealed over `MutableComponent` and
  `ImmutableComponent`, or an `ImmutableView` wrapper. A method that needs immutability says so in
  its signature, and `verifyMutable()` becomes unnecessary because a mutator does not exist on the
  immutable type. This branch has already sealed the constant families, so the pattern is
  established and the tooling is understood.
- **Freeze by construction.** `toImmutable()` returns a *new* instance with `final` fields, rather
  than flipping a flag on the existing one. Removes the entire memory-model problem in §3 and makes
  §4 impossible, because there is nothing to shallow-copy into a mutable state.
- **A source-shape ratchet**, if neither of the above is affordable now: a test that enumerates every
  mutator on `XvmStructure` subclasses and asserts each calls `verifyMutable()`. This branch has
  built several such ratchets; the reliable form enumerates from **bytecode**, not source text.
  That converts "we remembered" into "the build checks".

## 7. Reinforcing the review comments already on the PR

Each of these already has a comment; here is the supporting evidence from this branch's audit.

- **`null` as "absent" for collections** (`m_listContribs`, `m_mapParams`, `m_listInject`). Every one
  produces a non-final field, a null check at each use, and a branch that can be got wrong. This
  branch removed **28** such null-guard sites from the `ErrorListener` plane and reached zero; the
  measurable result was that "I do not want this" and "I did not think about this" stopped compiling
  to the same thing. An always-present empty collection also lets the field be `final`, which — see
  §3 — is what makes safe publication automatic.
- **`clone()` must go.** See §4. Note this PR *adds* one to the base class, so the count moves the
  wrong way.
- **`Arrays.copyOf` over `clone()` for arrays.** Agreed, and note `Arrays.copyOf` also survives a
  change of element type, where `clone()` silently returns the wrong static type.
- **Copy constructors with a correct return type instead of casting `Object`.** Agreed, and the
  strongest form of the argument is that the cast is not merely ugly: this branch found a blind cast
  inside a `CompletableFuture.whenComplete` where the failure was *swallowed by the completion
  stage*, converting a reported failure into a lost one. Casts in error paths are the worst place to
  discover a type was wrong.
- **Chainable `registerConstants(m_pool.preRegisterAll())` returning something useful.** Agreed.

## 8. Smaller notes

- `verifyMutable()` returns `boolean` and can only ever return `true` (it throws otherwise). If that
  signature exists so it can be used as `assert verifyMutable();`, then any such use is a check that
  disappears under `-da`. Either make it `void`, or document that it must never be called inside an
  assert. Right now the signature invites exactly the mistake the current call sites avoid.
- `Contribution.markReadOnly()` is asymmetric: `m_listInject` is copied **and** wrapped
  unmodifiable, `m_mapParams` is only copied. The read accessors wrap both, so this is not a hole —
  but inconsistency in an invariant of this kind is how holes are born.
- `markReadOnly()` freezes what `getContained()` reports. Any state a subclass holds that is not
  reachable through `getContained()` is silently not frozen. Worth an explicit test per subclass, or
  a ratchet, rather than an assumption.
- The test file covers cascade, guard, whole-file copy, counterpart lookup, injection-list ownership
  and covariant return declarations. **It contains no concurrency test**, which given §3 is the gap
  that matters most.

## 9. Summary

The feature is wanted and the implementation is careful in places others would not have been. But as
it stands it provides an immutability guarantee that is **not safe to rely on across threads**,
which is the only reason the PR body gives for wanting it.

Priority order:

1. **Blocking:** the memory model (§3). At minimum `volatile`; preferably freeze-by-construction.
2. **Blocking-ish:** the added shallow `clone()` (§4) — replace with copy constructors before this
   spreads to 20 subclasses.
3. **Should fix now, cheap:** `null`-as-absent collections → final empty collections; this also
   helps §3.
4. **Should agree the direction now, implement later:** structural rather than advisory
   immutability (§6), with a bytecode-based ratchet in the meantime.
5. **Polish:** `ensureMutable` naming/cost, `verifyMutable`'s return type, the `Contribution`
   asymmetry.
