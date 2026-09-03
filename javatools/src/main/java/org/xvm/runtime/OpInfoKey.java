package org.xvm.runtime;


import java.util.Objects;

import org.xvm.asm.Op;


/**
 * A typed key into a service's per-{@link Op} info cache.
 *
 * <p>The cache stores several unrelated kinds of value against one op - a call chain, a
 * composition, a target type, a constructor - and the {@code Category} enum names which is which.
 * Naming it is not the same as proving it: with an {@code Object}-valued cache,
 * {@code setOpInfo(op, Category.TargetClass, constructor)} followed by a read of the same category
 * as an {@code IdentityConstant} compiles cleanly, and fails much later as a
 * {@link ClassCastException} on a hot path, in whichever service happens to reuse the entry.</p>
 *
 * <p>Pairing the category with the value's {@link Class} closes that. The key is declared once
 * beside the category it belongs to, so the association is written down in exactly one place, and
 * every read goes through {@link Class#cast} - a real check at the cache boundary rather than an
 * unchecked cast at each of the call sites. The keys are the only way to reach the cache, so a
 * mismatched pairing has nowhere to enter.</p>
 *
 * @param category  the op-specific category this key caches under
 * @param type      the type of value stored under it
 *
 * @param <T> the cached value's type
 */
public record OpInfoKey<T>(Enum<?> category, Class<T> type) {
    public OpInfoKey {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(type, "type");
    }

    /**
     * @param category  the op-specific category
     * @param type      the type of value cached under it
     *
     * @return a key binding that category to that type
     */
    public static <T> OpInfoKey<T> of(Enum<?> category, Class<T> type) {
        return new OpInfoKey<>(category, type);
    }
}
