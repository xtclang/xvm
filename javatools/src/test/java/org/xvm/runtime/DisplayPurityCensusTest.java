package org.xvm.runtime;


import java.io.File;
import java.io.IOException;

import java.net.URI;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.DisplayPurityFixture;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.DeferredArrayHandle;

import org.xvm.runtime.template.reflect.xClass;

import org.xvm.runtime.template._native.reflect.xRTType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * CENSUS ratchet for the display-purity guarantee: <b>a debugger calling {@code toString()} must
 * never trigger a side effect.</b>
 *
 * <p>That is a promise about a whole family, not about a list of known offenders, so this test does
 * not name the classes it checks. It ENUMERATES, out of the COMPILED CLASSES, every
 * {@link ObjectHandle} subclass that declares its own {@code toString()} - handles are what a
 * watch window actually renders - and then requires each one to be either exercised by the live
 * population below or listed in {@link #NOT_EXERCISED} with a stated reason. A {@code toString()}
 * added tomorrow fails this test until someone does one of those two things, which is what makes
 * the guarantee hold for code nobody has written yet.</p>
 *
 * <p>The mutation observable is shared-{@link ConstantPool} growth. It is a proxy, but a sharp one:
 * the display impurities found in this codebase all ended in an intern - {@code ensure*Constant},
 * {@code getImplicitlyImportedIdentity}, the canonical {@code typeObject()}/{@code typeFunction()}
 * getters, {@code freeze()}, and {@code Source.normalize()}. {@link #censusDetectsAnImpureToString}
 * proves the detector actually fires, so a green result here cannot mean a dead instrument.</p>
 *
 * <h2>What this test does and does not prove</h2>
 *
 * <p><b>Coverage: 13 of the 49 handle {@code toString()} implementations are exercised; 36 are
 * listed in {@link #NOT_EXERCISED} with a reason.</b> So the ENUMERATION half is a true ratchet - a
 * {@code toString()} added tomorrow fails this test until it is covered or justified - while the
 * PURITY half only proves the 12 that a fixture can actually reach. Shrinking NOT_EXERCISED is
 * always an improvement.</p>
 *
 * <p>Two known limits of the pool-growth observable, both learned the hard way:</p>
 * <ul>
 * <li>{@code ensure*Constant} is IDEMPOTENT, so a {@code toString()} that interns once and then
 *     finds its constant already there is invisible unless the very first render is measured. An
 *     earlier version of this test warmed up before measuring and consequently passed against code
 *     that was still impure. {@link #renderingEveryLiveHandleMutatesNothing} now measures the first
 *     render, per object, attributing growth to the exact class.</li>
 * <li>Even so, rendering over a WELL-KNOWN type observes nothing, because container startup already
 *     interned everything reachable from it. {@link #renderingAHandleOverANovelTypeDoesNotFreezeIt}
 *     works around that by rendering over a type the pool has never seen. That is red-provable for
 *     {@code DeferredArrayHandle}. It is NOT red-provable for {@code xRTType.TypeHandle} or
 *     {@code xClass.ClassHandle}: constructing either one runs {@code ensureClass}/
 *     {@code resolveClass}, which interns the augmented type that their {@code toString()} would
 *     have frozen, so by render time there is nothing left to observe no matter how exotic the type
 *     is. Their fixes rest on the call chain, not on a failing assertion; the assertions below are
 *     kept as regression guards, not as evidence.</li>
 * </ul>
 */
public class DisplayPurityCensusTest {
    /**
     * Handle classes whose {@code toString()} this census cannot reach with a live instance, each
     * with the reason. This list is the honest part of the ratchet: an entry here is a KNOWN gap,
     * visible to any reader, not a silent omission. Shrinking it is always an improvement.
     */
    private static final Map<String, String> NOT_EXERCISED = Map.ofEntries(
        // --- interpreter-internal: only ever produced mid-execution, by the interpreter loop -----
        Map.entry("ObjectHandle$DeferredCallHandle",
                  "wraps a live suspended Frame; one built outside the interpreter is not "
                  + "representative of what a debugger would render"),
        Map.entry("ObjectHandle$DeferredSingletonHandle",
                  "only produced mid-initialization of a singleton constant"),
        Map.entry("ObjectHandle$DeferredPropertyHandle",
                  "only produced by the interpreter for a deferred property access"),
        Map.entry("ObjectHandle$InitializingHandle",
                  "exists only during const construction, inside the initializer"),
        Map.entry("ObjectHandle$TransientId",
                  "protected constructor; an internal key for transient field storage"),
        Map.entry("ObjectHandle$NativeFutureHandle",
                  "protected constructor; only created by native service plumbing"),
        Map.entry("ObjectHandle",
                  "the base class is never instantiated directly - every live handle is a "
                  + "subclass, and the subclasses that inherit this toString() unchanged (JavaLong "
                  + "and friends) ARE exercised, so the base body does run"),

        // --- need an executing Frame or a live call chain ----------------------------------------
        Map.entry("xRef$RefHandle", "requires a frame and a referent slot"),
        Map.entry("xAtomic$AtomicHandle", "a Ref annotation; requires a frame"),
        Map.entry("xAtomicInt128$AtomicLongLongHandle", "as xAtomic$AtomicHandle"),
        Map.entry("xAtomicIntNumber$AtomicJavaLongHandle", "as xAtomic$AtomicHandle"),
        Map.entry("xRTFunction$FunctionHandle", "requires a frame and a CallChain"),
        Map.entry("xRTFunction$DelegatingHandle", "as xRTFunction$FunctionHandle"),
        Map.entry("xRTFunction$SingleBoundHandle", "as xRTFunction$FunctionHandle"),
        Map.entry("xRTFunction$FullyBoundHandle", "as xRTFunction$FunctionHandle"),
        Map.entry("xRTFunction$NativeFunctionHandle", "as xRTFunction$FunctionHandle"),
        Map.entry("xRTFunction$FunctionProxyHandle", "requires a cross-service proxy"),
        Map.entry("xRTMethod$MethodHandle", "requires a resolved MethodStructure and a frame"),
        Map.entry("xRTSignature$SignatureHandle", "as xRTMethod$MethodHandle"),
        Map.entry("Proxy$ProxyHandle", "requires a live cross-service proxy target"),

        // --- protected template factories: value handles built only by their own template --------
        Map.entry("BaseInt128$LongLongHandle",
                  "protected factory on the template; its toString() renders a final LongLong plus "
                  + "the ObjectHandle base, which is exercised"),
        Map.entry("BaseDecFP$DecimalHandle", "as BaseInt128$LongLongHandle"),
        Map.entry("xFPLiteral$FPNHandle", "as BaseInt128$LongLongHandle"),
        Map.entry("xIntLiteral$IntNHandle", "as BaseInt128$LongLongHandle"),
        Map.entry("xRegEx$RegExHandle", "private factory; renders a final pattern string"),
        Map.entry("xEnum$EnumHandle",
                  "created only by a template's enum initialization; xBoolean's TRUE/FALSE are not "
                  + "available until the native container has run its enum warm-up"),

        // --- array/delegate internals ------------------------------------------------------------
        Map.entry("xRTDelegate$DelegateHandle", "created by array internals, not directly"),
        Map.entry("xRTCharDelegate$CharArrayHandle", "as xRTDelegate$DelegateHandle"),
        Map.entry("xRTSlicingDelegate$SliceHandle", "as xRTDelegate$DelegateHandle"),
        Map.entry("xRTComponentTemplate$ComponentTemplateHandle",
                  "requires a loaded Component from the reflection API"),
        Map.entry("xRTTypeTemplate$TypeTemplateHandle", "as xRTComponentTemplate"),

        // --- require real OS, network or injection resources -------------------------------------
        Map.entry("xOSFileNode$NodeHandle", "requires a real file-system node"),
        Map.entry("xRawOSFileChannel$ChannelHandle", "requires an open OS file channel"),
        Map.entry("xRTServer$HttpServerHandle", "requires a bound HTTP server"),
        Map.entry("xRTConnector$ConnectorHandle", "requires a live network connector"),
        Map.entry("xInject$InjectedHandle", "requires an injector and a resource provider"));

    // ----- the census ----------------------------------------------------------------------------

    @Test
    public void everyHandleToStringIsEitherExercisedOrExplicitlyExcluded() throws IOException {
        Set<String> census = handleToStringCensus();
        assertTrue(census.size() > 25,
                "the census found only " + census.size() + " handle toString() declarations, which "
                + "means the enumerator is broken, not that the codebase is small");

        assumeTrue(DisplayPurityFixture.systemModulesAvailable(),
                "compiled XDK system modules are required to build live handles");

        var runtime = DisplayPurityFixture.startRuntime();
        try {
            var container = new NativeContainer(
                    runtime, DisplayPurityFixture.systemRepository());
            List<Object> population = HandlePopulation.build(container);

            // an instance exercises the FIRST toString() up its hierarchy - that is the one that
            // actually runs - so a subclass that does not override still covers its parent's
            var exercised = new TreeSet<String>();
            for (Object o : population) {
                for (Class<?> clz = o.getClass(); clz != null; clz = clz.getSuperclass()) {
                    if (declaresToString(clz)) {
                        exercised.add(simpleName(clz));
                        break;
                    }
                }
            }

            var uncovered = new TreeSet<>(census);
            uncovered.removeAll(exercised);
            uncovered.removeAll(NOT_EXERCISED.keySet());

            assertTrue(uncovered.isEmpty(),
                    "these handle toString() implementations are neither exercised by this census "
                    + "nor listed in NOT_EXERCISED with a reason - add a live instance to "
                    + "HandlePopulation, or add an entry saying why it cannot be reached: "
                    + uncovered);

            // the exclusion list must not rot: an entry that no longer names a real handle
            // toString() is stale and should be deleted
            var stale = new TreeSet<>(NOT_EXERCISED.keySet());
            stale.removeAll(census);
            assertTrue(stale.isEmpty(),
                    "NOT_EXERCISED names handles that no longer declare toString(): " + stale);
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * The purity assertion itself: render the whole live population repeatedly and require that the
     * shared ConstantPool did not grow and that nothing threw.
     */
    @Test
    public void renderingEveryLiveHandleMutatesNothing() {
        assumeTrue(DisplayPurityFixture.systemModulesAvailable(),
                "compiled XDK system modules are required to build live handles");

        var runtime = DisplayPurityFixture.startRuntime();
        try {
            var container = new NativeContainer(
                    runtime, DisplayPurityFixture.systemRepository());
            ConstantPool pool       = container.getConstantPool();
            List<Object> population = HandlePopulation.build(container);

            assertTrue(population.size() > 20,
                    "population too small to be meaningful: " + population.size());

            // NOTE: do NOT warm up first. An "ensure*Constant" intern is idempotent, so a handle
            // that interns on its FIRST render and then finds the constant already there would be
            // invisible to a check that only looks at repeat renders. The first render is exactly
            // the one a debugger performs, so it is the one that must be clean.
            var offenders = renderAndAttributeGrowth(pool, population);
            assertTrue(offenders.isEmpty(),
                    "these toString() implementations interned into the shared ConstantPool on "
                    + "their FIRST render (class -> constants added): " + offenders
                    + " - looking at one of these in a debugger mutates the program");

            // and again, to catch anything that interns something fresh on every call
            var repeatOffenders = renderAndAttributeGrowth(pool, population);
            assertTrue(repeatOffenders.isEmpty(),
                    "these toString() implementations intern on EVERY render: " + repeatOffenders);

            int cBefore = pool.size();

            // negative control: the instrument must be alive
            pool.ensureParameterizedTypeConstant(pool.typeTuple(),
                    pool.typeInt64(), pool.typeString(), pool.typeObject(), pool.typeBoolean());
            assertTrue(pool.size() > cBefore,
                    "negative control failed: interning a fresh type did not grow the pool");
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * The sharp version of the pool assertion, for the {@code augmentType()}/{@code freeze()}
     * family - {@code DeferredArrayHandle}, and the reflection handles that render through
     * {@code getType()}.
     *
     * <p>Those sites intern an {@code ImmutableTypeConstant}, and {@code ensure*Constant} is
     * idempotent, so rendering a handle over a WELL-KNOWN type (Int, String) observes nothing: the
     * immutable form was already interned during container startup. The fix is to render over a
     * type the pool has never seen, so {@code freeze()} has real work to do at the moment of the
     * render.</p>
     *
     * <p>The control below is not optional. A test that silently degrades into "the type was
     * already interned, nothing to observe" would pass for the wrong reason - the exact failure
     * mode this class already hit once - so it first proves that freezing a type of this shape
     * really does grow the pool.</p>
     */
    @Test
    public void renderingAHandleOverANovelTypeDoesNotFreezeIt() {
        assumeTrue(DisplayPurityFixture.systemModulesAvailable(),
                "compiled XDK system modules are required");

        var runtime = DisplayPurityFixture.startRuntime();
        try {
            var container = new NativeContainer(
                    runtime, DisplayPurityFixture.systemRepository());
            ConstantPool pool      = container.getConstantPool();

            try (var ignore = ConstantPool.withPool(pool)) {
                // ---- control: freezing a type of this shape must really intern ------------------
                TypeConstant typeControl = pool.ensureArrayType(novelType(pool, pool.typeInt32()));
                int          cControl    = pool.size();
                typeControl.freeze();
                assertTrue(pool.size() > cControl,
                        "precondition failed: freeze() on a freshly built "
                        + typeControl.getValueString()
                        + " did not grow the pool, so this test cannot observe the interning it "
                        + "exists to catch - pick a type further off the beaten path");

                // ---- measurement: the same shape, a different (still novel) element -------------
                TypeConstant    typeNovel = pool.ensureArrayType(novelType(pool, pool.typeUInt8()));
                TypeComposition clzArray  = container.resolveClass(typeNovel);
                var             handle    = new DeferredArrayHandle(clzArray, Utils.OBJECTS_NONE);

                assertNoGrowth(pool, handle, "DeferredArrayHandle");

                // The reflection handles render through getType() too. NOTE: these two are
                // regression guards, NOT red-on-master evidence - building either handle already
                // interns the augmented type its toString() would freeze (ensureClass /
                // resolveClass), so this assertion stays green even against the unfixed code.
                // Verified: reverting both toString()s leaves this test passing.
                assertNoGrowth(pool,
                        xRTType.makeHandle(container, novelType(pool, pool.typeInt16()), true),
                        "xRTType.TypeHandle");
                assertNoGrowth(pool,
                        xClass.INSTANCE.createStruct(null, container.resolveClass(
                                pool.ensureParameterizedTypeConstant(pool.typeClass(),
                                        novelType(pool, pool.typeInt128())))),
                        "xClass.ClassHandle");
            }
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * Render one handle and require that it interned nothing.
     */
    private static void assertNoGrowth(ConstantPool pool, ObjectHandle handle, String sWhat) {
        int    cBefore   = pool.size();
        String sRendered = handle.toString();

        assertFalse(sRendered.isEmpty(), sWhat + " rendered nothing");
        assertEquals(cBefore, pool.size(),
                sWhat + ".toString() interned " + (pool.size() - cBefore) + " constant(s) while "
                + "rendering a type the pool had not yet frozen - it called "
                + "getType()/augmentType()/freeze(). Rendered: " + sRendered);
    }

    /**
     * @return a type shape the XDK's own startup has no reason to have interned
     */
    private static TypeConstant novelType(ConstantPool pool, TypeConstant typeLeaf) {
        return pool.ensureMapType(pool.typeString(), pool.ensureSetType(typeLeaf));
    }

    /**
     * Self-test of the detector. A census that cannot see a known-impure {@code toString()} proves
     * nothing about the ones it says are clean, so this renders a deliberately impure handle and
     * requires the pool-growth check to fire.
     */
    @Test
    public void censusDetectsAnImpureToString() {
        assumeTrue(DisplayPurityFixture.systemModulesAvailable(),
                "compiled XDK system modules are required");

        var runtime = DisplayPurityFixture.startRuntime();
        try {
            var container = new NativeContainer(
                    runtime, DisplayPurityFixture.systemRepository());
            ConstantPool pool      = container.getConstantPool();

            var offenders = renderAndAttributeGrowth(pool, List.of(new PoisonHandle(pool)));
            assertFalse(offenders.isEmpty(),
                    "the census's mutation detector did not fire on a toString() that interns - "
                    + "the purity assertions in this class would be worthless");
        } finally {
            runtime.shutdownXVM();
        }
    }

    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Render every member of the population one at a time, attributing any ConstantPool growth to
     * the exact class whose {@code toString()} caused it.
     *
     * @return offending class name -&gt; constants it added, empty when everything was clean
     */
    private static Map<String, Integer> renderAndAttributeGrowth(
            ConstantPool pool, List<Object> population) {
        var offenders = new TreeMap<String, Integer>();
        var sb        = new StringBuilder();
        for (Object o : population) {
            int cBefore = pool.size();

            sb.setLength(0);
            sb.append(o);                           // toString(), exactly as a debugger calls it
            assertFalse(sb.isEmpty(),
                    "a display method returned nothing for " + o.getClass().getName());

            int cGrew = pool.size() - cBefore;
            if (cGrew > 0) {
                offenders.merge(o.getClass().getName(), cGrew, Integer::sum);
            }
        }
        return offenders;
    }

    /**
     * Enumerate, from the COMPILED OUTPUT, every {@link ObjectHandle} subclass that declares its
     * own {@code toString()}.
     *
     * <p>This deliberately does not read source text. "Which classes subclass {@code ObjectHandle}
     * and declare {@code toString()}" is a question the class files answer exactly, whereas a
     * source scan can be fooled by formatting, by the token appearing in a comment or a string
     * literal, by a file moving, and by the working directory the build happens to run from.</p>
     */
    private static Set<String> handleToStringCensus() throws IOException {
        var census = new TreeSet<String>();
        for (Class<?> clz : classesInCodeSourceOf(ObjectHandle.class)) {
            if (ObjectHandle.class.isAssignableFrom(clz) && declaresToString(clz)) {
                census.add(simpleName(clz));
            }
        }
        return census;
    }

    /**
     * @return every class the compiled output containing {@code clzAnchor} holds, loaded but NOT
     *         initialized, skipping any that cannot be resolved
     */
    private static List<Class<?>> classesInCodeSourceOf(Class<?> clzAnchor) throws IOException {
        var source = clzAnchor.getProtectionDomain().getCodeSource();
        assertNotNull(source, "no CodeSource for " + clzAnchor.getName()
                + " - the census cannot enumerate the compiled output");

        var root = Path.of(URI.create(source.getLocation().toString()));
        assertTrue(Files.isDirectory(root),
                "expected the compiled classes directory, got " + root
                + " - if javatools is now tested from a jar, this scan needs a JarFile branch");

        var loader  = DisplayPurityCensusTest.class.getClassLoader();
        var classes = new ArrayList<Class<?>>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                String sName = root.relativize(file).toString()
                        .replace(File.separatorChar, '.');
                sName = sName.substring(0, sName.length() - ".class".length());
                try {
                    classes.add(Class.forName(sName, false, loader));
                } catch (Throwable ignore) {
                    // a class whose dependencies are not on the test classpath; it cannot be a
                    // live handle in this fixture either, so skipping it is safe
                }
            }
        }
        return classes;
    }

    private static boolean declaresToString(Class<?> clz) {
        try {
            clz.getDeclaredMethod("toString");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** {@code Outer$Nested} for a nested class, the simple name otherwise. */
    private static String simpleName(Class<?> clz) {
        String sName = clz.getName();
        int    ofPkg = sName.lastIndexOf('.');
        return ofPkg < 0 ? sName : sName.substring(ofPkg + 1);
    }

    /**
     * A handle whose {@code toString()} interns on every call - the thing this census exists to
     * catch. It is deliberately kept here, rather than by un-fixing a real site, so the self-test
     * stays valid forever.
     */
    private static final class PoisonHandle
            extends ObjectHandle {
        PoisonHandle(ConstantPool pool) {
            super(null);
            f_pool = pool;
        }

        @Override
        public String toString() {
            return "poison " + f_pool.ensureParameterizedTypeConstant(f_pool.typeList(),
                    f_pool.ensureStringConstant("intern-" + m_cRenders++).getType());
        }

        private final ConstantPool f_pool;
        private int                m_cRenders;
    }
}
