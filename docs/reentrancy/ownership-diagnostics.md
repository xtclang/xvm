# Runtime Ownership Diagnostics

`OwnershipDiagnostics` is a read-only dump utility for inspecting owner-scoped
runtime state rooted at one or more `Container` instances.

The purpose is to make hidden owner mistakes visible during manual stress runs:

- a template cached under container A that reports container B as its owner,
- a handle or composition returned through A that belongs to B,
- a computed lazy cache that contains owner-bearing values from the wrong
  container,
- and object identities shared between two inspected containers when the object
  is supposed to be owner-scoped.

## API

```java
String dump = OwnershipDiagnostics.dump(containerA, containerB);
```

The default mode is non-invasive. It reports lazy cells that are already
computed and marks deferred cells as deferred. It does not instantiate new
templates, views, handles, or metadata just because the dump was requested.

For a deliberately complete snapshot after startup warmup:

```java
String dump = OwnershipDiagnostics.dump(true, containerA, containerB);
```

`forceLazy=true` calls `Lazy.get()` on deferred cells while dumping. That is
useful in a diagnostic harness, but it is not appropriate for normal runtime
logging because it changes what has been warmed.

The same traversal is available as a validator:

```java
OwnershipDiagnostics.Validation validation =
        OwnershipDiagnostics.validate(containerA, containerB);

if (!validation.isValid()) {
    throw new AssertionError(validation.message());
}
```

For stress tests and manual runners that should fail immediately on illegal
ownership, use the throwing shortcut:

```java
OwnershipDiagnostics.assertValid(containerA, containerB);
```

`assertValid(...)` throws `IllegalStateException` when it finds an owner
mismatch, a constant-pool mismatch, or an owner-scoped object identity shared by
two inspected containers. This is the mode to wire into race reproducers once a
workload has warmed the path being checked.

## What It Dumps

For each container, the dump includes:

- the container identity, module, and constant pool identity,
- the owner-local `NativeTemplates` table,
- computed native-template lookup entries,
- final `Lazy` fields on `NativeTemplates` and computed template instances,
- the container's `templatesByType` cache,
- the container's `compositions` cache,
- service contexts known to the container,
- owner mismatches detected through runtime APIs,
- constant-pool mismatches detected on traversed constants,
- and cross-container object identity sharing for owner-bearing objects.

Owner-bearing objects currently include:

- `ClassTemplate`,
- `TypeComposition`,
- `ObjectHandle`,
- `ServiceContext`,
- and `NativeTemplates`.

Constants are described with their `ConstantPool` identity so a dump can show
pool mismatches for values that appear under the wrong container.

## Interpreting The Checks

`owner-mismatches: none` means every owner-bearing object reached from the
inspected roots reported the expected container owner.

`cross-container-shares: none` means the inspected containers did not share the
same owner-bearing object identity through the traversed caches.

A finding is actionable when an owner-scoped value appears under a different
container, for example:

```text
owner-mismatches: 1
  - value expected=C0 actual=C1 object=JavaLong@4c873330
```

That is the concrete shape of the old `xBoolean.TRUE`, `xNullable.NULL`,
`xOrdered.EQUAL`, `xBit.ZERO`, and native-template `INSTANCE` bugs: a caller
selects one container but receives a template, composition, or handle owned by
another.

## Using It In Stress Runs

The most useful placement is after the stress harness has warmed both
containers enough to exercise the path under investigation:

```java
System.err.println(OwnershipDiagnostics.dump(containerA, containerB));
```

Use default mode first. If the dump shows only deferred cells for the path being
investigated, rerun the same test with `forceLazy=true` after the workload has
finished. Forced mode is for diagnostics only; it intentionally changes cache
warmup timing.

## Limitations

The dump is an observation tool, not a proof by itself. It can prove that an
observed graph is wrong, and it can provide strong regression evidence when a
stress run exercises a path and reports no owner mismatches. It cannot prove
that uninstantiated lazy cells or unexecuted native paths are safe.

The dump also does not enumerate every loaded JVM class or every static field in
the process. The static-field inventory remains source-scan based in
[state-inventory.md](state-inventory.md). The dump complements those scans by
showing the runtime object graph that actually exists during a run.
