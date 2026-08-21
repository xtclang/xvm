package org.xvm.runtime;


import java.lang.reflect.Field;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constants;
import org.xvm.asm.FileStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.VersionConstant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for runtime owner graph validation.
 */
public class OwnershipDiagnosticsTest {
    @Test
    public void cleanSyntheticContainersValidate() {
        TestContainer containerA = newContainer("DiagA");
        TestContainer containerB = newContainer("DiagB");

        OwnershipDiagnostics.Validation validation =
                OwnershipDiagnostics.validate(containerA, containerB);

        assertTrue(validation.isValid(), validation::message);
        assertDoesNotThrow(() -> OwnershipDiagnostics.assertValid(containerA, containerB));
    }

    @Test
    public void validatorRejectsForeignTemplateInOwnerCache()
            throws Exception {
        TestContainer containerA = newContainer("DiagA");
        TestContainer containerB = newContainer("DiagB");

        TestTemplate templateFromB = new TestTemplate(containerB,
                createClass(containerB.file, "ForeignTemplate"));
        cacheTemplate(containerA, templateFromB);

        OwnershipDiagnostics.Validation validation =
                OwnershipDiagnostics.validate(containerA, containerB);

        assertFalse(validation.isValid());
        assertFalse(validation.ownerMismatches().isEmpty());
        assertThrows(IllegalStateException.class,
                () -> OwnershipDiagnostics.assertValid(containerA, containerB));
    }

    @SuppressWarnings("unchecked")
    private static void cacheTemplate(Container container, ClassTemplate template)
            throws Exception {
        Field field = Container.class.getDeclaredField("f_mapTemplatesByType");
        field.setAccessible(true);

        Map<TypeConstant, ClassTemplate> templates =
                (Map<TypeConstant, ClassTemplate>) field.get(container);
        templates.put(template.getStructure().getIdentityConstant().getType(), template);
    }

    private static TestContainer newContainer(String moduleName) {
        FileStructure file = new FileStructure(moduleName);
        return new TestContainer(new Runtime(), file);
    }

    private static ClassStructure createClass(FileStructure file, String name) {
        return file.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, name, null);
    }

    private static final class TestContainer
            extends Container {
        private TestContainer(Runtime runtime, FileStructure file) {
            super(runtime, null, file.getModuleId());
            this.file = file;
        }

        @Override
        public boolean isSpecified(String name) {
            return false;
        }

        @Override
        public boolean isPresent(IdentityConstant id) {
            return false;
        }

        @Override
        public boolean isVersionMatch(ModuleConstant module, VersionConstant version) {
            return false;
        }

        @Override
        public boolean isVersion(VersionConstant version) {
            return false;
        }

        @Override
        public ObjectHandle getInjectable(Frame frame, String name, TypeConstant type,
                                          ObjectHandle opts) {
            return null;
        }

        private final FileStructure file;
    }

    private static final class TestTemplate
            extends ClassTemplate {
        private TestTemplate(Container container, ClassStructure structure) {
            super(container, structure);
        }
    }
}
