package org.xvm.runtime;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.xvm.asm.FileStructure;

import org.xvm.asm.constants.TypeConstant;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConstHeapOwnershipTest {
    @Test
    void matchingTypesDoNotShareAnUnsharedSingletonFromTheParentHeap() {
        var runtime = new Runtime();
        try {
            var parent = container(runtime, null);
            var child = container(runtime, parent);
            var constant = parent.getConstantPool().ensureSingletonConstConstant(parent.getModule());
            var handle = new ObjectHandle(null) {};
            parent.ensureSingletonState(constant).setHandle(handle);
            parent.f_heap.saveConstHandle(constant, handle);
            var local = child.getConstantPool().register(constant);

            assertNull(child.f_heap.getConstHandle(local));
            assertNull(child.ensureSingletonState(local).getHandle());
            assertSame(handle, parent.f_heap.getConstHandle(constant));
            assertSame(handle, parent.ensureSingletonState(constant).getHandle());
        } finally {
            runtime.shutdownXVM();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void parentHeapOnlySuppliesHandlesSharedWithTheReceiver(boolean shared) {
        var runtime = new Runtime();
        try {
            var parent = container(runtime, null);
            var child = container(runtime, parent);
            var constant = parent.getConstantPool().ensureStringConstant("value");
            var handle = new ObjectHandle(null) {
                @Override
                public boolean isShared(Container receiver, Map<ObjectHandle, Boolean> visited) {
                    return receiver == parent || shared;
                }
            };
            parent.f_heap.saveConstHandle(constant, handle);
            var local = child.getConstantPool().register(constant);
            for (int i = 0; i < 2; i++) {
                if (shared) {
                    assertSame(handle, child.f_heap.getConstHandle(local));
                } else {
                    assertNull(child.f_heap.getConstHandle(local));
                }
            }
            assertSame(handle, parent.f_heap.getConstHandle(constant));
        } finally {
            runtime.shutdownXVM();
        }
    }

    private static Container container(Runtime runtime, Container parent) {
        return new Container(runtime, parent, new FileStructure("App").getModuleId()) {
            @Override
            public ObjectHandle getInjectable(Frame frame, String name, TypeConstant type, ObjectHandle options) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
