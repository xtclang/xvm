# Draft review to post on PR #565 — NOT POSTED

Nine comments are already pending on this PR (null-as-absent, clone removal, `Arrays.copyOf`,
chainable `registerConstants`, copy constructors). The seven below would be added, plus the summary.

---

## SUMMARY COMMENT (the review body)

The feature is wanted and parts of it are more careful than they look — `verifyMutable()` is called
as a plain statement rather than inside an `assert`, so enforcement survives `-da`, and
`Contribution.markReadOnly()` *copies* the collections, which neutralises any reference a caller
grabbed while the structure was still mutable. Both of those are easy to get wrong and are right here.

One question gates the rest of the review, and I would rather ask it than assume the answer:

> **Can a structure be frozen after it has been shared with another thread — or is the rule
> "build, freeze, then publish"?**

If the rule is build-freeze-publish, then `m_fReadOnly` being a plain `boolean` is *correct* and
cheaper than `volatile`: the publication supplies the happens-before edge for the flag and for
everything reachable from the structure. I would just want that contract written down, because
nothing currently states it and the API makes the other order equally easy to write —
`ensureReadOnly()` acts on a live object, `markReadOnly()` is itself a mutation, and
`ensureMutable()`/`clone()` mint a mutable structure from a frozen one at an arbitrary later time.

If the answer is "yes, or not sure", then this needs more than a modifier — see the inline comment
on the field.

Worth flagging regardless of the answer: **#549 is open and argues that `MethodStructure.m_fNative`
and `m_code` must be `volatile` because they are "written by one thread and read by others".**
`MethodStructure` is one of the classes this PR adds `m_fReadOnly` to. Those two PRs should agree
with each other before either lands.

Separately, and not blocking: this makes immutability an *advisory* property — a runtime check that
every current and future mutator has to remember. Worth deciding now whether the eventual direction
is a structural one (a distinct immutable type, or freeze-by-construction returning a new instance
with final fields), because that choice changes what is worth building on top of this.

---

## INLINE COMMENTS

### 1. `XvmStructure.java:695` — `private boolean m_fReadOnly;`

This is the field the whole feature rests on, and it has no publication story.

If the lifecycle is build → freeze → publish, this is fine as-is and I would only ask for the
contract to be stated. If a structure can be frozen *after* being shared, then two things break:
another thread can keep observing `false` and mutate through `verifyMutable()`; and even once it
sees `true`, nothing orders your writes to the structure's other fields against its reads, so it can
see a frozen structure with stale contents.

Cheapest fix if the ordering is not guaranteed: `volatile`. On a field this cold the cost is not
measurable and it removes the question. The better fix is freeze-by-construction — `toImmutable()`
returning a new instance whose fields are `final` — because then the ordering cannot be got wrong by
anyone later.

### 2. `XvmStructure.java:672` — the new `clone()` override

`Object.clone()` is shallow, so this returns a "mutable" structure that shares every referenced
sub-object with the frozen original; the only difference between them is the boolean cleared on the
next line. Anything a subclass does not explicitly deep-copy is now writable through the clone while
the original claims to be immutable.

This is also the opposite direction from where we want the codebase to go — a copy constructor per
type gives a correct return type instead of a cast from `Object`, cannot silently share what the
author forgot about, and does not quietly start sharing one more thing when a field is added later.

### 3. `XvmStructure.java:195` — `ensureMutable()`

`new FileStructure(getFileStructure())` copies the **entire containing file** and then searches the
copy for the counterpart. Calling something named `ensureMutable` on one `Parameter` therefore
copies a whole module, and nothing at the call site suggests it.

Could the cost be visible in the name or the return type? Failing that, a bulk entry point so a
caller converting many structures pays once, and at minimum a first javadoc sentence that says it
copies — the current one is "Obtain a mutable version of this XVM structure."

### 4. `XvmStructure.java:180` — `verifyMutable()` returns `boolean`

Every call site here uses it as a statement, which is right. But a method that returns `true` and
otherwise throws is shaped exactly like something meant for `assert verifyMutable();` — and written
that way it silently stops checking anything under `-da`.

There is precedent for that going wrong a few lines from here: `Contribution.getTypeParams()` still
contains `assert (map = Collections.unmodifiableMap(map)) != null;`, an assignment inside an assert
that stops protecting the map entirely when assertions are off.

Suggest `void`, so the mistake is not expressible.

### 5. `XvmStructure.java:224` — `markReadOnly()`

Freezing covers exactly what `getContained()` reports. Any state a subclass holds that is not
reachable that way is silently left mutable inside a structure that answers `isReadOnly() == true`.

That is an easy thing to get wrong once and never notice. Worth a per-subclass test, or a ratchet
that enumerates fields and asserts each is either frozen, immutable by type, or explicitly exempt.

### 6. `XvmStructure.java:254` — `abstract findThisIn(FileStructure)`

Every subclass now has to re-establish its own identity inside a copy of the file. That is the
identity-mapping problem, and it is where this codebase has repeatedly gone wrong: shared `Parameter`
elements between a super method and a short-hand override, `Contribution` bodies retaining a hidden
outer owner, `MethodStructure.Source` likewise.

Is there a way to make the mapping the copy's responsibility — built once as the copy is made —
rather than something each subclass re-derives by searching?

### 7. `Component.java:3045` — `Contribution.markReadOnly()`

`m_listInject` is copied **and** wrapped unmodifiable; `m_mapParams` is only copied. The read
accessors wrap both, so this is not a hole today — but the asymmetry is how holes get born, and a
reader cannot tell which of the two is the intended pattern.

(The copy itself is the good part and worth keeping: it detaches any reference a caller obtained
while the structure was mutable.)
