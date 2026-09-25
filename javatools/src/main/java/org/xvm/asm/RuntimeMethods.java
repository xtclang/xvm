package org.xvm.asm;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import java.util.function.Supplier;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

/**
 * Generated delegation executables for one runtime descriptor owner.
 *
 * <p>This table retains prepared bodies across semantic clears. Services key their own decoded
 * Ops, frame layouts and initialization completion by these exact method objects. Keys contain
 * the receiver, host signature, delegated declaration and target property, all canonicalized by
 * the exact descriptor owner before lookup. Native bindings are fixed by the prepared image
 * and its container.
 *
 * <p>Build attempts are private. Only a fully assembled method is published, and failed attempts
 * leave no entry. Concurrent callers may assemble independent candidates, then use the same
 * completed winner. No table lock is held while metadata or descriptors are calculated. This
 * does not make later execution state safe for arbitrary concurrent mutation.
 */
public final class RuntimeMethods {
    public RuntimeMethods(ConstantPool pool) {
        if (pool.hasSerializedIndices()) {
            throw new IllegalArgumentException("Runtime executables require a descriptor owner");
        }
        this.pool = pool;
    }

    public MethodStructure ensureMethodDelegation(TypeInfo receiver, MethodConstant host,
                                                MethodStructure declaration, PropertyConstant delegate) {
        var key = key(receiver.getType(), host, declaration.getIdentityConstant(), delegate);
        return ensure(key, () -> ((ClassStructure) key.host().getNamespace().getComponent())
                .ensureMethodDelegation(pool, declaration, key.delegate().getName()));
    }

    public MethodStructure ensurePropertyDelegation(TypeInfo receiver, MethodConstant accessor,
                                                  PropertyStructure declaration, PropertyStructure delegate) {
        var key = key(receiver.getType(), accessor, declaration.getIdentityConstant(),
                delegate.getIdentityConstant());
        return ensure(key, () -> delegate.getContainingClass().ensurePropertyDelegation(pool,
                declaration, delegate, key.host().getSignature()));
    }

    private Key key(TypeConstant receiver, MethodConstant host, Constant declaration,
                    PropertyConstant delegate) {
        return new Key(pool.register(receiver), pool.register(host), pool.register(declaration),
                pool.register(delegate));
    }

    /** Package-private construction boundary for deterministic publication/failure tests. */
    MethodStructure ensure(Key key, Supplier<MethodStructure> build) {
        // Validate even on a hit: structural equality never permits foreign-context operands.
        key = key(key.receiver(), key.host(), key.declaration(), key.delegate());
        var existing = methods.get(key);
        if (existing != null) {
            return existing;
        }
        var candidate = build.get();
        if (!(candidate instanceof RuntimeMethodStructure)
                || candidate.getConstantPool() != pool || candidate.getLocalConstants() == null) {
            throw new IllegalStateException("Generated method must be assembled by its owner");
        }
        var winner = methods.putIfAbsent(key, candidate);
        return winner == null ? candidate : winner;
    }

    record Key(TypeConstant receiver, MethodConstant host, Constant declaration,
               PropertyConstant delegate) {}

    private final ConstantPool pool;
    private final ConcurrentMap<Key, MethodStructure> methods = new ConcurrentHashMap<>();
}
