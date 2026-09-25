package org.xvm.asm.constants;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.Constants;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.IdentityConstant.NestedIdentity;
import org.xvm.asm.constants.MethodBody.Implementation;

import org.xvm.runtime.RuntimeTypeContext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DestinationOwnershipTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void defaultArgumentSuperMatchingDoesNotTruncateInAFrozenImage(boolean bindAmbient) {
        var source = new FileStructure(Constants.ECSTASY_MODULE);
        var pool = source.getConstantPool();
        var object = source.getModule().createClass(Access.PUBLIC, Format.CLASS, "Object", null);
        var type = object.getIdentityConstant().getType();
        var host = source.getModule().createClass(Access.PUBLIC, Format.CLASS, "Host", null);
        var method = host.createMethod(false, Access.PUBLIC, null,
                new Parameter[] {new Parameter(pool, type, null, null, true, 0, false)}, "call",
                new Parameter[] {new Parameter(pool, type, "first", null, false, 0, false),
                        new Parameter(pool, type, "second", pool.ensureStringConstant("default"), false, 1, false)},
                true, false);
        var base = object.createMethod(false, Access.PUBLIC, null, Parameter.NO_PARAMS, "call",
                new Parameter[] {new Parameter(pool, type, "value", null, false, 0, false)}, true, false);
        var subSignature = method.getIdentityConstant().getSignature();
        var baseSignature = base.getIdentityConstant().getSignature();
        var subInfo = new MethodInfo(new MethodBody(method.getIdentityConstant(), subSignature,
                Implementation.Explicit), 0);
        var baseInfo = new MethodInfo(new MethodBody(base.getIdentityConstant(), baseSignature,
                Implementation.Explicit), 0);
        var context = new RuntimeTypeContext(pool);
        var constants = pool.getConstants();
        var positions = Arrays.stream(constants).map(c -> c.getPosition()).toList();
        context.freezeDefinitions();
        var ambient = bindAmbient ? new FileStructure("Unrelated").getConstantPool() : null;
        try (var scope = ConstantPool.withPool(ambient)) {
            var receiver = context.typeOf(host.getIdentityConstant());
            assertEquals(List.of(baseSignature), receiver.collectPotentialSuperMethods(subInfo,
                    subSignature, Map.of(baseSignature, baseInfo)));
        }
        assertArrayEquals(constants, pool.getConstants());
        assertEquals(positions, Arrays.stream(constants).map(c -> c.getPosition()).toList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void delayedNestedResolutionKeepsItsExplicitDestination(boolean bindAmbient) {
        var source = new FileStructure("Source");
        var pool = source.getConstantPool();
        var holder = source.getModule().createClass(Access.PUBLIC, Format.CLASS, "Holder", null);
        var actual = source.getModule().createClass(Access.PUBLIC, Format.CLASS, "Actual", null)
                .getCanonicalType();
        var originalIdentity = actual.getDefiningConstant();
        var formal = holder.addTypeParam("T", actual).getIdentityConstant().getFormalType();
        var parent = pool.ensurePropertyConstant(holder.getIdentityConstant(), "outer");
        var method = pool.ensureMethodConstant(parent, "value", TypeConstant.NO_TYPES, new TypeConstant[] {formal});
        var expected = new SignatureConstant(pool, "value", TypeConstant.NO_TYPES, new TypeConstant[] {actual});
        var destination = new FileStructure(source).getConstantPool();
        var unrelated = new FileStructure("Unrelated").getConstantPool();
        var nested = (NestedIdentity) method.resolveNestedIdentity(destination, ignored -> actual);
        assertNull(destination.getConstant(expected));

        try (var scope = ConstantPool.withPool(bindAmbient ? unrelated : null)) {
            var hash = nested.hashCode();
            assertEquals(hash, nested.hashCode());
            var resolved = destination.getConstant(expected);
            assertNotNull(resolved);
            assertSame(destination, resolved.getConstantPool());
            assertNull(unrelated.getConstant(expected));
            var equivalent = (NestedIdentity) destination.ensureMethodConstant(parent, (SignatureConstant) resolved)
                    .getNestedIdentity();
            assertEquals(nested, equivalent);
            assertEquals(0, nested.compareTo(equivalent));
            var transported = nested.getResolvedIdentity();
            assertSame(destination, transported.getConstantPool());
            assertEquals(equivalent, transported.getNestedIdentity());
            assertEquals(actual, ((MethodConstant) transported).getRawReturns()[0]);
        }
        assertSame(originalIdentity, actual.getDefiningConstant());
        assertSame(pool, actual.getConstantPool());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pendingWrapperResolutionUsesDestination(boolean bindAmbient) {
        var source = new FileStructure(Constants.ECSTASY_MODULE);
        var pool = source.getConstantPool();
        var destination = new FileStructure(source).getConstantPool();
        var pending = new ParameterizedTypeConstant(pool, pool.typeArray(),
                new TypeConstant[] {new PendingTypeConstant(pool, null)});
        var actual = pool.ensureArrayType(pool.typeString());
        var ambient = bindAmbient ? new FileStructure("Unrelated").getConstantPool() : null;
        try (var scope = ConstantPool.withPool(ambient)) {
            var resolved = pending.resolvePending(destination, actual);
            assertSame(destination, resolved.getConstantPool());
            assertEquals(actual, resolved);
            assertSame(pool, pending.getConstantPool());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unrelatedAmbientPoolDoesNotDuplicateObjectMethods(boolean bindAmbient) {
        var source = new FileStructure(Constants.ECSTASY_MODULE);
        var object = declaredMethod(source, "Object");
        var left = declaredMethod(source, "Left");
        var right = declaredMethod(source, "Right");
        var base = new MethodInfo(new MethodBody[] {left, object}, 0);
        var addition = new MethodInfo(new MethodBody[] {right, object}, 0);
        var ambient = bindAmbient ? new FileStructure("Unrelated").getConstantPool() : null;
        try (var scope = ConstantPool.withPool(ambient)) {
            var chain = base.layerOn(addition, false, new ErrorList(20)).getChain();
            assertEquals(3, chain.length);
            assertEquals(1, Arrays.stream(chain).filter(body -> body.equals(object)).count());
        }
    }

    private static MethodBody declaredMethod(FileStructure file, String name) {
        var clz = file.getModule().createClass(Access.PUBLIC, Format.INTERFACE, name, null);
        var method = clz.createMethod(false, Access.PUBLIC, null,
                Parameter.NO_PARAMS, "m", Parameter.NO_PARAMS, true, true);
        return new MethodBody(method.getIdentityConstant(), method.getIdentityConstant().getSignature(),
                Implementation.Declared);
    }
}
