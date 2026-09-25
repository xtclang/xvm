package org.xvm.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.HandleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.RuntimeTypeContext.IncompatibleTypeOwnerException;
import org.xvm.runtime.ServiceContext.CallLaterRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapturedValuesTest {
    private final Runtime runtime = new Runtime();

    @AfterEach
    void shutdown() {
        runtime.shutdownXVM();
    }

    @Test
    void capturedArgumentsSurviveMetadataClearsInTheirExecutionHeap() {
        var file = image("CapturedValues");
        var marker = file.getModule().createClass(Access.PUBLIC, Format.ANNOTATION, "Marker", null);
        var container = new TestContainer(runtime, file);
        var context = container.getTypeContext();
        var constants = file.getConstantPool().getConstants();
        context.freezeDefinitions();
        var value = new ObjectHandle(null) {};
        var token = container.f_heap.capture(value);
        var pool = context.getDescriptorPool();
        var annotated = pool.ensureAnnotatedTypeConstant(marker.getIdentityConstant(),
                new Constant[] {token}, context.typeOf(file.getModuleId()));
        var tuple = pool.ensureTupleType(annotated);
        var otherCapture = container.f_heap.capture(value);
        assertNotEquals(token, otherCapture);
        assertNotSame(annotated, pool.ensureAnnotatedTypeConstant(marker.getIdentityConstant(),
                new Constant[] {otherCapture}, context.typeOf(file.getModuleId())));

        context.clearMetadata();

        assertSame(annotated, context.intern(annotated));
        assertSame(tuple, pool.ensureTupleType(annotated));
        assertSame(value, token.getHandle(frame(container)));
        assertSame(value, token.getHandle(frame(container)));
        assertArrayEquals(constants, file.getConstantPool().getConstants());
    }

    @Test
    void sameImageAndSharedModulesDoNotAuthorizeCaptureTransport() {
        var file = image("CaptureTransport");
        var marker = file.getModule().createClass(Access.PUBLIC, Format.ANNOTATION, "Marker", null);
        var first = new TestContainer(runtime, file);
        var second = new TestContainer(runtime, file);
        var source = first.getTypeContext();
        var destination = second.getTypeContext();
        source.freezeDefinitions();
        destination.freezeDefinitions();
        var value = new ObjectHandle(null) {};
        var token = first.f_heap.capture(value);
        var annotated = source.getDescriptorPool().ensureAnnotatedTypeConstant(
                marker.getIdentityConstant(), new Constant[] {token}, source.typeOf(file.getModuleId()));
        assertFalse(annotated.isShared(destination.getDescriptorPool()));
        assertThrows(IncompatibleTypeOwnerException.class, () -> destination.intern(annotated));
        assertThrows(IncompatibleTypeOwnerException.class,
                () -> destination.importShared(annotated, source, _ -> true));
        assertThrows(IncompatibleTypeOwnerException.class,
                () -> destination.importShared(source.getDescriptorPool().ensureTupleType(annotated),
                        source, _ -> true));
        assertThrows(IllegalArgumentException.class, () -> token.getHandle(frame(second)));
        assertSame(value, token.getHandle(frame(first)));
    }

    @Test
    void definitionsAndUnknownTokensCannotSupplyCapturedValues() {
        var file = image("CaptureTokens");
        var container = new TestContainer(runtime, file);
        assertThrows(IllegalArgumentException.class, () -> new HandleConstant(file.getConstantPool()));
        var token = new HandleConstant(container.getTypeContext().getDescriptorPool());
        assertThrows(IllegalArgumentException.class, () -> container.f_heap.resolveCapture(token));
    }

    private static FileStructure image(String name) {
        var file = new FileStructure(name);
        file.merge(new FileStructure(Constants.ECSTASY_MODULE).getModule(), false, false);
        return file;
    }

    private static Frame frame(Container container) {
        var context = new ServiceContext(container, "capture", 1);
        var fiber = new Fiber(context, new CallLaterRequest(null, null, Utils.OBJECTS_NONE, 0));
        return new Frame(fiber, 0, Op.NO_OPS, Utils.OBJECTS_NONE, Op.A_IGNORE, null);
    }

    private static final class TestContainer extends Container {
        TestContainer(Runtime runtime, FileStructure file) {
            super(runtime, null, file.getModuleId());
        }

        @Override
        public ObjectHandle getInjectable(Frame frame, String name, TypeConstant type, ObjectHandle options) {
            throw new UnsupportedOperationException();
        }
    }
}
