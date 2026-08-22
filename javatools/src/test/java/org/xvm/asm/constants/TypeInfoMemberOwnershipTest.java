package org.xvm.asm.constants;


import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.PropertyBody.Effect;
import org.xvm.asm.constants.TypeInfo.Progress;

import org.xvm.util.ListMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for {@link PropertyInfo}, {@link PropertyBody}, and {@link ChildInfo} ownership.
 */
public class TypeInfoMemberOwnershipTest {
    @Test
    public void propertyAndChildInfoHaveExclusiveOwners() {
        FileStructure  file   = new FileStructure("test");
        ClassStructure struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);

        PropertyStructure structProperty = struct.createProperty(false, Access.PUBLIC,
                Access.PUBLIC, struct.getCanonicalType(), "value");
        PropertyConstant idProperty = structProperty.getIdentityConstant();
        PropertyBody body = new PropertyBody(structProperty, Implementation.Native, null,
                structProperty.getType(), true, false, false, Effect.None, Effect.None,
                false, false, null, null);
        PropertyInfo property = PropertyInfo.create(body, 0);

        ClassStructure structChild = struct.createClass(
                Access.PUBLIC, Format.CLASS, "Child", null);
        ChildInfo child = new ChildInfo(structChild);
        var childCopy = new ChildInfo(structChild);

        assertEquals(child, childCopy);
        assertEquals(child.hashCode(), childCopy.hashCode());

        TypeInfo info1 = createTypeInfo(struct, idProperty, property, child);
        TypeInfo info2 = createTypeInfo(struct, idProperty, property, child);

        PropertyInfo property1 = info1.getProperties().get(idProperty);
        PropertyInfo property2 = info2.getProperties().get(idProperty);

        assertSame(info1, property1.getTypeInfo());
        assertSame(info2, property2.getTypeInfo());
        assertNotSame(property1, property2);

        assertSame(property1, property1.getHead().getPropertyInfo());
        assertSame(property2, property2.getHead().getPropertyInfo());
        assertNotSame(property1.getHead(), property2.getHead());

        assertSame(property1, info1.getVirtProperties().get(idProperty.getNestedIdentity()));
        assertSame(property2, info2.getVirtProperties().get(idProperty.getNestedIdentity()));

        assertFalse(property1.getHead().isExploded());
        assertFalse(property2.getHead().isExploded());
        property1.getHead().markExploded();
        assertTrue(property1.getHead().isExploded());
        assertFalse(property2.getHead().isExploded());

        ChildInfo child1 = info1.getChildInfosByName().get("Child");
        ChildInfo child2 = info2.getChildInfosByName().get("Child");

        assertSame(info1, child1.getTypeInfo());
        assertSame(info2, child2.getTypeInfo());
        assertNotSame(child1, child2);
        assertNotSame(child1.getAllIdentities(), child2.getAllIdentities());

        assertSame(child1, info1.getChildInfosByName().get("alias.Child"));
        assertSame(child2, info2.getChildInfosByName().get("alias.Child"));
    }

    @Test
    public void propertyInfoFactoryDoesNotCallOverridableBodyAttachment() {
        FileStructure  file   = new FileStructure("test");
        ClassStructure struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);

        PropertyStructure structProperty = struct.createProperty(false, Access.PUBLIC,
                Access.PUBLIC, struct.getCanonicalType(), "value");
        var body = new OwnerInspectingPropertyBody(structProperty, 11, 1);
        PropertyInfo property = PropertyInfo.create(body, 11);

        assertEquals(11, property.getRank());
        assertEquals(1, property.getPropertyBodies().length);
        assertSame(property, property.getHead().getPropertyInfo());
        assertNotSame(body, property.getHead());
        assertNull(body.getPropertyInfo());
    }

    private TypeInfo createTypeInfo(
            ClassStructure   struct,
            PropertyConstant idProperty,
            PropertyInfo     property,
            ChildInfo        child) {
        ListMap<String, ChildInfo> children = new ListMap<>();
        children.put("Child", child);
        children.put("alias.Child", child);

        return new TypeInfoReal(
                struct.getCanonicalType(), 0, struct, 0, false,
                Collections.emptyMap(), Annotation.NO_ANNOTATIONS, Annotation.NO_ANNOTATIONS,
                null, null, null, Collections.emptyList(), new ListMap<>(), new ListMap<>(),
                Map.of(idProperty, property), Collections.emptyMap(),
                Map.of(idProperty.getNestedIdentity(), property), Collections.emptyMap(),
                children, null, Progress.Complete);
    }

    private static final class OwnerInspectingPropertyBody extends PropertyBody {
        private final int expectedRank;
        private final int expectedBodies;

        OwnerInspectingPropertyBody(
                PropertyStructure struct,
                int               expectedRank,
                int               expectedBodies) {
            super(struct, Implementation.Native, null, struct.getType(), true, false, false,
                    Effect.None, Effect.None, false, false, null, null);

            this.expectedRank   = expectedRank;
            this.expectedBodies = expectedBodies;
        }

        @Override
        synchronized PropertyBody forProperty(PropertyInfo property) {
            if (property.getRank() != expectedRank
                    || property.getPropertyBodies().length != expectedBodies) {
                throw new IllegalStateException("property owner was observed too early");
            }
            return super.forProperty(property);
        }
    }
}
