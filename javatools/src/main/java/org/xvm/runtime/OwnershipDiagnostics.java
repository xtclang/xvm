package org.xvm.runtime;


import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

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
        return new Dumper(forceLazy).dump(containers);
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
        Dumper dumper = new Dumper(forceLazy, false);
        dumper.scan(containers);
        return dumper.validation();
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
        private final Set<Object> dumpedTemplateLazy =
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
            requireNonNull(path, "path");
            requireNonNull(handle, "handle");

            containerNames.put(expected, "C0");
            dumpValue(path, handle, expected, 0);
        }

        private void dumpContainer(int index, Container container) {
            String name = containerNames.get(container);

            if (emitDump) {
                out.append('\n')
                        .append(name)
                        .append(' ')
                        .append(describeContainer(container))
                        .append('\n');
            }

            record("container[" + index + "]", container, container);

            dumpConstHeap(container);
            dumpNativeTemplates(container);
            // A main container can cache app-specific parameterized type keys
            // whose implementation template is the canonical native class
            // template, for example Array<Property<Point>> -> native xArray.
            dumpMap("templatesByType", readField(container, "f_mapTemplatesByType"),
                    container, 1, true);
            dumpMap("compositions", readField(container, "f_mapCompositions"), container, 1);
            dumpCollection("services", readField(container, "f_setServices"), container, 1);
        }

        private void dumpConstHeap(Container container) {
            Object heap = readField(container, "f_heap");
            if (emitDump) {
                out.append("  constHeap = ")
                        .append(identity(heap))
                        .append('\n');
            }

            // Constant handles may be canonical native-parent values, e.g. Char/Int/String/enum
            // handles. Those are legal only for this container's own NativeContainer parent.
            dumpMap("constHeap.entries", readField(heap, "f_mapConstants"), container, 2, true);
        }

        private void dumpNativeTemplates(Container container) {
            NativeTemplates templates = container.nativeTemplates();

            if (emitDump) {
                out.append("  nativeTemplates = ")
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
                            lazy, owner, expected, indent + 1, allowNativeOwner);
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
                dumpValue("value", lazy.get(), expected, indent + 1, allowNativeOwner);
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

                dumpValue("value", getOwnerLazy(lazy, owner), expected, indent + 1,
                        allowNativeOwner);
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
            if (handle instanceof GenericHandle) {
                Object fields = safe(() -> readField(handle, "m_aFields"));
                if (fields instanceof ObjectHandle[] ahFields) {
                    dumpValue("handle.fields", ahFields, expected, indent, allowNativeOwner);
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
            if (actual != null && effectiveExpected != null && actual != effectiveExpected) {
                ownerMismatches.add(path + " expected=" + containerName(effectiveExpected)
                        + " actual=" + containerName(actual)
                        + " object=" + identity(value));
            }

            Occurrence previous = seen.putIfAbsent(value,
                    new Occurrence(path, effectiveExpected, actual));
            if (previous != null && previous.expected != effectiveExpected) {
                crossShares.add(previous.path + " and " + path
                        + " share " + identity(value)
                        + " previousExpected=" + containerName(previous.expected)
                        + " currentExpected=" + containerName(effectiveExpected)
                        + " actualOwner=" + containerName(actual));
            }
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

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static Object getOwnerLazy(Lazy.Owner lazy, Object owner) {
            return lazy.get(owner);
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
