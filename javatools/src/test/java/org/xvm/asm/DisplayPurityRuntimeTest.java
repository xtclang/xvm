package org.xvm.asm;


import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

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
 * <p>Two things need a real, fully-realized type system rather than the cold synthetic pool
 * {@link DisplayPurityTest} uses: rendering on a thread with NO pool bound (a debugger's actual
 * situation), and the {@code TypeInfo} header / {@code dump()} split.</p>
 *
 * <p>NOTE: there is deliberately no "render everything and assert the pool did not grow" test here
 * any more. Against a warmed container-zero pool that assertion catches nothing - every constant a
 * display path reaches for is already interned - and an earlier version of it passed against code
 * that was still impure. A revert sweep confirmed it was the unique catcher for nothing at all.
 * The cold-pool assertions live in {@link DisplayPurityTest}; the per-object, first-render version
 * lives in the runtime census.</p>
 */
public class DisplayPurityRuntimeTest {
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

    @Test
    public void toStringIsAPureHeaderAndDumpIsTheFullMemberList() {
        assumeTrue(DisplayPurityFixture.systemModulesAvailable(),
                "compiled XDK system modules are required");

        var runtime = DisplayPurityFixture.startRuntime();
        try {
            ConstantPool pool = DisplayPurityFixture.nativePool(runtime);
            TypeInfo     info = pool.typeInt64().ensureTypeInfo();  // build it first: that is legitimate

            int    cBefore = pool.size();
            String header  = info.toString();

            assertTrue(header.startsWith("TypeInfo:"), header);
            assertFalse(header.contains("\n"),
                    "the pure toString() is a one-line header, not a member dump: " + header);
            assertFalse(header.contains("- Methods"),
                    "the pure toString() must not walk members: " + header);

            for (int i = 0; i < 5; i++) {
                info.toString();
            }
            assertEquals(cBefore, pool.size(),
                    "the pure toString() grew the shared ConstantPool");

            // The full dump is still available, still starts with the same header, and is what
            // Ecstasy's Type.dump() returns. It needs a pool bound to the thread, exactly as the
            // historical toString() did - that is the point: the FORCED rendering may keep its
            // ambient requirements, because nothing reaches it implicitly.
            String dump;
            try (var ignore = ConstantPool.withPool(pool)) {
                dump = info.dump();
            }
            // rendered after the dump, so both agree on the lazily-computed "abstract" marker that
            // only the forced path is allowed to compute
            assertTrue(dump.startsWith(info.toString()),
                    "the dump starts with exactly the header toString() produces");
            assertTrue(dump.contains("\n- Methods"),
                    "dump() is the full member dump - this is what Type.dump() returns");
            assertTrue(dump.contains("\n- Properties"), "dump() lists properties");

            // the retained deprecated overload must be exactly dump(), for either argument, and
            // must not mutate either - it is still a toString and someone will call it
            int cAfterDump = pool.size();
            try (var ignore = ConstantPool.withPool(pool)) {
                assertEquals(dump, info.toString(false), "toString(false) must delegate to dump()");
                assertEquals(dump, info.toString(true),
                        "toString(true) must delegate to dump() too - the optimized-chain view it "
                        + "used to select was the mutating branch and had no callers");
            }
            assertEquals(cAfterDump, pool.size(),
                    "the deprecated toString(boolean) overload grew the shared ConstantPool");
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
