package org.xvm.runtime;


import java.lang.ref.Reference;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.XvmStructure;

import org.xvm.util.Auto;

import org.xvm.asm.constants.HandleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.util.Lazy;

import static java.util.Objects.requireNonNull;


/**
 * Read-only diagnostics for inspecting container-owned runtime state.
 *
 * <p>The default dump reports only already-computed lazy values. Use
 * {@link #dump(boolean, Container...)} with {@code forceLazy=true} only when
 * intentional cache warmup is acceptable, because that mode calls
 * {@link Lazy#get()} or owner-aware lazy get on deferred cells.</p>
 */
public final class OwnershipDiagnostics {
    /**
     * System property that enables fail-fast runtime boundary ownership checks.
     */
    public static final String VALIDATE_PROPERTY = "xvm.runtime.validateOwnership";

    private OwnershipDiagnostics() {
    }

    /**
     * Dump already-instantiated owner-scoped runtime state for one or more
     * containers.
     *
     * @param containers  the containers to inspect
     *
     * @return a text dump suitable for logs or test artifacts
     */
    public static String dump(Container... containers) {
        return dump(false, containers);
    }

    /**
     * Dump owner-scoped runtime state for one or more containers.
     *
     * @param forceLazy   true to compute deferred lazy cells while dumping
     * @param containers  the containers to inspect
     *
     * @return a text dump suitable for logs or test artifacts
     */
    public static String dump(boolean forceLazy, Container... containers) {
        try (var _ = openDiagnosticsWindow(containers)) {
            return new Dumper(forceLazy).dump(containers);
        }
    }

    /**
     * Validate already-instantiated owner-scoped runtime state for one or more containers.
     *
     * @param containers  the containers to inspect
     *
     * @return structured validation findings
     */
    public static Validation validate(Container... containers) {
        return validate(false, containers);
    }

    /**
     * Validate owner-scoped runtime state for one or more containers.
     *
     * @param forceLazy   true to compute deferred lazy cells while validating
     * @param containers  the containers to inspect
     *
     * @return structured validation findings
     */
    public static Validation validate(boolean forceLazy, Container... containers) {
        try (var _ = openDiagnosticsWindow(containers)) {
            Dumper dumper = new Dumper(forceLazy, false);
            dumper.scan(containers);
            return dumper.validation();
        }
    }

    /**
     * Open a runtime-synthesis window for the duration of a diagnostics walk. Diagnostics are
     * supervised readers, but rendering a type constant's value computes isA relations, and the
     * relation calculus interns normalized types - on a runtime-published pool that trips the
     * registration guard from inside the very forensics reporting a failure, replacing the
     * original finding with the guard's own IllegalStateException (the same-JVM stress harness
     * proved that exact masking). The window is thread-scoped, so any pool instance opens it
     * for every pool the walk can reach.
     */
    private static Auto openDiagnosticsWindow(Container... containers) {
        if (containers.length == 0 || containers[0] == null) {
            return () -> { };
        }
        return containers[0].getConstantPool().openRuntimeSynthesisWindow("ownership diagnostics");
    }

    /**
     * Validate already-instantiated owner-scoped runtime state and throw on illegal ownership.
     *
     * @param containers  the containers to inspect
     */
    public static void assertValid(Container... containers) {
        validate(containers).assertValid();
    }

    /**
     * Validate owner-scoped runtime state and throw on illegal ownership.
     *
     * @param forceLazy   true to compute deferred lazy cells while validating
     * @param containers  the containers to inspect
     */
    public static void assertValid(boolean forceLazy, Container... containers) {
        validate(forceLazy, containers).assertValid();
    }

    /**
     * Return the containers currently registered under the same runtime as the supplied root,
     * including the root itself.
     *
     * @param root  a container in the runtime to inspect
     *
     * @return a stable snapshot of related containers
     */
    public static Set<Container> runtimeContainers(Container root) {
        requireNonNull(root, "root");

        Set<Container> containers = new LinkedHashSet<>(root.f_runtime.containers());
        containers.add(root);
        return Collections.unmodifiableSet(containers);
    }

    /**
     * Validate all currently registered containers under the same runtime as the supplied root.
     *
     * @param root  a container in the runtime to inspect
     *
     * @return structured validation findings
     */
    public static Validation validateRuntime(Container root) {
        return validate(runtimeContainers(root).toArray(Container[]::new));
    }

    /**
     * Validate all currently registered containers under the same runtime as the supplied root and
     * throw on illegal ownership.
     *
     * @param root  a container in the runtime to inspect
     */
    public static void assertRuntimeValid(Container root) {
        validateRuntime(root).assertValid();
    }

    /**
     * Dump all currently registered containers under the same runtime as the supplied root.
     *
     * @param root  a container in the runtime to inspect
     *
     * @return a text dump suitable for logs or test artifacts
     */
    public static String dumpRuntime(Container root) {
        return dump(runtimeContainers(root).toArray(Container[]::new));
    }

    /**
     * Capture a structured snapshot of the whole world: every container currently registered with
     * the runtime, plus the full ownership validation run across that complete set.
     *
     * <p>This is slice 1 of the world-state snapshot plan (see
     * {@code docs/reentrancy/ownership-diagnostics.md}): the snapshot is a queryable value, the
     * text dump is just one rendering of it, and two snapshots are diffable. The intended
     * sequential-run usage is: snapshot the world after run N completes, run N+1, snapshot again,
     * and treat every {@link WorldDiff#retained() retained} container from a completed earlier run
     * as a liveness leak - the registry only holds containers weakly, so a completed run's
     * container can appear in a later world only while something still strongly references it.
     * Execution-state capture (fibers, frames, registers) and the generic reflective reachability
     * sweep are the next slices.
     *
     * @param runtime  the runtime whose registered containers should be captured
     *
     * @return the world snapshot
     */
    public static WorldSnapshot snapshotWorld(Runtime runtime) {
        requireNonNull(runtime, "runtime");

        List<Container> containers = new ArrayList<>(runtime.containers());
        containers.sort(Comparator
                .comparing((Container container) -> String.valueOf(container))
                .thenComparingInt(System::identityHashCode));

        List<ContainerInfo> infos = containers.stream()
                .map(container -> new ContainerInfo(
                        System.identityHashCode(container),
                        String.valueOf(container),
                        Integer.toHexString(System.identityHashCode(container.getConstantPool()))))
                .toList();

        return new WorldSnapshot(infos, validate(containers.toArray(Container[]::new)));
    }

    /**
     * One container's identity row in a {@link WorldSnapshot}. The identity hash is the diff key;
     * the snapshot deliberately does not retain the container object itself, so holding old
     * snapshots for comparison cannot itself keep dead worlds alive.
     */
    public record ContainerInfo(int identity, String label, String pool) {}

    /**
     * A structured, diffable capture of every live container and the ownership validation across
     * the complete set.
     */
    public record WorldSnapshot(List<ContainerInfo> containers, Validation validation) {
        public WorldSnapshot {
            containers = List.copyOf(containers);
        }

        /**
         * @return true iff the captured world has no illegal owner relationship
         */
        public boolean isValid() {
            return validation.isValid();
        }

        /**
         * Compare this (later) world against an earlier one by container identity.
         *
         * @param before  the earlier world snapshot
         *
         * @return the containers added since, retained across, and removed since {@code before}
         */
        public WorldDiff diffFrom(WorldSnapshot before) {
            requireNonNull(before, "before");

            Map<Integer, ContainerInfo> old = before.containers.stream()
                    .collect(Collectors.toMap(ContainerInfo::identity, info -> info,
                            (a, b) -> a));
            Map<Integer, ContainerInfo> now = this.containers.stream()
                    .collect(Collectors.toMap(ContainerInfo::identity, info -> info,
                            (a, b) -> a));

            List<ContainerInfo> added    = this.containers.stream()
                    .filter(info -> !old.containsKey(info.identity())).toList();
            List<ContainerInfo> retained = this.containers.stream()
                    .filter(info -> old.containsKey(info.identity())).toList();
            List<ContainerInfo> removed  = before.containers.stream()
                    .filter(info -> !now.containsKey(info.identity())).toList();
            return new WorldDiff(added, retained, removed);
        }

        /**
         * @return a multi-line human-readable rendering of the snapshot
         */
        public String render() {
            StringBuilder sb = new StringBuilder("world: ")
                    .append(containers.size())
                    .append(" container(s)");
            for (ContainerInfo info : containers) {
                sb.append("\n  - ").append(info.label())
                  .append(" id=").append(Integer.toHexString(info.identity()))
                  .append(" pool=").append(info.pool());
            }
            sb.append('\n')
              .append(isValid() ? "ownership: valid" : validation.message());
            return sb.toString();
        }
    }

    /**
     * The identity-keyed difference between two world snapshots. For sequential same-JVM runs,
     * {@link #retained()} entries that belong to a completed earlier run are liveness leaks.
     */
    public record WorldDiff(List<ContainerInfo> added,
                            List<ContainerInfo> retained,
                            List<ContainerInfo> removed) {
        public WorldDiff {
            added    = List.copyOf(added);
            retained = List.copyOf(retained);
            removed  = List.copyOf(removed);
        }

        /**
         * @return a multi-line human-readable rendering of the diff
         */
        public String render() {
            StringBuilder sb = new StringBuilder("world diff:");
            renderSection(sb, "added", added);
            renderSection(sb, "retained", retained);
            renderSection(sb, "removed", removed);
            return sb.toString();
        }

        private static void renderSection(StringBuilder sb, String label,
                                          List<ContainerInfo> infos) {
            sb.append('\n').append(label).append(": ").append(infos.size());
            for (ContainerInfo info : infos) {
                sb.append("\n  - ").append(info.label())
                  .append(" id=").append(Integer.toHexString(info.identity()));
            }
        }
    }

    // ----- generic reachability sweep (world X-ray slice 3) --------------------------------------

    /**
     * Walk the complete Java object graph reachable from a container's state and report every
     * owner-scoped object whose owner is UNRELATED to the swept container - not the container
     * itself, not one of its ancestors, and not one of its descendants. This is the generic
     * detector that the curated {@link #dump}/{@link #validate} walkers cannot be: it does not
     * depend on anyone having enumerated the field that leaks, because it visits every reference,
     * every array element, every collection entry.
     * <p/>
     * What it flags: a MUTABLE {@link ObjectHandle} owned by an unrelated container (immutable
     * handles are legitimate cross-owner currency by the pass-through protocol, and
     * service/proxy handles are the designed cross-container mechanism); any
     * {@link ClassTemplate}, {@link TypeComposition}, {@link NativeTemplates}, or
     * {@link ServiceContext} owned by an unrelated container (those are never legitimate
     * cross-container state); and a mutable live handle cached by a {@code HandleConstant} in a
     * reachable pool whose owner is unrelated.
     * <p/>
     * Deliberate scope limits, stated so nobody mistakes a clean sweep for more than it is:
     * other {@link Container} objects and unrelated {@link ServiceContext}s are boundaries - the
     * sweep flags them when unrelated but does not descend into their state (sweep each
     * container separately); the constant/structure plane below {@link ConstantPool} is
     * owner-neutral module data and is not walked (except the {@code HandleConstant} live-handle
     * check); {@code static} roots are not enumerated - process-global leaks through statics are
     * the province of the source-shape scans and the parked JIT-statics rows; and a reachability
     * sweep proves retained references, not temporal races - the stress harness owns those.
     *
     * @param root  the container whose reachable state to sweep
     *
     * @return the sweep report; never silently truncated
     */
    public static SweepReport sweepForeignReferences(Container root) {
        try (var _ = openDiagnosticsWindow(root)) {
            return sweepForeignReferencesInWindow(root);
        }
    }

    private static SweepReport sweepForeignReferencesInWindow(Container root) {
        Map<Object, Boolean>   visited        = new IdentityHashMap<>();
        Deque<SweepNode>       queue          = new ArrayDeque<>();
        List<ForeignReference> violations     = new ArrayList<>();
        List<String>           blindSpots     = new ArrayList<>();
        List<String>           pendingFutures = new ArrayList<>();

        queue.add(new SweepNode(root, null, "container"));
        visited.put(root, Boolean.TRUE);

        int cVisited = 0;
        while (!queue.isEmpty()) {
            SweepNode node  = queue.poll();
            Object    value = node.value();
            ++cVisited;

            // ownership check for owner-scoped values
            if (Dumper.isOwnerScoped(value)) {
                Container owner = Dumper.ownerOf(value);
                if (owner != null && !isRelated(root, owner)
                        && (!(value instanceof ObjectHandle handle) || isMutableSafe(handle))) {
                    violations.add(new ForeignReference(
                            node.renderPath(), describeValue(value), describeContainer(owner)));
                }
            }

            // descent
            if (value instanceof Container container) {
                if (container != root) {
                    continue; // container boundary: sweep it separately
                }
            } else if (value instanceof ServiceContext context) {
                Container owner = context.f_container;
                if (owner != null && !isRelated(root, owner)) {
                    continue; // foreign context: flagged above, not walked
                }
            } else if (value instanceof ObjectHandle handle && !isMutableSafe(handle)) {
                // immutable handles are frozen legitimate cross-owner currency: their whole
                // graph (composition included) belongs to the origin container by design, so
                // walking it would flag every received pass-through handle as a false positive
                continue;
            }

            if (value instanceof ConstantPool pool) {
                sweepPoolHandles(root, node, pool, violations);
                continue;
            }
            if (value instanceof XvmStructure || value instanceof Constant) {
                continue; // owner-neutral module plane
            }

            if (value instanceof Object[] aValue) {
                for (int i = 0; i < aValue.length; ++i) {
                    enqueue(queue, visited, node, "[" + i + "]", aValue[i]);
                }
                continue;
            }
            Class<?> clz = value.getClass();
            if (clz.isArray()) {
                continue; // primitive array
            }

            if (value instanceof Reference<?> ref) {
                Object referent = ref.get();
                if (referent != null && Dumper.isOwnerScoped(referent)) {
                    // ownership-check the referent without walking through the weak edge
                    enqueueBoundary(queue, visited, node, ".referent", referent);
                }
                continue;
            }
            if (value instanceof AtomicReference<?> ref) {
                enqueue(queue, visited, node, ".get()", ref.get());
                continue;
            }
            if (value instanceof AtomicReferenceArray<?> aRef) {
                for (int i = 0, c = aRef.length(); i < c; ++i) {
                    enqueue(queue, visited, node, ".get(" + i + ")", aRef.get(i));
                }
                continue;
            }
            if (value instanceof Optional<?> optional) {
                enqueue(queue, visited, node, ".get()", optional.orElse(null));
                continue;
            }
            if (value instanceof Map<?, ?> map && clz.getName().startsWith("java.")) {
                int i = 0;
                for (Map.Entry<?, ?> entry : safeEntries(map)) {
                    enqueue(queue, visited, node, ".key[" + i + "]", entry.getKey());
                    enqueue(queue, visited, node, ".value[" + i + "]", entry.getValue());
                    ++i;
                }
                continue;
            }
            if (value instanceof Iterable<?> iterable && clz.getName().startsWith("java.")) {
                int i = 0;
                for (Object element : safeElements(iterable)) {
                    enqueue(queue, visited, node, "[" + i + "]", element);
                    ++i;
                }
                continue;
            }
            if (value instanceof Map.Entry<?, ?> entry) {
                enqueue(queue, visited, node, ".key", safeKey(entry));
                enqueue(queue, visited, node, ".value", safeValue(entry));
                continue;
            }
            if (value instanceof CompletableFuture<?> future) {
                // non-blocking: a completed future's result is reachable state. A PENDING future
                // is legal live state, not an unfollowable ownership edge: its value does not
                // exist yet, and its dependent continuations were allocated by this container's
                // own execution (cross-container calls exchange proxies, never raw handles), so
                // the unreadable content cannot carry a foreign reference. The stress harness
                // proved runs routinely end with fire-and-forget futures still pending, so they
                // are counted and rendered for forensics but do not fail the sweep - only a
                // genuinely unfollowable edge (reflection failure, unknown carrier type) does.
                if (future.isDone() && !future.isCompletedExceptionally()) {
                    enqueue(queue, visited, node, ".getNow()", future.getNow(null));
                } else if (!future.isDone()) {
                    pendingFutures.add(node.renderPath() + " -> pending CompletableFuture");
                }
                continue;
            }

            if (isSweepLeaf(clz)) {
                continue;
            }

            if (clz.getName().startsWith("org.xvm")) {
                for (Class<?> c = clz; c != null && c != Object.class; c = c.getSuperclass()) {
                    for (Field field : c.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers())
                                || field.getType().isPrimitive()) {
                            continue;
                        }
                        try {
                            field.setAccessible(true);
                            enqueue(queue, visited, node, "." + field.getName(), field.get(value));
                        } catch (RuntimeException | ReflectiveOperationException | Error e) {
                            // an edge the sweep cannot follow is a measured blind spot, never a
                            // silent one
                            blindSpots.add(node.renderPath() + "." + field.getName()
                                    + " -> inaccessible: " + e);
                        }
                    }
                }
                continue;
            }

            // JDK types other than the walked generic carriers (collections, entries, atomics,
            // references, futures, optionals) have no Object-typed user-data slots that runtime
            // state can hide in, so they are documented leaves rather than blind-spot noise; an
            // unrecognized type from any OTHER origin that can hold references is a measured
            // blind spot
            String sName = clz.getName();
            if (!sName.startsWith("java.") && !sName.startsWith("javax.")
                    && !sName.startsWith("com.sun.") && holdsReferences(clz)) {
                blindSpots.add(node.renderPath() + " -> unhandled type " + sName);
            }
        }

        return new SweepReport(describeContainer(root), cVisited,
                List.copyOf(violations), List.copyOf(blindSpots), List.copyOf(pendingFutures));
    }

    /**
     * @return true iff instances of the class can hold object references the sweep did not walk
     */
    private static boolean holdsReferences(Class<?> clz) {
        for (Class<?> c = clz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void sweepPoolHandles(Container root, SweepNode node, ConstantPool pool,
                                         List<ForeignReference> violations) {
        // the constant plane is owner-neutral module data EXCEPT live handles cached by
        // HandleConstant - exactly the owner-bearing state the ConstHeap guard polices
        Constant[] aconst;
        try {
            aconst = pool.getConstants();
        } catch (RuntimeException ignore) {
            return;
        }
        for (int i = 0; i < aconst.length; ++i) {
            if (aconst[i] instanceof HandleConstant constant) {
                ObjectHandle handle = (ObjectHandle) Dumper.readField(constant, "m_hValue");
                if (handle != null && isMutableSafe(handle)) {
                    Container owner = Dumper.ownerOf(handle);
                    if (owner != null && !isRelated(root, owner)) {
                        violations.add(new ForeignReference(
                                node.renderPath() + ".pool[" + i + "]",
                                describeValue(handle), describeContainer(owner)));
                    }
                }
            }
        }
    }

    private static void enqueue(Deque<SweepNode> queue, Map<Object, Boolean> visited,
                                SweepNode parent, String edge, Object value) {
        if (value != null && visited.putIfAbsent(value, Boolean.TRUE) == null) {
            queue.add(new SweepNode(value, parent, edge));
        }
    }

    private static void enqueueBoundary(Deque<SweepNode> queue, Map<Object, Boolean> visited,
                                        SweepNode parent, String edge, Object value) {
        // enqueue for the ownership check only; the descent rules above stop the walk there
        enqueue(queue, visited, parent, edge, value);
    }

    private static boolean isRelated(Container root, Container owner) {
        for (Container c = root; c != null; c = c.f_parent) {
            if (c == owner) {
                return true; // self or ancestor
            }
        }
        for (Container c = owner; c != null; c = c.f_parent) {
            if (c == root) {
                return true; // descendant
            }
        }
        return false;
    }

    private static boolean isMutableSafe(ObjectHandle handle) {
        try {
            return handle.isMutable();
        } catch (RuntimeException ignore) {
            // a handle that cannot even answer isMutable() is worth reporting
            return true;
        }
    }

    private static boolean isSweepLeaf(Class<?> clz) {
        if (clz.isEnum() || clz == String.class || clz == Class.class
                || Number.class.isAssignableFrom(clz)        // boxed/atomic/big numerics
                || clz == Boolean.class || clz == Character.class
                || Thread.class.isAssignableFrom(clz)
                || ClassLoader.class.isAssignableFrom(clz)
                || ThreadLocal.class.isAssignableFrom(clz)
                // executors hold in-flight work - temporal state that a reachability sweep
                // cannot meaningfully attribute; the quiesced execution-state capture slice
                // owns it, and a quiesced world's queues are empty
                || Executor.class.isAssignableFrom(clz)) {
            return true;
        }
        String sName = clz.getName();
        return sName.startsWith("java.lang.invoke")
            || sName.startsWith("jdk.")
            || sName.startsWith("sun.");
    }

    private static List<Map.Entry<?, ?>> safeEntries(Map<?, ?> map) {
        try {
            return new ArrayList<>(map.entrySet());
        } catch (RuntimeException ignore) {
            return List.of();
        }
    }

    private static List<Object> safeElements(Iterable<?> iterable) {
        List<Object> list = new ArrayList<>();
        try {
            for (Object element : iterable) {
                list.add(element);
            }
        } catch (RuntimeException ignore) {
            // concurrent mutation mid-iteration: report what was seen
        }
        return list;
    }

    private static Object safeKey(Map.Entry<?, ?> entry) {
        try {
            return entry.getKey();
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    private static Object safeValue(Map.Entry<?, ?> entry) {
        try {
            return entry.getValue();
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    private static String describeValue(Object value) {
        return value.getClass().getSimpleName()
                + "@" + Integer.toHexString(System.identityHashCode(value));
    }

    private static String describeContainer(Container container) {
        return container.getClass().getSimpleName()
                + "@" + Integer.toHexString(System.identityHashCode(container));
    }

    /**
     * One foreign reference found by {@link #sweepForeignReferences}: the field-by-field path
     * from the swept container to the object, the object, and its actual owner.
     */
    public record ForeignReference(String path, String value, String owner) {
        public String render() {
            return path + " -> " + value + " owned by " + owner;
        }
    }

    /**
     * The complete result of one reachability sweep. Never silently truncated: every reachable
     * object was visited or is behind a documented boundary.
     */
    public record SweepReport(String root, int objectsVisited,
                              List<ForeignReference> violations, List<String> blindSpots,
                              List<String> pendingFutures) {
        public SweepReport {
            violations     = List.copyOf(violations);
            blindSpots     = List.copyOf(blindSpots);
            pendingFutures = List.copyOf(pendingFutures);
        }

        /**
         * @return true iff the sweep found no foreign references AND followed every edge - a
         *         report with blind spots is not clean, it is unfinished, and it says where.
         *         Pending futures are counted and rendered but do not make a sweep unclean:
         *         their unread content is same-container by construction (see the walker).
         */
        public boolean isClean() {
            return violations.isEmpty() && blindSpots.isEmpty();
        }

        public void assertClean() {
            if (!isClean()) {
                throw new IllegalStateException(render());
            }
        }

        public String render() {
            StringBuilder sb = new StringBuilder("reachability sweep of ").append(root)
                    .append(": ").append(objectsVisited).append(" objects, ")
                    .append(violations.size()).append(" foreign reference(s), ")
                    .append(blindSpots.size()).append(" blind spot(s), ")
                    .append(pendingFutures.size()).append(" pending future(s)");
            for (ForeignReference violation : violations) {
                sb.append("\n  - ").append(violation.render());
            }
            for (String blindSpot : blindSpots) {
                sb.append("\n  ? ").append(blindSpot);
            }
            for (String pending : pendingFutures) {
                sb.append("\n  ~ ").append(pending);
            }
            return sb.toString();
        }
    }

    private record SweepNode(Object value, SweepNode parent, String edge) {
        String renderPath() {
            StringBuilder sb = new StringBuilder();
            renderTo(sb);
            return sb.toString();
        }

        private void renderTo(StringBuilder sb) {
            if (parent != null) {
                parent.renderTo(sb);
            }
            sb.append(edge);
        }
    }


    /**
     * Validate a handle graph as if it were being used by the specified owner.
     *
     * @param expected  the expected owning container
     * @param path      a diagnostic path naming the boundary being checked
     * @param handle    the handle to inspect
     *
     * @return structured validation findings
     */
    public static Validation validateHandle(Container expected, String path, ObjectHandle handle) {
        Dumper dumper = new Dumper(false, false);
        dumper.scanHandle(expected, path, handle);
        return dumper.validation();
    }

    /**
     * Validate a handle graph and throw on illegal ownership.
     *
     * @param expected  the expected owning container
     * @param path      a diagnostic path naming the boundary being checked
     * @param handle    the handle to inspect
     */
    public static void assertHandleValid(Container expected, String path, ObjectHandle handle) {
        validateHandle(expected, path, handle).assertValid();
    }

    /**
     * Validate a handle graph when {@link #VALIDATE_PROPERTY} is enabled.
     *
     * @param expected  the expected owning container
     * @param path      a diagnostic path naming the boundary being checked
     * @param handle    the handle to inspect
     */
    public static void assertHandleValidIfEnabled(Container expected, String path,
                                                 ObjectHandle handle) {
        if (Boolean.getBoolean(VALIDATE_PROPERTY)) {
            assertHandleValid(expected, path, handle);
        }
    }


    // ----- dumper -------------------------------------------------------------------------------

    /**
     * Structured ownership validation result.
     *
     * @param ownerMismatches       owner-bearing objects found under the wrong inspected owner
     * @param poolMismatches        constants found under a container with a different ConstantPool
     * @param crossContainerShares  owner-bearing object identities shared by different containers
     */
    public record Validation(List<String> ownerMismatches,
                             List<String> poolMismatches,
                             List<String> crossContainerShares) {
        public Validation {
            ownerMismatches      = List.copyOf(ownerMismatches);
            poolMismatches       = List.copyOf(poolMismatches);
            crossContainerShares = List.copyOf(crossContainerShares);
        }

        /**
         * @return true iff the inspected graph has no illegal owner relationship
         */
        public boolean isValid() {
            return ownerMismatches.isEmpty()
                    && poolMismatches.isEmpty()
                    && crossContainerShares.isEmpty();
        }

        /**
         * Throw an IllegalStateException if the inspected graph contains illegal ownership.
         */
        public void assertValid() {
            if (!isValid()) {
                throw new IllegalStateException(message());
            }
        }

        /**
         * @return a multi-line validation summary
         */
        public String message() {
            return List.of(
                    section("owner-mismatches", ownerMismatches),
                    section("pool-mismatches", poolMismatches),
                    section("cross-container-shares", crossContainerShares))
                .stream()
                .filter(section -> !section.isEmpty())
                .collect(Collectors.joining("\n", "Invalid XVM runtime ownership\n", ""));
        }

        private static String section(String label, List<String> findings) {
            if (findings.isEmpty()) {
                return "";
            }

            return findings.stream()
                    .collect(Collectors.joining("\n  - ",
                            label + ": " + findings.size() + "\n  - ",
                            ""));
        }
    }

    private static final class Dumper {
        private final boolean forceLazy;
        private final boolean emitDump;

        private final StringBuilder out;

        private final Map<Container, String> containerNames = new IdentityHashMap<>();
        private final Map<Object, Occurrence> seen = new IdentityHashMap<>();

        /**
         * The container whose roots the walk currently descends from; values owned by this
         * container are its own lifetime state and never owner-mismatches, regardless of how
         * deep inside ancestor-attributed handles they sit.
         */
        private Container m_containerRoot;
        private final Set<Object> dumpedTemplateLazy =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Object> dumpedCompositionLazy =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Object> expandedValues =
                Collections.newSetFromMap(new IdentityHashMap<>());

        private final List<String> ownerMismatches = new ArrayList<>();
        private final List<String> poolMismatches  = new ArrayList<>();
        private final List<String> crossShares     = new ArrayList<>();

        Dumper(boolean forceLazy) {
            this(forceLazy, true);
        }

        Dumper(boolean forceLazy, boolean emitDump) {
            this.forceLazy = forceLazy;
            this.emitDump  = emitDump;
            this.out       = emitDump ? new StringBuilder() : null;
        }

        String dump(Container... containers) {
            scan(containers);
            return out.toString();
        }

        void scan(Container... containers) {
            requireNonNull(containers, "containers");

            if (emitDump) {
                out.append("XVM runtime ownership dump\n");
                out.append("mode.lazy=")
                        .append(forceLazy ? "force" : "computed-only")
                        .append('\n');
            }

            for (int i = 0; i < containers.length; i++) {
                Container container = requireNonNull(containers[i], "containers[" + i + "]");
                containerNames.put(container, "C" + i);
            }

            for (int i = 0; i < containers.length; i++) {
                dumpContainer(i, containers[i]);
            }

            if (emitDump) {
                dumpShares();
            }
        }

        void scanHandle(Container expected, String path, ObjectHandle handle) {
            requireNonNull(expected, "expected");
            m_containerRoot = expected;
            requireNonNull(path, "path");
            requireNonNull(handle, "handle");

            containerNames.put(expected, "C0");
            dumpValue(path, handle, expected, 0);
        }

        private void dumpContainer(int index, Container container) {
            m_containerRoot = container;
            String name = containerNames.get(container);

            if (emitDump) {
                out.append('\n')
                        .append(name)
                        .append(' ')
                        .append(describeContainer(container))
                        .append('\n');
            }

            record("container[" + index + "]", container, container);

            dumpRuntimeRegistry(container);
            dumpConstHeap(container);
            dumpNativeTemplates(container);
            // A main container can cache app-specific parameterized type keys
            // whose implementation template is the canonical native class
            // template, for example Array<Property<Point>> -> native xArray.
            dumpMap("templatesByType", readField(container, "f_mapTemplatesByType"),
                    container, 1, true);
            // Composition lazy state (field-name arrays, method-init structures) may resolve
            // canonical native-parent values - reflection lazily computes f_fieldNames with
            // string handles interned by the NativeContainer parent, the same currency the
            // constHeap walk below accepts. Still reject values from any other runtime owner.
            dumpMap("compositions", readField(container, "f_mapCompositions"), container, 1, true);
            dumpCollection("services", readField(container, "f_setServices"), container, 1);
        }

        private void dumpRuntimeRegistry(Container container) {
            if (!emitDump) {
                return;
            }

            Set<Container> containers = container.f_runtime.containers();
            line(1, "runtimeRegistry = contains=" + containers.contains(container)
                    + " size=" + containers.size());
        }

        private void dumpConstHeap(Container container) {
            ConstHeap heap = container.getConstHeap();
            if (emitDump) {
                out.append("  constHeap = ")
                        .append(identity(heap))
                        .append(" owner=explicit-parameter")
                        .append('\n');
            }

            // Constant handles may be canonical native-parent values, e.g. Char/Int/String/enum
            // handles. Those are legal only for this container's own NativeContainer parent.
            dumpMap("constHeap.entries", readField(heap, "f_mapConstants"), container, 2, true);
        }

        private void dumpNativeTemplates(Container container) {
            Object lazy = readField(container, "f_nativeTemplates");
            if (!(lazy instanceof Lazy.Owner<?, ?> ownerLazy)) {
                line(1, "nativeTemplates = " + describe(lazy, container));
                return;
            }

            boolean computed = ownerLazy.isComputed();
            line(1, "nativeTemplates = Lazy.Owner[" + (computed ? "computed" : "deferred") + "]");
            if (!computed && !forceLazy) {
                return;
            }

            NativeTemplates templates = withOwnerPool(container,
                    () -> ownerLazy.get(container, NativeTemplates.class));
            if (emitDump) {
                out.append("    value = ")
                        .append(describe(templates, container))
                        .append('\n');
            }
            record("nativeTemplates", templates, container);

            // A main container's NativeTemplates table may resolve canonical
            // core templates from its NativeContainer parent. That is the
            // normal Container.getTemplate(...) delegation model; still reject
            // native-template values from any other runtime owner.
            dumpLazyFields(templates, container, 2, true);
            dumpMap("nativeTemplateKeys", readField(templates, "f_mapTemplates"),
                    container, 2, true, templates);
        }

        private void dumpLazyFields(Object owner, Container expected, int indent) {
            dumpLazyFields(owner, expected, indent, false);
        }

        private void dumpLazyFields(Object owner, Container expected, int indent,
                                    boolean allowNativeOwner) {
            List<Field> fields = lazyFields(owner.getClass());
            if (fields.isEmpty()) {
                return;
            }

            line(indent, "lazyFields:");
            for (Field field : fields) {
                Object value = readField(owner, field);
                if (value instanceof Lazy<?> lazy) {
                    dumpLazy(field.getDeclaringClass().getSimpleName() + "." + field.getName(),
                            lazy, expected, indent + 1, allowNativeOwner);
                } else if (value instanceof Lazy.Owner<?, ?> lazy) {
                    dumpOwnerLazy(field.getDeclaringClass().getSimpleName() + "." + field.getName(),
                            lazy, lazyOwner(owner, field), expected, indent + 1, allowNativeOwner);
                }
            }
        }

        private void dumpLazy(String label, Lazy<?> lazy, Container expected, int indent) {
            dumpLazy(label, lazy, expected, indent, false);
        }

        private void dumpLazy(String label, Lazy<?> lazy, Container expected, int indent,
                              boolean allowNativeOwner) {
            boolean computed = lazy.isComputed();
            line(indent, label + " = Lazy[" + (computed ? "computed" : "deferred") + "]");

            if (computed || forceLazy) {
                dumpValue("value", withOwnerPool(expected, lazy::get), expected, indent + 1,
                        allowNativeOwner);
            }
        }

        private void dumpOwnerLazy(String label, Lazy.Owner<?, ?> lazy, Object owner,
                                   Container expected, int indent, boolean allowNativeOwner) {
            boolean computed = lazy.isComputed();
            line(indent, label + " = Lazy.Owner[" + (computed ? "computed" : "deferred") + "]");

            if (computed || forceLazy) {
                if (owner == null) {
                    line(indent + 1, "value = <unavailable: missing lazy owner>");
                    return;
                }

                Container ownerContainer = ownerOf(owner);
                Container scopedOwner    = Objects.requireNonNullElse(ownerContainer, expected);
                dumpValue("value", withOwnerPool(scopedOwner, () -> lazy.get(owner, Object.class)),
                        expected, indent + 1, allowNativeOwner);
            }
        }

        private void dumpMap(String label, Object value, Container expected, int indent) {
            dumpMap(label, value, expected, indent, false);
        }

        private void dumpMap(String label, Object value, Container expected, int indent,
                             boolean allowNativeOwner) {
            dumpMap(label, value, expected, indent, allowNativeOwner, null);
        }

        private void dumpMap(String label, Object value, Container expected, int indent,
                             boolean allowNativeOwner, Object lazyOwner) {
            if (!(value instanceof Map<?, ?> map)) {
                line(indent, label + " = " + describe(value, expected, allowNativeOwner));
                return;
            }

            line(indent, label + " size=" + map.size());
            map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .forEach(entry -> dumpMapEntry(entry, expected, indent + 1,
                            allowNativeOwner, lazyOwner));
        }

        private void dumpMapEntry(Map.Entry<?, ?> entry, Container expected, int indent) {
            dumpMapEntry(entry, expected, indent, false);
        }

        private void dumpMapEntry(Map.Entry<?, ?> entry, Container expected, int indent,
                                  boolean allowNativeOwner) {
            dumpMapEntry(entry, expected, indent, allowNativeOwner, null);
        }

        private void dumpMapEntry(Map.Entry<?, ?> entry, Container expected, int indent,
                                  boolean allowNativeOwner, Object lazyOwner) {
            Object key   = entry.getKey();
            Object value = entry.getValue();

            record("key " + key, key, expected, allowNativeOwner);
            if (value instanceof Lazy<?> lazy) {
                dumpLazy(String.valueOf(key), lazy, expected, indent, allowNativeOwner);
            } else if (value instanceof Lazy.Owner<?, ?> lazy) {
                dumpOwnerLazy(String.valueOf(key), lazy, lazyOwner, expected, indent,
                        allowNativeOwner);
            } else {
                dumpValue(String.valueOf(key), value, expected, indent, allowNativeOwner);
            }
        }

        private void dumpCollection(String label, Object value, Container expected, int indent) {
            if (!(value instanceof Iterable<?> iterable)) {
                line(indent, label + " = " + describe(value, expected));
                return;
            }

            line(indent, label + ':');
            int index = 0;
            for (Object element : iterable) {
                dumpValue("[" + index++ + "]", element, expected, indent + 1);
            }
            if (index == 0) {
                line(indent + 1, "(empty)");
            }
        }

        private void dumpValue(String label, Object value, Container expected, int indent) {
            dumpValue(label, value, expected, indent, false);
        }

        private void dumpValue(String label, Object value, Container expected, int indent,
                               boolean allowNativeOwner) {
            line(indent, label + " = " + describe(value, expected, allowNativeOwner));
            record(label, value, expected, allowNativeOwner);

            if (value == null || !expandedValues.add(value)) {
                return;
            }

            Container nestedExpected = nestedExpected(expected, value, allowNativeOwner);
            if (value instanceof ClassTemplate template && dumpedTemplateLazy.add(template)) {
                dumpLazyFields(template, nestedExpected, indent + 1, allowNativeOwner);
                return;
            }

            if (value instanceof TypeComposition composition &&
                    dumpedCompositionLazy.add(composition)) {
                dumpLazyFields(composition, nestedExpected, indent + 1, allowNativeOwner);
                return;
            }

            if (value instanceof Map<?, ?> map) {
                dumpMap("entries", map, nestedExpected, indent + 1, allowNativeOwner);
                return;
            }

            if (value != null && value.getClass().isArray()) {
                int count = Array.getLength(value);
                line(indent + 1, "arrayLength=" + count);
                for (int i = 0; i < count; i++) {
                    dumpValue("[" + i + "]", Array.get(value, i), nestedExpected,
                            indent + 2, allowNativeOwner);
                }
            }

            if (value instanceof ObjectHandle handle) {
                dumpHandleFields(handle, nestedExpected, indent + 1, allowNativeOwner);
            }
        }

        private void dumpHandleFields(ObjectHandle handle, Container expected, int indent,
                                      boolean allowNativeOwner) {
            if (handle instanceof GenericHandle generic) {
                dumpValue("handle.fields", generic.getFieldViewForDiagnostics(), expected,
                        indent, allowNativeOwner);

                ObjectHandle[] ahOverrides = generic.getFieldOverridesForDiagnostics();
                if (ahOverrides != null) {
                    dumpValue("handle.fieldOverrides", ahOverrides, expected, indent,
                            allowNativeOwner);
                }

                Container owner = safe(() -> (Container) readField(handle, "m_owner"));
                if (owner != null) {
                    line(indent, "handle.explicitOwner = " + describeContainer(owner));
                }
            }

            if (handle instanceof RefHandle) {
                Object referent = safe(() -> readField(handle, "m_hReferent"));
                if (referent instanceof ObjectHandle hReferent) {
                    dumpValue("ref.referentHolder", hReferent, expected, indent,
                            allowNativeOwner);
                }

                Object property = safe(() -> readField(handle, "m_idProp"));
                if (property instanceof Constant constant) {
                    dumpValue("ref.property", constant, expected, indent, allowNativeOwner);
                }
            }
        }

        private void record(String path, Object value, Container expected) {
            record(path, value, expected, false);
        }

        private void record(String path, Object value, Container expected, boolean allowNativeOwner) {
            if (value instanceof Constant constant) {
                recordConstantPool(path, constant, expected, allowNativeOwner);
            }

            if (!isOwnerScoped(value)) {
                return;
            }

            Container actual            = ownerOf(value);
            Container effectiveExpected = effectiveExpected(expected, actual, allowNativeOwner);
            if (actual != null && effectiveExpected != null && actual != effectiveExpected
                    && actual != m_containerRoot) {
                // a value owned by the WALK ROOT itself always satisfies ownership: its graph has
                // the root's lifetime even when it is nested inside an ancestor-attributed handle
                // (handles of canonical native classes get the NativeContainer's composition, so a
                // run-lifetime Path graph legally mixes native-attributed outers with run-owned
                // inners). The detector's teeth are unchanged where they matter: walking the
                // NATIVE root, a run-container-owned value is still a retention violation, and
                // any unrelated-container owner is still a cross-run share.
                ownerMismatches.add(path + " expected=" + containerName(effectiveExpected)
                        + " actual=" + containerName(actual)
                        + " object=" + identity(value));
            }

            Occurrence previous = seen.putIfAbsent(value,
                    new Occurrence(path, effectiveExpected, actual));
            if (previous != null && previous.expected != effectiveExpected
                    && !isLegitimateSharedCurrency(value, previous.expected, effectiveExpected)) {
                crossShares.add(previous.path + " and " + path
                        + " share " + identity(value)
                        + " previousExpected=" + containerName(previous.expected)
                        + " currentExpected=" + containerName(effectiveExpected)
                        + " actualOwner=" + containerName(actual));
            }
        }

        /**
         * Parent-flow constant currency is interned sharing by design:
         * {@code ConstHeap.getConstHandle} copies a parent's handle into descendant heaps
         * after an isShared check, and relocateConst deliberately moves canonical constants
         * toward ancestors - so one immutable handle (or an ownerless deferred marker such as
         * DeferredPropertyHandle) legitimately appears under MANY observers, including
         * SIBLING containers that both reached it through their common ancestor (the
         * connector-reuse regime made that ordinary: consecutive main containers share one
         * native plane). This matches the reachability sweep's policy exactly: immutable
         * handles are pass-through currency. The cross-container leak signal that must keep
         * flagging is MUTABLE state observed from more than one container - and it does,
         * regardless of how the observers are related.
         */
        private static boolean isLegitimateSharedCurrency(Object value, Container previous,
                                                          Container current) {
            return value instanceof ObjectHandle handle
                    && !isMutableSafe(handle);
        }

        private void recordConstantPool(String path, Constant constant, Container expected) {
            recordConstantPool(path, constant, expected, false);
        }

        private void recordConstantPool(String path, Constant constant, Container expected,
                                        boolean allowNativeOwner) {
            if (expected == null) {
                return;
            }

            ConstantPool actual = safe(() -> constant.getConstantPool());
            Container expectedPoolOwner = effectivePoolOwner(expected, actual, allowNativeOwner);
            if (actual != null && actual != expectedPoolOwner.getConstantPool()) {
                poolMismatches.add(path + " expected=" + containerName(expectedPoolOwner)
                        + " expectedPool=" + identity(expectedPoolOwner.getConstantPool())
                        + " actualPool=" + identity(actual)
                        + " object=" + identity(constant));
            }
        }

        private void dumpShares() {
            out.append("\nchecks:\n");
            appendFindings("owner-mismatches", ownerMismatches);
            appendFindings("pool-mismatches", poolMismatches);
            appendFindings("cross-container-shares", crossShares);
        }

        private void appendFindings(String label, List<String> findings) {
            out.append("  ").append(label).append(": ");
            if (findings.isEmpty()) {
                out.append("none\n");
                return;
            }

            out.append(findings.size())
                    .append('\n')
                    .append(findings.stream()
                            .collect(Collectors.joining("\n    - ", "    - ", "\n")));
        }

        private String describe(Object value, Container expected) {
            return describe(value, expected, false);
        }

        private String describe(Object value, Container expected, boolean allowNativeOwner) {
            if (value == null) {
                return "null";
            }

            StringBuilder description = new StringBuilder(identity(value));
            Container     owner       = ownerOf(value);
            Container     expectedOwner = effectiveExpected(expected, owner, allowNativeOwner);
            if (owner != null) {
                description.append(" owner=").append(containerName(owner));
                if (expectedOwner != null && owner != expectedOwner) {
                    description.append(" OWNER-MISMATCH expected=")
                            .append(containerName(expectedOwner));
                }
            }

            if (value instanceof Constant constant) {
                ConstantPool pool = safe(() -> constant.getConstantPool());
                Container expectedPoolOwner = expected == null || pool == null
                        ? expected
                        : effectivePoolOwner(expected, pool, allowNativeOwner);
                description.append(" pool=").append(pool == null ? "null" : identity(pool));
                if (expectedPoolOwner != null && pool != null
                        && pool != expectedPoolOwner.getConstantPool()) {
                    description.append(" POOL-MISMATCH expected=")
                            .append(identity(expectedPoolOwner.getConstantPool()));
                }
            }

            if (value instanceof JavaLong hLong) {
                description.append(" value=").append(hLong.getValue());
            }

            if (value instanceof ObjectHandle handle) {
                TypeConstant type = safe(handle::getType);
                if (type != null) {
                    description.append(" type=").append(type.getValueString());
                }
            }

            return description.toString();
        }

        private static Container effectiveExpected(Container expected, Container actual,
                                                   boolean allowNativeOwner) {
            if (expected == null || actual == null || !allowNativeOwner) {
                return expected;
            }

            NativeContainer nativeOwner = safe(() -> expected.getNativeContainer());
            return actual == nativeOwner ? nativeOwner : expected;
        }

        private static Container effectivePoolOwner(Container expected, ConstantPool actual,
                                                    boolean allowNativeOwner) {
            if (!allowNativeOwner || actual == expected.getConstantPool()) {
                return expected;
            }

            NativeContainer nativeOwner = safe(() -> expected.getNativeContainer());
            return nativeOwner != null && actual == nativeOwner.getConstantPool()
                    ? nativeOwner
                    : expected;
        }

        private static Container nestedExpected(Container expected, Object value,
                                                boolean allowNativeOwner) {
            if (!allowNativeOwner || !isOwnerScoped(value)) {
                return expected;
            }

            Container actual = ownerOf(value);
            return actual == null ? expected : effectiveExpected(expected, actual, true);
        }

        private static Container ownerOf(Object value) {
            try {
                if (value instanceof ClassTemplate template) {
                    return template.f_container;
                }
                if (value instanceof TypeComposition composition) {
                    return composition.getContainer();
                }
                if (value instanceof ObjectHandle handle) {
                    TypeComposition composition = handle.getComposition();
                    return composition == null ? null : composition.getContainer();
                }
                if (value instanceof ServiceContext context) {
                    return context.f_container;
                }
                if (value instanceof NativeTemplates templates) {
                    return (Container) readField(templates, "f_container");
                }
            } catch (RuntimeException ignore) {
                return null;
            }

            return null;
        }

        private static boolean isOwnerScoped(Object value) {
            return value instanceof ClassTemplate
                    || value instanceof TypeComposition
                    || value instanceof ObjectHandle
                    || value instanceof ServiceContext
                    || value instanceof NativeTemplates;
        }

        private static Object lazyOwner(Object owner, Field field) {
            if (owner instanceof ClassComposition
                    && Lazy.Owner.class.isAssignableFrom(field.getType())) {
                return readField(owner, "f_clzInception");
            }
            return owner;
        }

        private String describeContainer(Container container) {
            String module = safe(() -> String.valueOf(container.getModule()));
            return identity(container) + " module=" + module
                    + " pool=" + identity(container.getConstantPool());
        }

        private String containerName(Container container) {
            if (container == null) {
                return "unknown";
            }
            return containerNames.computeIfAbsent(container,
                    key -> "external@" + Integer.toHexString(System.identityHashCode(key)));
        }

        private static String identity(Object value) {
            if (value == null) {
                return "null";
            }
            return value.getClass().getSimpleName()
                    + '@'
                    + Integer.toHexString(System.identityHashCode(value));
        }

        private void line(int indent, String text) {
            if (emitDump) {
                out.append("  ".repeat(Math.max(0, indent))).append(text).append('\n');
            }
        }

        private static List<Field> lazyFields(Class<?> clz) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> current = clz; current != null; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())
                            && (Lazy.class.isAssignableFrom(field.getType())
                                || Lazy.Owner.class.isAssignableFrom(field.getType()))) {
                        field.setAccessible(true);
                        fields.add(field);
                    }
                }
            }

            fields.sort(Comparator
                    .comparing((Field field) -> field.getDeclaringClass().getName())
                    .thenComparing(Field::getName));
            return fields;
        }

        private static Object readField(Object target, String name) {
            Class<?> clz = target.getClass();
            while (clz != null) {
                try {
                    Field field = clz.getDeclaredField(name);
                    return readField(target, field);
                } catch (NoSuchFieldException ignore) {
                    clz = clz.getSuperclass();
                }
            }

            throw new IllegalArgumentException(
                    target.getClass().getName() + " does not declare " + name);
        }

        private static Object readField(Object target, Field field) {
            try {
                field.setAccessible(true);
                return field.get(target);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        private static <T> T withOwnerPool(Container owner, SupplierWithException<T> supplier) {
            return supplier.get();
        }

        private static <T> T safe(SupplierWithException<T> supplier) {
            try {
                return supplier.get();
            } catch (RuntimeException e) {
                return null;
            }
        }

        private Validation validation() {
            return new Validation(ownerMismatches, poolMismatches, crossShares);
        }
    }

    private record Occurrence(String path, Container expected, Container actual) {}

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }
}
