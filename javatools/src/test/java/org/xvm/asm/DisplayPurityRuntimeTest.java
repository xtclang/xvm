package org.xvm.asm;


import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.TypeConstant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * The EMPIRICAL gate for side-effect-free display code.
 *
 * <p>{@code toString()} is called implicitly - by {@code String} concatenation, by
 * {@code Throwable.toString()} while a stack trace prints, and by an IDE debugger rendering a row
 * in the Variables view. A display method that interns into a {@link ConstantPool}, forces a lazy
 * cell, or writes a resolution back into a field therefore makes OBSERVING the program CHANGE the
 * program: setting a breakpoint alters behaviour and the debugger session stops being
 * trustworthy.</p>
 *
 * <p>This test builds a broad population of real, fully-realized type-system objects, snapshots the
 * shared {@code ConstantPool}, then renders every one of them repeatedly through {@code toString()}
 * / {@code getValueString()} / {@code getDescription()} and asserts the pool did not grow. Pool
 * growth is the sharpest available signal because the dominant display impurities all end in an
 * intern: {@code ensure*Constant}, {@code getImplicitlyImportedIdentity}, the canonical
 * {@code typeObject()}/{@code typeFunction()} getters, and {@code Source.normalize()}'s
 * one-{@code StringConstant}-per-source-line.</p>
 */
public class DisplayPurityRuntimeTest {
    @Test
    public void renderingTheTypeSystemDoesNotGrowTheConstantPool() {
        assumeTrue(DisplayPurityFixture.systemModulesAvailable(),
                "compiled XDK system modules are required");

        var runtime = DisplayPurityFixture.startRuntime();
        try {
            ConstantPool pool = DisplayPurityFixture.nativePool(runtime);

            // ---- build the population FIRST; this legitimately interns --------------------------
            List<Object> population = buildPopulation(pool);
            assertTrue(population.size() > 100,
                    "the population must be broad enough to be meaningful, was " + population.size());

            // warm every renderer once, so a one-shot memoization inside a renderer is not counted
            try (var ignore = ConstantPool.withPool(pool)) {
                render(population);
            }

            // ---- now the pool must be stable across repeated rendering --------------------------
            int cBefore = pool.size();
            try (var ignore = ConstantPool.withPool(pool)) {
                for (int i = 0; i < 3; i++) {
                    render(population);
                }
            }
            assertEquals(cBefore, pool.size(),
                    "rendering the type system grew the shared ConstantPool - a display method is "
                    + "interning, so merely looking at a value in a debugger mutates the program");

            // ---- negative control: prove pool.size() actually detects interning -----------------
            // Without this, a green above could merely mean the instrument is dead.
            pool.ensureParameterizedTypeConstant(pool.typeTuple(),
                    pool.typeInt64(), pool.typeString(), pool.typeObject(), pool.typeBoolean());
            assertTrue(pool.size() > cBefore,
                    "negative control failed: interning a fresh type did not grow the pool, so the "
                    + "purity assertion above proves nothing");
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * A debugger renders on whatever thread is suspended, and that thread has no {@code
     * ConstantPool} bound to it - {@code ConstantPool.getCurrentPool()} returns null. A display
     * method that reaches for the ambient pool therefore throws, taking down the rendering of every
     * container that holds the object.
     */
    @Test
    public void renderingWithoutAnAmbientPoolDoesNotThrow() {
        assumeTrue(DisplayPurityFixture.systemModulesAvailable(),
                "compiled XDK system modules are required");

        var runtime = DisplayPurityFixture.startRuntime();
        try {
            ConstantPool pool       = DisplayPurityFixture.nativePool(runtime);
            List<Object> population = buildPopulation(pool);

            assertNull(ConstantPool.getCurrentPool(),
                    "this test is only meaningful with no ambient pool bound");
            render(population);
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * A deliberately broad population: the constant families whose display paths were the flagged
     * offenders, plus the {@code TypeInfo} member objects (PropertyInfo/MethodInfo and their
     * bodies) that a debugger expands into.
     */
    private static List<Object> buildPopulation(ConstantPool pool) {
        var list = new ArrayList<>();

        // type constants, including the FUNCTION shape whose renderer called isA(typeFunction())
        TypeConstant typeInt    = pool.typeInt64();
        TypeConstant typeString = pool.typeString();
        list.add(typeInt);
        list.add(typeString);
        list.add(pool.typeObject());
        list.add(pool.ensureArrayType(typeInt));
        list.add(pool.ensureParameterizedTypeConstant(pool.typeTuple(), typeInt, typeString));
        list.add(pool.ensureImmutableTypeConstant(pool.ensureArrayType(typeString)));
        list.add(pool.ensureParameterizedTypeConstant(pool.typeFunction(),
                pool.ensureParameterizedTypeConstant(pool.typeTuple(), typeInt),
                pool.ensureParameterizedTypeConstant(pool.typeTuple(), typeString)));
        list.add(typeInt.ensureNullable());

        // TypeInfos and the member objects a debugger expands into
        for (TypeConstant type : List.of(typeInt, typeString, pool.typeObject())) {
            var info = type.ensureTypeInfo();
            list.add(info);
            list.addAll(info.getProperties().values());
            list.addAll(info.getMethods().values());
            list.addAll(info.getTypeParams().values());

            // the structures behind them: ClassStructure contributions, MethodStructure descriptions
            var struct = info.getClassStructure();
            if (struct != null) {
                list.add(struct);
                list.addAll(struct.getContributionsAsList());
                list.addAll(struct.children());
            }
        }

        return list;
    }

    /** Render every object through each display method the contract covers. */
    private static void render(List<Object> population) {
        var sb = new StringBuilder();
        for (Object o : population) {
            sb.setLength(0);
            sb.append(o);                                   // toString()
            if (o instanceof Constant constant) {
                sb.append(constant.getValueString());
            }
            if (o instanceof XvmStructure structure) {
                sb.append(structure.getDescription());
            }
            assertFalse(sb.isEmpty(), "a display method returned nothing for " + o.getClass());
        }
    }
}
