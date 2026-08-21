package org.xvm.asm;


import java.lang.ref.Reference;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;


/**
 * Diagnostic guard for {@link Constant#adoptedBy(ConstantPool)}.
 *
 * <p>Constant adoption moves logical constant identity into a target pool. The old default
 * implementation uses {@link Object#clone()}, so this validator looks specifically for owner-local
 * helper objects that were shallow-copied by reference. Logical child constants are allowed to be
 * shared at this point because {@link ConstantPool#register(Constant)} recursively adopts them into
 * the target pool after the outer constant has been constructed.</p>
 */
final class ConstantAdoptionValidator {
    /**
     * Enable fail-fast validation at constant-pool adoption boundaries.
     */
    static final String VALIDATE_PROPERTY = "xvm.asm.validateConstantAdoption";

    private static final Set<String> FORBIDDEN_EXACT_TYPES = Set.of(
            "org.xvm.asm.ConstantPool",
            "org.xvm.runtime.Container",
            "org.xvm.runtime.Fiber",
            "org.xvm.runtime.Frame",
            "org.xvm.runtime.ServiceContext");

    private static final Set<String> FORBIDDEN_PACKAGES = Set.of(
            "java.util.concurrent.atomic.",
            "java.util.concurrent.locks.");

    private ConstantAdoptionValidator() {
    }

    /**
     * Validate an adoption boundary when {@link #VALIDATE_PROPERTY} is enabled.
     *
     * @param source   the source constant
     * @param adopted  the adopted target-pool constant
     */
    static void assertValidIfEnabled(Constant source, Constant adopted) {
        if (Boolean.getBoolean(VALIDATE_PROPERTY)) {
            validate(source, adopted).assertValid();
        }
    }

    /**
     * Validate an adoption boundary.
     *
     * @param source   the source constant
     * @param adopted  the adopted target-pool constant
     *
     * @return validation findings
     */
    static Validation validate(Constant source, Constant adopted) {
        requireNonNull(source, "source");
        requireNonNull(adopted, "adopted");

        List<String> findings = new ArrayList<>();
        for (Field field : sharedFieldCandidates(source.getClass(), adopted.getClass())) {
            Object sourceValue  = readField(field, source);
            Object adoptedValue = readField(field, adopted);

            if (sourceValue != null && sourceValue == adoptedValue
                    && isForbiddenSharedReference(sourceValue)) {
                findings.add(describe(field, sourceValue));
            }
        }

        return new Validation(findings);
    }

    /**
     * Structured adoption validation result.
     *
     * @param sharedReferences  forbidden references shared by the source and adopted constants
     */
    record Validation(List<String> sharedReferences) {
        Validation {
            sharedReferences = List.copyOf(sharedReferences);
        }

        /**
         * @return true iff adoption copied no forbidden helper/runtime references
         */
        boolean isValid() {
            return sharedReferences.isEmpty();
        }

        /**
         * Throw an IllegalStateException if the adoption copied illegal helper/runtime state.
         */
        void assertValid() {
            if (!isValid()) {
                throw new IllegalStateException(message());
            }
        }

        /**
         * @return a multi-line validation summary
         */
        String message() {
            return sharedReferences.stream()
                    .collect(Collectors.joining("\n  - ",
                            "Invalid XVM constant adoption\nshared forbidden references: "
                                    + sharedReferences.size() + "\n  - ",
                            ""));
        }
    }

    private static List<Field> sharedFieldCandidates(Class<?> sourceType, Class<?> adoptedType) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = sourceType; type != null; type = type.getSuperclass()) {
            if (!type.isAssignableFrom(adoptedType)) {
                continue;
            }

            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && !field.getType().isPrimitive()) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static boolean isForbiddenSharedReference(Object value) {
        if (value instanceof Constant) {
            return false;
        }

        if (value instanceof ConstantPool
                || value instanceof ThreadLocal<?>
                || value instanceof Reference<?>) {
            return true;
        }

        Class<?> type = value.getClass();
        if (nameMatches(type, FORBIDDEN_EXACT_TYPES::contains)
                || nameMatches(type, ConstantAdoptionValidator::isForbiddenPackage)) {
            return true;
        }

        return isMutableCollection(value);
    }

    private static boolean isMutableCollection(Object value) {
        if (!(value instanceof Map<?, ?>) && !(value instanceof Collection<?>)) {
            return false;
        }

        String name = value.getClass().getName();
        return !name.startsWith("java.util.ImmutableCollections$")
                && !name.startsWith("java.util.Collections$Empty")
                && !name.startsWith("java.util.Collections$Singleton")
                && !name.startsWith("java.util.Collections$Unmodifiable");
    }

    private static boolean nameMatches(Class<?> type, Predicate<String> predicate) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (predicate.test(current.getName())) {
                return true;
            }

            for (Class<?> iface : current.getInterfaces()) {
                if (nameMatches(iface, predicate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isForbiddenPackage(String name) {
        return FORBIDDEN_PACKAGES.stream().anyMatch(name::startsWith);
    }

    private static Object readField(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String describe(Field field, Object value) {
        return field.getDeclaringClass().getSimpleName()
                + '.'
                + field.getName()
                + " shared "
                + value.getClass().getName()
                + '@'
                + Integer.toHexString(System.identityHashCode(value));
    }
}
