package org.xvm.asm;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.BuildRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class LinkedRepositoryIsolationTest {
    @Test
    void versionedReadThroughCopiesTheModuleBeforeMutation() {
        var version = new Version("1.0");
        ModuleStructure original = new FileStructure("sample.xtclang.org").getModule();
        original.setVersion(version);
        var source = new BuildRepository();
        source.storeModule(original);

        var request = new BuildRepository();
        var repository = new LinkedRepository(true, request, source);
        ModuleStructure copy = repository.loadModule(original.getName(), version, true);

        assertNotSame(original, copy);
        assertNotSame(original.getConstantPool(), copy.getConstantPool());
        assertSame(copy, request.loadModule(original.getName()));
        copy.setVersion(new Version("2.0"));
        assertEquals(version, original.getVersion());
        assertSame(original, source.loadModule(original.getName()));
    }
}
