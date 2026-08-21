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
The owning `NativeTemplates` table itself is already a final field on
`Container`; inspecting it does not allocate a new owner table.

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

Validation traverses the same owner graph as `dump(...)`, but it does not build
or retain the full textual dump on the success path. Full dump text is reserved
for explicit `dump(...)` calls and failure logging.

For hot runtime boundaries where the expected owner is known but a full
container graph scan would be too expensive, validate just the returned handle:

```java
OwnershipDiagnostics.assertHandleValidIfEnabled(
        frame.f_context.f_container,
        "mgmt.Container.invoke module target",
        hModule);
```

That helper is controlled by the `xvm.runtime.validateOwnership` system
property. It is intended for stress and diagnostic runs, not normal production
execution. `manualTests:runParallelStress` enables it so wrong-owner module
targets fail at the native boundary that produced them.

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

## Native Parent Ownership

The validator deliberately allows one narrow kind of sharing: a main container
may point at a class template owned by its own `NativeContainer` parent.
`Container.getTemplate(...)` already has that semantics. Shared/core templates
such as `xArray`, `xVar`, `xListMap`, and many numeric templates are canonical
native templates, while the main container may cache owner-local keys that refer
to those native implementation objects.

This allowance applies only to the inspected container's own native parent. It
does not allow a main container from run N to point at run N-1's native
container, and it does not allow arbitrary owner-scoped values to be shared
between two main containers.

The same native-parent allowance applies to constant-heap handle values. A main
container's constant heap can memoize canonical native-parent values such as
small `Char`, `Int`, `Boolean`, `String`, and native enum handles. Those are not
cross-run leaks when their owner is the current container's own
`NativeContainer`. They are still rejected if they come from another main
container or another runtime's native parent.

The traversal also changes expectation at the boundary. If a main container's
cache points at a native-owned template, the nested lazy fields of that template
are validated against the native owner, not against both owners. That catches a
native template that accidentally retains a main-container value and would leak
it to another run.

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

`manualTests:runDirectSequenceStress` enables this validator by setting
`XtcRunTask.validateRuntimeOwnership=true`. The Gradle plugin direct executor
keeps a bounded window of completed interpreter containers observed by the
build-scoped direct runtime classloader and calls
`OwnershipDiagnostics.assertValid(...)` after each successful direct
`Runner.run()`. The current container is always validated, so stale owner values
reachable from the current run fail structurally even when the workload happens
not to crash. The recent-container window catches direct cross-run sharing
without making the diagnostic harness retain every completed runtime graph in a
long all-module stress run. The default window is six containers and can be
overridden with the `org.xtclang.directRuntimeOwnershipWindow` system property.

`manualTests:runParallelStress` also enables the lightweight fail-fast property:

```text
-Dxvm.runtime.validateOwnership=true
```

That caught the `TestProps` adoption leak as an owner mismatch at
`mgmt.Container.invoke` before the run reached the later
`!&lazyInstance.assigned` assertion. The lesson is important for future
debugging: a high-level XTC state failure can be downstream of an owner leak in
a handle graph. When a parallel-only failure looks impossible at the language
level, enable ownership validation and check the first wrong-owner boundary.

## Limitations

The dump is an observation tool, not a proof by itself. It can prove that an
observed graph is wrong, and it can provide strong regression evidence when a
stress run exercises a path and reports no owner mismatches. It cannot prove
that uninstantiated lazy cells or unexecuted native paths are safe.

The dump also does not enumerate every loaded JVM class or every static field in
the process. The static-field inventory remains source-scan based in
[state-inventory.md](state-inventory.md). The dump complements those scans by
showing the runtime object graph that actually exists during a run.
