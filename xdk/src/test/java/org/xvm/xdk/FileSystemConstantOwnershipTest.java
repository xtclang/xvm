package org.xvm.xdk;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.xvm.asm.Constant;
import org.xvm.asm.DirRepository;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.Op;

import org.xvm.asm.constants.FSNodeConstant;
import org.xvm.asm.constants.FileStoreConstant;

import org.xvm.runtime.MainContainer;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Runtime;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Materialize embedded file-system definitions using the distribution provisioned by the XDK task.
 */
class FileSystemConstantOwnershipTest {
    @Test
    @Timeout(60)
    void fileSystemValuesBelongToTheirExecutionHeaps() {
        var runtime = new Runtime();
        try {
            var root = new NativeContainer(runtime, repository());
            var file = root.createFileStructure(new FileStructure("FileSystemValues").getModule());
            var pool = file.getConstantPool();
            var epoch = FileTime.fromMillis(0);
            var node = pool.register(new FSNodeConstant(pool, "file", epoch, epoch, new byte[] {1, 2}));
            var directory = pool.register(new FSNodeConstant(pool, "dir", epoch, epoch,
                    new FSNodeConstant[] {node}));
            var store = pool.register(new FileStoreConstant(pool, "dir", directory));
            var first = new MainContainer(runtime, root, file.getModuleId());
            var second = new MainContainer(runtime, root, file.getModuleId());
            var constants = pool.getConstants();
            first.getTypeContext().freezeDefinitions();
            second.getTypeContext().freezeDefinitions();
            for (Constant definition : List.of(node, directory, store)) {
                var firstValue = materialize(first, definition);
                var secondValue = materialize(second, definition);
                assertNotSame(firstValue, secondValue);
                assertSame(first, firstValue.getComposition().getContainer());
                assertSame(second, secondValue.getComposition().getContainer());
                first.getTypeContext().clearMetadata();
                second.getTypeContext().clearMetadata();
                assertSame(firstValue, materialize(first, definition));
                assertSame(secondValue, materialize(second, definition));
            }
            assertArrayEquals(new byte[] {1, 2}, node.getFileBytes());
            assertArrayEquals(constants, pool.getConstants());
        } finally {
            runtime.shutdownXVM();
        }
    }

    private static ObjectHandle materialize(MainContainer container, Constant definition) {
        var function = new NativeFunctionHandle((frame, args, result) -> {
            var value = frame.getConstHandle(definition);
            return Op.isDeferred(value)
                    ? value.proceed(frame, caller -> caller.assignValue(result, caller.popStack()))
                    : frame.assignValue(result, value);
        });
        return container.ensureServiceContext().postRequest(null, function, Utils.OBJECTS_NONE, 1).join();
    }

    private static ModuleRepository repository() {
        var installed = Path.of("build", "install", "xdk");
        return new LinkedRepository(new DirRepository(installed.resolve("lib").toFile(), true),
                new DirRepository(installed.resolve("javatools").toFile(), true));
    }
}
