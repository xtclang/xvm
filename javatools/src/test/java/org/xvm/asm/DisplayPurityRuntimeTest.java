package org.xvm.asm;


import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.api.EmbeddingTestSupport;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.Runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * The EMPIRICAL half of the side-effect-free display gate
 * (docs/reentrancy/plans/side-effect-free-tostring.md).
 *
 * <p>{@link DisplayPurityTest} greps banned callees out of display-method bodies, but that scan is
 * textual and NOT transitive: impurity hidden behind an innocuous-looking helper (the way
 * {@code xEnum.EnumHandle.toString} forced a lazy {@code EnumInfo} through a plain {@code getName()})
 * is invisible to it. This test closes that gap from the other side: it builds a broad population of
 * real, fully-realized type-system objects, snapshots the shared {@code ConstantPool}, then renders
 * every one of them repeatedly through {@code toString()} / {@code getValueString()} /
 * {@code getDescription()} and asserts the pool did not grow.</p>
 *
 * <p>Pool growth is the sharpest available signal because the dominant display impurities all end in
 * an intern: {@code ensure*Constant}, {@code getImplicitlyImportedIdentity}, the canonical
 * {@code typeObject()}/{@code typeFunction()} getters, {@code freeze()}, and {@code normalize()}'s
 * one-StringConstant-per-source-line. A forced lazy cell that does not intern would slip through, so
 * this complements rather than replaces the static gate.</p>
 */
public class DisplayPurityRuntimeTest {
    @Test
    public void renderingTheTypeSystemDoesNotGrowTheConstantPool() {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        var runtime = new Runtime();
        runtime.start();
        try {
            ConstantPool pool = NativeContainer
                    .create(runtime, EmbeddingTestSupport.systemRepository())
                    .getConstantPool();

            // ---- build the population FIRST; this legitimately interns ---------------------------
            List<Object> population = buildPopulation(pool);
            assertTrue(population.size() > 100,
                    "the population must be broad enough to be meaningful, was " + population.size());

            // warm every renderer once, so first-call memoization inside a renderer is not counted
            render(population);

            // ---- now the pool must be stable across repeated rendering ---------------------------
            int cBefore = pool.size();
            for (int i = 0; i < 3; i++) {
                render(population);
            }
            assertEquals(cBefore, pool.size(),
                    "rendering the type system grew the shared ConstantPool - a display method is "
                    + "interning (see docs/reentrancy/plans/side-effect-free-tostring.md)");

            // ---- negative control: prove pool.size() actually detects interning ------------------
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
     * A deliberately broad population: the constant families whose display paths were the flagged
     * offenders, plus the TypeInfo member objects (PropertyInfo/MethodInfo and their bodies) that a
     * debugger expands into.
     */
    private static List<Object> buildPopulation(ConstantPool pool) {
        var list = new ArrayList<>();

        // type constants, including the FUNCTION shape whose renderer used to call isA(typeFunction)
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

            // the structures behind them: ClassStructure contributions, MethodStructure descriptions
            ClassStructure struct = info.getClassStructure();
            if (struct != null) {
                list.add(struct);
                list.addAll(struct.getContributionsAsList());
                struct.children().forEach(list::add);
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
