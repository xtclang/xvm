package org.xvm.runtime;


import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.CompletableFuture;

import java.util.function.Supplier;

import org.xvm.asm.ConstantPool;

import org.xvm.runtime.ObjectHandle.DeferredArrayHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.annotations.xFuture;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xTuple;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.reflect.xClass;

import org.xvm.runtime.template.text.xChar;
import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTType;


/**
 * Builds a population of LIVE {@link ObjectHandle}s over a real container, for
 * {@link DisplayPurityCensusTest} to render.
 *
 * <p>Building a handle is allowed to intern, allocate and force whatever it likes - that is normal
 * program behaviour. The census only requires that RENDERING one afterwards does not. Each entry is
 * built inside a guard, because a factory that fails here must not silently shrink the population:
 * the census's coverage assertion then reports the class as uncovered and fails.</p>
 */
final class HandlePopulation {
    private HandlePopulation() {
    }

    static List<Object> build(Container container) {
        var list = new ArrayList<>();
        var pool = container.getConstantPool();

        try (var ignore = ConstantPool.withPool(pool)) {
            add(list, () -> xString.makeHandle("hello"));
            add(list, () -> xString.makeHandle("with \"quotes\" and \\ backslash"));
            add(list, () -> xInt64.makeHandle(42L));
            add(list, () -> xInt64.makeHandle(Long.MIN_VALUE));
            add(list, () -> xChar.makeHandle('x'));
            add(list, () -> xBoolean.makeHandle(true));
            add(list, () -> xBoolean.makeHandle(false));
            add(list, () -> ObjectHandle.DEFAULT);
            add(list, () -> new ObjectHandle.ConstantHandle(pool.ensureStringConstant("konst")));
            add(list, () -> new ObjectHandle.ConstantHandle(pool.typeInt64()));

            // reflection handles - these are the ones whose toString() used to freeze/intern
            add(list, () -> xRTType.makeHandle(container, pool.typeInt64(), true));
            add(list, () -> xRTType.makeHandle(container, pool.typeString(), false));
            add(list, () -> xRTType.makeHandle(container,
                    pool.ensureParameterizedTypeConstant(pool.typeList(), pool.typeInt64()), true));
            add(list, () -> xClass.INSTANCE.createStruct(null, container.resolveClass(
                    pool.ensureParameterizedTypeConstant(pool.typeClass(), pool.typeInt64()))));

            // aggregates
            add(list, () -> xTuple.makeImmutableHandle(xTuple.INSTANCE.getCanonicalClass(),
                            xString.makeHandle("a"), xInt64.makeHandle(1L)));
            add(list, () -> xArray.makeStringArrayHandle(new StringHandle[] {
                    xString.makeHandle("a"), xString.makeHandle("b")}));

            // a deferred array: its toString() interned an ImmutableTypeConstant on EVERY render
            add(list, () -> new DeferredArrayHandle(
                    xArray.INSTANCE.getCanonicalClass(),
                    new ObjectHandle[] {xInt64.makeHandle(1L)}));

            // exception handles: Java itself renders these while printing a stack trace
            add(list, () -> xException.makeHandle(null, "boom"));
            add(list, () -> xException.makeHandle(null, "boom").getException());

            // a future that has NOT completed - rendering must not join it
            add(list, () -> xFuture.makeHandle(new CompletableFuture<>()));
            add(list, () -> xFuture.makeHandle(CompletableFuture.completedFuture(
                    xInt64.makeHandle(7L))));
            add(list, () -> xFuture.makeHandle(
                    CompletableFuture.failedFuture(new IllegalStateException("nope"))));

            // compositions and templates, which handle rendering delegates into
            add(list, () -> xString.INSTANCE.getCanonicalClass());
            add(list, () -> xInt64.INSTANCE.getCanonicalClass());
            add(list, () -> xString.INSTANCE);
            add(list, () -> container);
        }

        return list;
    }

    /**
     * Add one handle, tolerating a factory that cannot run in this fixture. Nothing is hidden: a
     * miss shows up as an uncovered class in the census's coverage assertion.
     */
    private static void add(List<Object> list, Supplier<Object> supplier) {
        try {
            Object o = supplier.get();
            if (o != null) {
                list.add(o);
            }
        } catch (Throwable ignore) {
            // the census reports the resulting coverage gap
        }
    }

    /** @return the generic handles in the population, for tests that need field-bearing handles */
    static List<GenericHandle> genericHandles(List<Object> population) {
        return population.stream()
                .filter(GenericHandle.class::isInstance)
                .map(GenericHandle.class::cast)
                .toList();
    }
}
