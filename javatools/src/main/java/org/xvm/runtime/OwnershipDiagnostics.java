package org.xvm.runtime;


import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.util.Lazy;

import static java.util.Objects.requireNonNull;


/**
 * Read-only diagnostics for inspecting container-owned runtime state.
 *
 * <p>The default dump reports only already-computed lazy values. Use
 * {@link #dump(boolean, Container...)} with {@code forceLazy=true} only when
 * intentional cache warmup is acceptable, because that mode calls
 * {@link Lazy#get()} on deferred cells.</p>
 */
public final class OwnershipDiagnostics {
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


    // ----- dumper -------------------------------------------------------------------------------

    private static final class Dumper {
        Dumper(boolean forceLazy) {
            f_forceLazy = forceLazy;
        }

        String dump(Container... containers) {
            requireNonNull(containers, "containers");

            out.append("XVM runtime ownership dump\n");
            out.append("mode.lazy=").append(f_forceLazy ? "force" : "computed-only").append('\n');

            for (int i = 0; i < containers.length; i++) {
                Container container = requireNonNull(containers[i], "containers[" + i + "]");
                f_containerNames.put(container, "C" + i);
            }

            for (int i = 0; i < containers.length; i++) {
                dumpContainer(i, containers[i]);
            }

            dumpShares();
            return out.toString();
        }

        private void dumpContainer(int index, Container container) {
            String name = f_containerNames.get(container);

            out.append('\n')
                    .append(name)
                    .append(' ')
                    .append(describeContainer(container))
                    .append('\n');

            record("container[" + index + "]", container, container);

            dumpNativeTemplates(container);
            dumpMap("templatesByType", readField(container, "f_mapTemplatesByType"), container, 1);
            dumpMap("compositions", readField(container, "f_mapCompositions"), container, 1);
            dumpCollection("services", readField(container, "f_setServices"), container, 1);
        }

        private void dumpNativeTemplates(Container container) {
            NativeTemplates templates = container.nativeTemplates();

            out.append("  nativeTemplates = ")
                    .append(describe(templates, container))
                    .append('\n');
            record("nativeTemplates", templates, container);

            dumpLazyFields(templates, container, 2);
            dumpMap("nativeTemplateKeys", readField(templates, "f_mapTemplates"), container, 2);
        }

        private void dumpLazyFields(Object owner, Container expected, int indent) {
            List<Field> fields = lazyFields(owner.getClass());
            if (fields.isEmpty()) {
                return;
            }

            line(indent, "lazyFields:");
            for (Field field : fields) {
                Object value = readField(owner, field);
                if (value instanceof Lazy<?> lazy) {
                    dumpLazy(field.getDeclaringClass().getSimpleName() + "." + field.getName(),
                            lazy, expected, indent + 1);
                }
            }
        }

        private void dumpLazy(String label, Lazy<?> lazy, Container expected, int indent) {
            boolean computed = lazy.isComputed();
            line(indent, label + " = Lazy[" + (computed ? "computed" : "deferred") + "]");

            if (computed || f_forceLazy) {
                dumpValue("value", lazy.get(), expected, indent + 1);
            }
        }

        private void dumpMap(String label, Object value, Container expected, int indent) {
            if (!(value instanceof Map<?, ?> map)) {
                line(indent, label + " = " + describe(value, expected));
                return;
            }

            line(indent, label + " size=" + map.size());
            map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .forEach(entry -> dumpMapEntry(entry, expected, indent + 1));
        }

        private void dumpMapEntry(Map.Entry<?, ?> entry, Container expected, int indent) {
            Object key   = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Lazy<?> lazy) {
                dumpLazy(String.valueOf(key), lazy, expected, indent);
            } else {
                dumpValue(String.valueOf(key), value, expected, indent);
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
            line(indent, label + " = " + describe(value, expected));
            record(label, value, expected);

            if (value instanceof ClassTemplate template && !f_dumpedTemplateLazy.containsKey(template)) {
                f_dumpedTemplateLazy.put(template, Boolean.TRUE);
                dumpLazyFields(template, expected, indent + 1);
                return;
            }

            if (value instanceof Map<?, ?> map) {
                dumpMap("entries", map, expected, indent + 1);
                return;
            }

            if (value != null && value.getClass().isArray()) {
                int count = Array.getLength(value);
                line(indent + 1, "arrayLength=" + count);
                for (int i = 0; i < count; i++) {
                    dumpValue("[" + i + "]", Array.get(value, i), expected, indent + 2);
                }
            }
        }

        private void record(String path, Object value, Container expected) {
            if (!isOwnerScoped(value)) {
                return;
            }

            Container actual = ownerOf(value);
            if (actual != null && expected != null && actual != expected) {
                f_ownerMismatches.add(path + " expected=" + containerName(expected)
                        + " actual=" + containerName(actual)
                        + " object=" + identity(value));
            }

            Occurrence previous = f_seen.putIfAbsent(value, new Occurrence(path, expected, actual));
            if (previous != null && previous.expected != expected) {
                f_crossShares.add(previous.path + " and " + path
                        + " share " + identity(value)
                        + " previousExpected=" + containerName(previous.expected)
                        + " currentExpected=" + containerName(expected)
                        + " actualOwner=" + containerName(actual));
            }
        }

        private void dumpShares() {
            out.append("\nchecks:\n");
            appendFindings("owner-mismatches", f_ownerMismatches);
            appendFindings("cross-container-shares", f_crossShares);
        }

        private void appendFindings(String label, List<String> findings) {
            out.append("  ").append(label).append(": ");
            if (findings.isEmpty()) {
                out.append("none\n");
                return;
            }

            out.append(findings.size()).append('\n');
            for (String finding : findings) {
                out.append("    - ").append(finding).append('\n');
            }
        }

        private String describe(Object value, Container expected) {
            if (value == null) {
                return "null";
            }

            StringBuilder description = new StringBuilder(identity(value));
            Container     owner       = ownerOf(value);
            if (owner != null) {
                description.append(" owner=").append(containerName(owner));
                if (expected != null && owner != expected) {
                    description.append(" OWNER-MISMATCH expected=").append(containerName(expected));
                }
            }

            if (value instanceof Constant constant) {
                ConstantPool pool = safe(() -> constant.getConstantPool());
                description.append(" pool=").append(pool == null ? "null" : identity(pool));
                if (expected != null && pool != null && pool != expected.getConstantPool()) {
                    description.append(" POOL-MISMATCH expected=")
                            .append(identity(expected.getConstantPool()));
                }
            }

            if (value instanceof JavaLong hLong) {
                description.append(" value=").append(hLong.getValue());
            }

            return description.toString();
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
            return f_containerNames.computeIfAbsent(container,
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
            out.append("  ".repeat(Math.max(0, indent))).append(text).append('\n');
        }

        private static List<Field> lazyFields(Class<?> clz) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> current = clz; current != null; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())
                            && Lazy.class.isAssignableFrom(field.getType())) {
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

        private static <T> T safe(SupplierWithException<T> supplier) {
            try {
                return supplier.get();
            } catch (RuntimeException e) {
                return null;
            }
        }

        private final boolean f_forceLazy;

        private final StringBuilder out = new StringBuilder();

        private final IdentityHashMap<Container, String> f_containerNames = new IdentityHashMap<>();
        private final IdentityHashMap<Object, Occurrence> f_seen = new IdentityHashMap<>();
        private final IdentityHashMap<Object, Boolean> f_dumpedTemplateLazy = new IdentityHashMap<>();

        private final List<String> f_ownerMismatches = new ArrayList<>();
        private final List<String> f_crossShares     = new ArrayList<>();
    }

    private record Occurrence(String path, Container expected, Container actual) {}

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }
}
