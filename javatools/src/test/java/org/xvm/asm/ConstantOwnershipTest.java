package org.xvm.asm;

import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Component.Format;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.constants.DynamicFormalConstant;
import org.xvm.asm.constants.FSNodeConstant;
import org.xvm.asm.constants.FileStoreConstant;
import org.xvm.asm.constants.IdentityConstant.NestedIdentity;
import org.xvm.asm.constants.ParameterizedTypeConstant;
import org.xvm.asm.constants.RegisterConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConstantOwnershipTest {
    @Test
    void adoptingDefinitionsDoesNotCopyExecutionHandles() {
        var source = new FileStructure("Source");
        var pool = source.getConstantPool();
        var destination = new FileStructure(source).getConstantPool();
        var singleton = pool.ensureSingletonConstConstant(source.getModule().getIdentityConstant());
        var epoch = FileTime.fromMillis(0);
        var node = pool.register(new FSNodeConstant(pool, "file", epoch, epoch, new byte[] {1}));
        var dir = pool.register(new FSNodeConstant(pool, "dir", epoch, epoch, new FSNodeConstant[] {node}));
        var store = pool.register(new FileStoreConstant(pool, "dir", dir));
        var handle = new ObjectHandle(null) {};
        singleton.setHandle(handle);
        node.setHandle(handle);
        store.setHandle(handle);

        assertNull(destination.register(singleton).getHandle());
        assertNull(destination.register(node).getHandle());
        assertNull(destination.register(store).getHandle());
        assertSame(handle, pool.register(singleton).getHandle());
        assertSame(handle, node.getHandle());
        assertSame(handle, store.getHandle());
    }

    @Test
    void registeringASignatureCannotRebindAnUnshareableSourceType() {
        var source = new FileStructure("Source");
        var pool = source.getConstantPool();
        var destination = new FileStructure("Destination").getConstantPool();
        var type = source.getModule().createClass(Access.PUBLIC, Format.CLASS, "Actual", null)
                .getCanonicalType();
        var identity = type.getDefiningConstant();
        assertFalse(type.isShared(destination));
        assertSame(type, destination.register(type));

        var signature = pool.ensureSignatureConstant("accept", new TypeConstant[] {type}, TypeConstant.NO_TYPES);
        var registered = destination.register(signature);
        assertSame(destination, registered.getConstantPool());
        assertSame(type, registered.getRawParams()[0]);
        assertSame(pool, type.getConstantPool());
        assertSame(identity, type.getDefiningConstant());
        assertSame(pool, identity.getConstantPool());
        assertDoesNotThrow(type::getCategory);
    }

    @Test
    void adoptedNestedIdentityDoesNotRetainItsSourceIdentity() {
        var source = new FileStructure("Source");
        var pool = source.getConstantPool();
        var clz = source.getModule().createClass(Access.PUBLIC, Format.CLASS, "Holder", null);
        var outer = pool.ensurePropertyConstant(clz.getIdentityConstant(), "outer");
        var inner = pool.ensurePropertyConstant(outer, "inner");
        var original = (NestedIdentity) inner.getNestedIdentity();
        var destination = new FileStructure(source).getConstantPool();
        var copy = destination.register(inner);
        var copied = (NestedIdentity) copy.getNestedIdentity();

        assertNotSame(original, copied);
        assertSame(copy, copied.getIdentityConstant());
        assertSame(destination, copied.getIdentityConstant().getConstantPool());
        assertSame(inner, original.getIdentityConstant());
    }

    @Test
    void copiedDefinitionsDetachCompletedCompilerRegisters() {
        var source = new FileStructure("Source");
        var pool = source.getConstantPool();
        var holder = source.getModule().createClass(Access.PUBLIC, Format.CLASS, "Holder", null);
        var formal = holder.addTypeParam("Element", pool.typeObject()).getIdentityConstant();
        var method = holder.createMethod(true, Access.PUBLIC, null, Parameter.NO_PARAMS,
                "value", Parameter.NO_PARAMS, true, true).getIdentityConstant();
        var register = new Register(holder.getIdentityConstant().getType(), "value", 0);
        var binding = pool.register(new RegisterConstant(pool, register));
        var dynamic = pool.register(new DynamicFormalConstant(pool, method, "value", register, formal));
        var destination = new FileStructure(source).getConstantPool();
        var copiedBinding = destination.register(binding);
        var copiedDynamic = destination.register(dynamic);

        assertNull(copiedBinding.getRegister());
        assertNull(copiedDynamic.getRegister());
        assertSame(register, binding.getRegister());
        assertSame(register, dynamic.getRegister());
        assertSame(copiedBinding, destination.register(binding));
        assertSame(copiedDynamic, destination.register(dynamic));
        assertSame(destination, copiedDynamic.getMethod().getConstantPool());
        assertSame(destination, copiedDynamic.getFormalConstant().getConstantPool());
    }

    @Test
    void adoptedTypesHaveIndependentCalculationState() throws Exception {
        var sourceFile = new FileStructure(Constants.ECSTASY_MODULE);
        var source = sourceFile.getConstantPool();
        var destination = new FileStructure(sourceFile).getConstantPool();
        var type = source.ensureTupleType(source.typeString());
        for (var name : new String[] {"ensureConsumesMap", "ensureProducesMap", "ensureRelationMap"}) {
            var method = TypeConstant.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(type);
        }
        var copy = destination.register(type);
        for (var name : new String[] {"m_mapConsumes", "m_mapProduces", "m_mapRelations", "m_tloInProgress"}) {
            var field = TypeConstant.class.getDeclaredField(name);
            field.setAccessible(true);
            assertNull(field.get(copy), name + " must be recomputed in the destination");
        }
        var depth = TypeConstant.class.getDeclaredField("recursionDepth");
        depth.setAccessible(true);
        assertNotSame(depth.get(type), depth.get(copy));
        var lock = ParameterizedTypeConstant.class.getDeclaredField("resolutionLock");
        lock.setAccessible(true);
        assertNotSame(lock.get(type), lock.get(copy));
    }

}
