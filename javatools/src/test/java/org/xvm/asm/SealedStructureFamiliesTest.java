package org.xvm.asm;


import java.lang.reflect.Modifier;

import java.util.Arrays;
import java.util.Set;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the sealed Component tree (sealed-hierarchy audit, stage 4), with no hatches: the
 * structure fakes that subclassed MethodStructure/PropertyStructure were retired - the test
 * package can reach the protected constructors and cloneBody() directly, and the
 * no-overridable-call-during-construction property is enforced at compile time by the fatal
 * -Xlint:this-escape gate.
 */
public class SealedStructureFamiliesTest {
    @Test
    public void componentTreeIsSealed() {
        assertTrue(Component.class.isSealed(), "Component must be sealed");
        assertEquals(
                setOf(ClassStructure.class, CompositeComponent.class, FileStructure.class,
                        MethodStructure.class, MultiMethodStructure.class,
                        PropertyStructure.class, TypedefStructure.class),
                setOf(Component.class.getPermittedSubclasses()),
                "Component permits list drifted; update the sealed-hierarchy audit");

        assertTrue(ClassStructure.class.isSealed(), "ClassStructure must be sealed");
        assertEquals(setOf(ModuleStructure.class, PackageStructure.class),
                setOf(ClassStructure.class.getPermittedSubclasses()));

        for (var leaf : new Class<?>[] {CompositeComponent.class, FileStructure.class,
                MethodStructure.class, PropertyStructure.class, MultiMethodStructure.class,
                TypedefStructure.class, ModuleStructure.class, PackageStructure.class}) {
            assertTrue(Modifier.isFinal(leaf.getModifiers()),
                    leaf.getSimpleName() + " must be final");
        }
    }

    private static Set<String> setOf(Class<?>... classes) {
        return Arrays.stream(classes).map(Class::getName).collect(Collectors.toSet());
    }
}
