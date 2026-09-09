package org.xvm.asm;


import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.ClassFile;
import java.util.ArrayList;
import java.net.URISyntaxException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.CatchStart;
import org.xvm.asm.op.Cmp;
import org.xvm.asm.op.GP_Add;
import org.xvm.asm.op.GP_Neg;
import org.xvm.asm.op.GuardAll;
import org.xvm.asm.op.GuardStart;
import org.xvm.asm.op.IIP_Inc;
import org.xvm.asm.op.IIP_PreInc;
import org.xvm.asm.op.IsNot;
import org.xvm.asm.op.IsType;
import org.xvm.asm.op.JumpCond;
import org.xvm.asm.op.JumpEq;
import org.xvm.asm.op.JumpNCond;
import org.xvm.asm.op.JumpNFirst;
import org.xvm.asm.op.JumpNSample;
import org.xvm.asm.op.JumpType;
import org.xvm.asm.op.JumpVal;
import org.xvm.asm.op.JumpVal_N;
import org.xvm.asm.op.Nop;
import org.xvm.asm.op.PIP_Inc;
import org.xvm.asm.op.PIP_PreInc;
import org.xvm.asm.op.Return_0;
import org.xvm.asm.op.Var_C;
import org.xvm.asm.op.Var_I;

import org.xvm.runtime.ObjectHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Structural checks for runtime-executed op-code caches that can otherwise pin the first frame's
 * constant pool when a decoded op graph is reused.
 */
public class OpRuntimeCacheTest {

    /**
     * Conditional jump ops are shared decoded method state. They must not cache frame-owned
     * ConditionalConstant values because parallel frames can run the same op object.
     */
    @Test
    public void conditionalJumpOpsDoNotCacheFrameConditionalConstants() {
        assertNoConditionalConstantField(JumpCond.class);
        assertNoConditionalConstantField(JumpNCond.class);
    }

    /**
     * Common-type calculation during op execution must not write frame-derived type constants back
     * into decoded op fields. The old shape made one frame's owner state visible to later frames.
     */
    @Test
    public void commonTypeCalculationDoesNotWriteFrameConstantsBackToOps()
            throws IOException, URISyntaxException {
        assertNoFieldWriteIn(OpTest.class,     "calculateCommonType", "m_typeCommon");
        assertNoFieldWriteIn(OpCondJump.class, "calculateCommonType", "m_typeCommon");
    }

    /**
     * Switch ops are shared decoded instruction objects. Their first-execution switch tables can
     * contain frame/container-owned handles and type constants, so the tables must live under the
     * executing container instead of on the op.
     */
    @Test
    public void switchOpsDoNotCacheOwnerValuesOnDecodedOps() {
        assertNoOwnerBearingRuntimeCacheFields(JumpVal.class);
        assertNoOwnerBearingRuntimeCacheFields(JumpVal_N.class);
    }

    /**
     * {@code assert:once} is intentionally keyed by the decoded op, but concurrent first execution
     * still needs exactly one winner. A plain boolean preserves neither Java memory-model
     * publication nor one-winner behavior under parallel execution.
     */
    @Test
    public void jumpNFirstUsesAtomicDecodedOpState()
            throws Exception {
        Field field = JumpNFirst.class.getDeclaredField("m_fVisited");
        assertEquals(AtomicBoolean.class, field.getType());
        assertTrue(Modifier.isFinal(field.getModifiers()),
                "decoded-op once state must be a final atomic cell");

        var op = new JumpNFirst(input(2), Constant.NO_CONSTS);
        assertEquals(11, op.process(null, 10));
        assertEquals(12, op.process(null, 10));
    }

    /**
     * Parallel callers racing the first {@code assert:once} execution must produce exactly one
     * fall-through and route every other caller to the skip target.
     */
    @Test
    public void jumpNFirstConcurrentFirstExecutionHasOneWinner()
            throws Exception {
        var op       = new JumpNFirst(input(2), Constant.NO_CONSTS);
        int attempts = 32;
        var start    = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(attempts);

        try {
            var futures = IntStream.range(0, attempts)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return op.process(null, 10);
                    }))
                    .toList();

            start.countDown();

            var results = futures.stream()
                    .map(OpRuntimeCacheTest::getFuture)
                    .toList();

            assertEquals(1, results.stream().filter(result -> result == 11).count());
            assertEquals(attempts - 1, results.stream().filter(result -> result == 12).count());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Method code is the publication cell for decoded op arrays. A frame must receive one fully
     * linked op graph, not whichever partially decoded graph wins a plain-field race during
     * parallel first execution.
     */
    @Test
    public void methodCodeIsSafelyPublishedAndLinkedBeforeRuntimeAccess()
            throws Exception {
        var field = MethodStructure.class.getDeclaredField("m_code");
        assertTrue(Modifier.isVolatile(field.getModifiers()),
                "decoded Code publication must create a happens-before edge");
        var ready = MethodStructure.Code.class.getDeclaredField("m_fRuntimeLinked");
        assertTrue(Modifier.isVolatile(ready.getModifiers()),
                "runtime link readiness must publish op link writes");

        var method = newVoidMethod();
        var code = method.createCode().add(new Return_0());
        code.prepareOps();

        var ops = method.getOps();
        assertEquals(1, ops.length);
        assertEquals(0, ops[0].getAddress());
        assertDoesNotThrow(() -> method.ensureCode().assertRuntimeReadyForDiagnostics());

        var clone = method.ensureCode().cloneOnto(newVoidMethod());
        assertDoesNotThrow(clone::assertRuntimeReadyForDiagnostics);
    }

    /**
     * Guard descriptors are decoded method metadata. They may be cached for the hot path, but the
     * cache must be built during address resolution, not by the first frame that happens to execute
     * the shared {@code GUARD} or {@code GUARD_ALL} op.
     */
    @Test
    public void guardDescriptorsAreLinkedBeforeProcessAndNotPublishedByProcess()
            throws Exception {
        var target   = new Nop();
        var guardAll = new GuardAll(target);
        Op[] allOps  = {guardAll, target};
        initOps(allOps);

        guardAll.resolveAddresses(allOps);
        assertDoesNotThrow(guardAll::assertReadyForRuntime);
        assertNotNull(fieldObject(guardAll, "m_guard"));

        var guard = new GuardStart(input(1, 2, 3, 1), Constant.NO_CONSTS);
        var catchStart = new CatchStart(input(), Constant.NO_CONSTS);
        Op[] guardOps = {guard, catchStart};
        initOps(guardOps);

        guard.resolveAddresses(guardOps);
        assertDoesNotThrow(guard::assertReadyForRuntime);
        assertNotNull(fieldObject(guard, "m_guard"));

        assertNoFieldWriteIn(GuardStart.class, "process", "m_guard");
        assertNoFieldWriteIn(GuardAll.class,   "process", "m_guard");
    }

    /**
     * Constructor cleanup for op-shape classes must preserve the binary-decoded operand layout.
     * This proves the reentrancy fix did not alter instruction semantics.
     */
    @Test
    public void opcodeShapeConstructorsPreserveDecodedOperandLayouts()
            throws Exception {
        assertDecodedInts(new GP_Add(input(1, 2, 3), Constant.NO_CONSTS),
                "m_nTarget", 1, "m_nArgValue", 2, "m_nRetValue", 3);
        assertDecodedInts(new GP_Neg(input(4, 5), Constant.NO_CONSTS),
                "m_nTarget", 4, "m_nRetValue", 5);

        assertDecodedInts(new JumpEq(input(6, 7, 8, 9), Constant.NO_CONSTS),
                "m_nType", 6, "m_nArg", 7, "m_nArg2", 8, "m_ofJmp", 9);
        assertDecodedInts(new JumpType(input(10, 11, 12), Constant.NO_CONSTS),
                "m_nArg", 10, "m_nArg2", 11, "m_ofJmp", 12);

        assertDecodedInts(new Cmp(input(13, 14, 15, 16), Constant.NO_CONSTS),
                "m_nType", 13, "m_nValue1", 14, "m_nValue2", 15, "m_nRetValue", 16);
        assertDecodedInts(new IsType(input(17, 18, 19), Constant.NO_CONSTS),
                "m_nValue1", 17, "m_nValue2", 18, "m_nRetValue", 19);
        assertDecodedInts(new IsNot(input(20, 21), Constant.NO_CONSTS),
                "m_nValue1", 20, "m_nRetValue", 21);

        assertDecodedInts(new IIP_PreInc(input(22, 23, 24), Constant.NO_CONSTS),
                "m_nTarget", 22, "m_nIndex", 23, "m_nRetValue", 24);
        assertDecodedInts(new IIP_Inc(input(25, 26), Constant.NO_CONSTS),
                "m_nTarget", 25, "m_nIndex", 26);

        assertDecodedInts(new PIP_PreInc(input(27, 28, 29), Constant.NO_CONSTS),
                "m_nPropId", 27, "m_nTarget", 28, "m_nRetValue", 29);
        assertDecodedInts(new PIP_Inc(input(30, 31), Constant.NO_CONSTS),
                "m_nPropId", 30, "m_nTarget", 31);

        assertDecodedInts(new Var_I(input(32, 33), Constant.NO_CONSTS),
                "m_nType", 32, "m_nValueId", 33);
        assertDecodedInts(new Var_C(input(33), Constant.NO_CONSTS), "m_nArgValue", 33);
        assertDecodedInts(new CatchStart(input(), Constant.NO_CONSTS));
    }

    /**
     * Removing runtime caches from hot op shapes must not add replacement mutable fields. This
     * guards the performance/shape equivalence of decoded instruction objects.
     */
    @Test
    public void opcodeShapeCleanupDoesNotAddHotShapeFields() {
        assertNoShapeFields(OpGeneral.class);
        assertNoShapeFields(OpTest.class);
        assertNoShapeFields(OpCondJump.class);
        assertNoShapeFields(OpInPlace.class);
        assertNoShapeFields(OpIndex.class);
        assertNoShapeFields(OpPropInPlace.class);
        assertNoShapeFields(OpVar.class);
    }

    private static void assertNoConditionalConstantField(Class<?> clazz) {
        List<String> fields = Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> ConditionalConstant.class.isAssignableFrom(field.getType()))
                .map(Field::getName)
                .toList();

        assertEquals(List.of(), fields, clazz.getName() + " must resolve conditions from Frame");
    }

    /**
     * Asserts that {@code sField} is never written by {@code sMethod} in the COMPILED class.
     *
     * <p>This replaces a regex over the .java file. Reading source proves only that the code is
     * spelled a particular way: it passes if the same field is written through a differently named
     * local, and fails on a harmless reformat. A {@code putfield} in the compiled method is the
     * defect whatever the source looks like, which is why the tree reads compiled classes elsewhere
     * - see {@code FreezeViewSharingTest}.</p>
     *
     * @param clazz    the class to scan
     * @param sMethod  the method that must not write the field
     * @param sField   the field that must not be written there
     */
    private static void assertNoFieldWriteIn(Class<?> clazz, String sMethod, String sField)
            throws IOException, URISyntaxException {
        assertEquals(List.of(), fieldWritesIn(clazz, sMethod, sField),
                () -> sField + " is frame-derived, so caching it on a decoded Op publishes one"
                        + " frame's value to every frame sharing that op");
    }

    /**
     * @return one entry per {@code putfield} of {@code sField} in {@code sMethod}
     */
    private static List<String> fieldWritesIn(Class<?> clazz, String sMethod, String sField)
            throws IOException, URISyntaxException {
        Path pathAnchor = Path.of(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(pathAnchor),
                "javatools must be scannable as exploded classes, but the code source is "
                        + pathAnchor);

        Path pathClass = pathAnchor.resolve(clazz.getName().replace('.', '/') + ".class");
        assertTrue(Files.isRegularFile(pathClass), "no compiled class at " + pathClass);

        var offenders = new ArrayList<String>();
        var cMethods  = new int[1];
        for (var method : ClassFile.of().parse(Files.readAllBytes(pathClass)).methods()) {
            if (!sMethod.equals(method.methodName().stringValue())) {
                continue;
            }
            cMethods[0]++;
            method.code().ifPresent(code -> code.elementList().stream()
                    .filter(FieldInstruction.class::isInstance)
                    .map(FieldInstruction.class::cast)
                    .filter(field -> sField.equals(field.name().stringValue())
                            && field.opcode() == Opcode.PUTFIELD)
                    .forEach(field -> offenders.add(
                            clazz.getSimpleName() + '.' + sMethod + " writes " + sField)));
        }

        assertTrue(cMethods[0] > 0,
                () -> "expected " + clazz.getSimpleName() + " to declare " + sMethod
                        + "; the check would otherwise pass vacuously");
        return List.copyOf(offenders);
    }

    /**
     * The detector must be able to fail. A scanner that silently matches nothing would let every
     * assertion above pass while proving nothing, which is precisely how the source-text checks
     * these replaced could go stale unnoticed - so point it at a write that is KNOWN to exist.
     *
     * <p>{@code OpTest.write} legitimately assigns {@code m_typeCommon} (it discards the
     * assembly-time type so the runtime re-derives it from the correct pool). If that stops being
     * found, the checks above have gone blind rather than clean.</p>
     */
    @Test
    public void theFieldWriteDetectorActuallyDetects() throws IOException, URISyntaxException {
        assertFalse(fieldWritesIn(OpTest.class, "write", "m_typeCommon").isEmpty(),
                "OpTest.write assigns m_typeCommon, so the scanner must report it");
    }

    private static void assertNoOwnerBearingRuntimeCacheFields(Class<?> clazz) {
        var fields = Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(OpRuntimeCacheTest::isOwnerBearingRuntimeCacheField)
                .map(Field::getName)
                .toList();

        assertEquals(List.of(), fields, clazz.getName()
                + " must keep frame-owned switch caches out of decoded Op fields");
    }

    private static boolean isOwnerBearingRuntimeCacheField(Field field) {
        Class<?> type = baseComponentType(field.getType());
        return ObjectHandle.class.isAssignableFrom(type)
                || TypeConstant.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || List.class.isAssignableFrom(type)
                || type.getSimpleName().equals("Algorithm");
    }

    private static Class<?> baseComponentType(Class<?> type) {
        while (type.isArray()) {
            type = type.getComponentType();
        }
        return type;
    }

    private static <T> T getFuture(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (ExecutionException e) {
            throw new AssertionError(e.getCause());
        }
    }

    private static DataInputStream input(int... values) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            for (int value : values) {
                writePackedLong(out, value);
            }
        }
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static void assertDecodedInts(Op op, Object... fields)
            throws Exception {
        assertEquals(0, fields.length % 2);
        for (int i = 0; i < fields.length; i += 2) {
            String name     = (String) fields[i];
            Object expected = fields[i + 1];
            assertEquals(expected, fieldValue(op, name),
                    () -> op.getClass().getSimpleName() + "." + name);
        }
    }

    private static int fieldValue(Op op, String name) throws Exception {
        Class<?> clazz = op.getClass();
        while (clazz != null) {
            try {
                var field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field.getInt(op);
            } catch (NoSuchFieldException _) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new AssertionError("missing field " + name + " on " + op.getClass().getName());
    }

    private static Object fieldObject(Op op, String name) throws Exception {
        Class<?> clazz = op.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(op);
            } catch (NoSuchFieldException _) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new AssertionError("missing field " + name + " on " + op.getClass().getName());
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, () -> "missing method " + signature);

        int brace = source.indexOf('{', start);
        assertTrue(brace >= 0, () -> "missing method body " + signature);

        int depth = 0;
        for (int i = brace, c = source.length(); i < c; i++) {
            char ch = source.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}' && --depth == 0) {
                return source.substring(brace, i + 1);
            }
        }
        throw new AssertionError("unterminated method body " + signature);
    }

    private static void assertNoShapeFields(Class<?> clazz) {
        var fields = Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> field.getType().getSimpleName().endsWith("Shape")
                        || field.getName().toLowerCase().contains("shape")
                        || field.getName().equals("binary")
                        || field.getName().equals("secondArgument")
                        || field.getName().equals("assigns")
                        || field.getName().equals("typeAware"))
                .map(Field::getName)
                .toList();

        assertEquals(List.of(), fields, clazz.getName()
                + " must not add per-op shape/cache fields to the runtime hot path");
    }

    private static Path sourcePath(String source) {
        Path path = Path.of("src/main/java", source);
        if (!Files.exists(path)) {
            path = Path.of("javatools/src/main/java", source);
        }
        assertTrue(Files.exists(path), () -> "missing source file " + source);
        return path;
    }

    private static MethodStructure newVoidMethod() {
        var file = new FileStructure("test");
        var clz = file.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Test", null);
        return clz.createMethod(false, Constants.Access.PUBLIC, null,
                Parameter.NO_PARAMS, "run", Parameter.NO_PARAMS, true, true);
    }

    private static void initOps(Op[] ops) {
        for (int i = 0; i < ops.length; i++) {
            ops[i].initInfo(i, i + 1, 0, 0);
        }
    }
}
