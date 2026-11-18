package org.xvm.asm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.net.URISyntaxException;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.MethodConstant;

/**
 * {@link ModuleApi} reaches every method of the XDK's modules: after it strips one, no method that the module file
 * names has code or source text left, and no module has a build time or a source directory.
 */
class ModuleApiStripTest {
    @Test
    void strippingTheXdkModulesLeavesNoCodeSourceTimestampOrDirectory() throws IOException, URISyntaxException {
        var resource = ModuleApiStripTest.class.getResource("/xdk-modules");
        assertNotNull(resource, "the build puts the compiled XDK modules in the test resources");
        File[] modules = new File(resource.toURI()).listFiles((dir, name) -> name.endsWith(".xtc"));
        assertTrue(modules != null && modules.length > 10, () -> "found " + Arrays.toString(modules));

        for (File module : modules) {
            FileStructure stripped = read(strip(module));
            try (var ignored = ConstantPool.withPool(stripped.getConstantPool())) {
                List<MethodStructure> methods = Arrays.stream(stripped.getConstantPool().getConstants())
                        .filter(MethodConstant.class::isInstance)
                        .map(constant -> ((MethodConstant) constant).getComponent())
                        .filter(MethodStructure.class::isInstance)
                        .map(MethodStructure.class::cast)
                        .toList();
                assertFalse(methods.isEmpty(), module.getName());
                for (MethodStructure method : methods) {
                    // named without its signature, whose description needs the modules it imports
                    assertFalse(method.hasCode(), () -> module.getName() + ": " + method.getName());
                    assertNull(method.getSourceText(), () -> module.getName() + ": " + method.getName());
                }
                for (ModuleStructure each : stripped.children()) {
                    assertNull(each.getTimestamp(), () -> module.getName() + ": " + each.getName());
                    assertNull(each.getSourceDir(), () -> module.getName() + ": " + each.getName());
                }
            }
        }
    }

    /**
     * @return the module file without what its API leaves out
     */
    private static byte[] strip(File module) throws IOException {
        FileStructure file;
        try (var in = new FileInputStream(module)) {
            file = new FileStructure(in, false, false);
        }
        try (var ignored = ConstantPool.withPool(file.getConstantPool())) {
            ModuleApi.strip(file);
            var out = new ByteArrayOutputStream();
            file.writeTo(out);
            return out.toByteArray();
        }
    }

    private static FileStructure read(byte[] bytes) throws IOException {
        return new FileStructure(new ByteArrayInputStream(bytes), false, false);
    }
}
