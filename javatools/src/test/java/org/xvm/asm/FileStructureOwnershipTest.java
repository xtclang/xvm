package org.xvm.asm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.xvm.asm.Component.Format;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ast.ConstantExprAST;
import org.xvm.asm.ast.RegisterAST;
import org.xvm.asm.ast.ReturnStmtAST;

import org.xvm.asm.op.Return_1;

import org.xvm.compiler.BuildRepository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FileStructureOwnershipTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void linkingAVersionedModulePreservesRepositoryOwnership(boolean runtime) throws IOException {
        var dependency = new FileStructure("dependency.xtclang.org").getModule();
        var version = new Version("1.0");
        dependency.setVersion(version);
        dependency.createClass(Access.PUBLIC, Format.CLASS, "Value", null);
        dependency = dependency.extractVersion(version);
        var bytes = serialize(dependency.getFileStructure());
        var type = (ClassStructure) dependency.getChild("Value");
        var identity = type.getIdentityConstant();
        var sourcePool = dependency.getConstantPool();
        var repository = new BuildRepository();
        repository.storeModule(dependency);
        var consumer = new FileStructure("consumer.xtclang.org");
        consumer.ensureModule(consumer.getConstantPool()
                .ensureModuleConstant(dependency.getName(), version)).fingerprintRequired();

        assertNull(consumer.linkModules(repository, runtime));
        assertSame(dependency, repository.loadModule(dependency.getName()));
        assertSame(identity, type.getIdentityConstant());
        assertSame(sourcePool, identity.getConstantPool());
        assertArrayEquals(bytes, serialize(dependency.getFileStructure()));
        var roundTrip = new FileStructure(new ByteArrayInputStream(serialize(consumer)));
        assertEquals(consumer.getModuleId(), roundTrip.getModuleId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"new", "decoded", "readonly", "decoded-readonly"})
    void copyingANewOrDecodedAstKeepsConstantOwnersSeparate(String state) throws IOException {
        var source = new FileStructure("Source");
        var pool = source.getConstantPool();
        var method = source.getModule().createMethod(true, Access.PUBLIC, null,
                new Parameter[] {new Parameter(pool, pool.typeString(), null, null, true, 0, false)},
                "value", Parameter.NO_PARAMS, true, true);
        var value = pool.ensureStringConstant("test");
        method.createCode().add(new Return_1(value));
        method.setAst(new ReturnStmtAST(new ConstantExprAST(value)), new RegisterAST[0]);
        if (state.startsWith("decoded")) {
            source = new FileStructure(new ByteArrayInputStream(serialize(source)));
            method = (MethodStructure) method.findThisIn(source);
            pool = source.getConstantPool();
            value = pool.ensureStringConstant("test");
        }
        var original = (ReturnStmtAST) method.getAst();
        if (state.endsWith("readonly")) {
            source.markReadOnly();
        }
        var copy = new FileStructure(source);
        var copiedMethod = (MethodStructure) method.findThisIn(copy);
        var copied = (ReturnStmtAST) copiedMethod.getAst();

        assertNotSame(original, copied);
        assertSame(value, ((ConstantExprAST) original.getExprs()[0]).getValue());
        assertSame(pool, value.getConstantPool());
        assertSame(copy.getConstantPool(), ((ConstantExprAST) copied.getExprs()[0]).getValue().getConstantPool());
        copiedMethod.forceAssembly(copy.getConstantPool());
        assertSame(value, ((ConstantExprAST) original.getExprs()[0]).getValue());
    }

    @Test
    void copyingUnassembledCodeCreatesIndependentOperations() throws Exception {
        var source = new FileStructure("Source");
        var pool = source.getConstantPool();
        var method = source.getModule().createMethod(true, Access.PUBLIC, null,
                new Parameter[] {new Parameter(pool, pool.typeString(), null, null, true, 0, false)},
                "value", Parameter.NO_PARAMS, true, true);
        var value = pool.ensureStringConstant("test");
        var original = new Return_1(value);
        method.createCode().add(original);
        var initialized = MethodStructure.class.getDeclaredField("m_fInitialized");
        initialized.setAccessible(true);
        initialized.setBoolean(method, true);
        var copy = new FileStructure(source);
        var copiedMethod = (MethodStructure) method.findThisIn(copy);
        assertNotSame(original, copiedMethod.ensureCode().getAssembledOps()[0]);
        assertEquals(false, initialized.getBoolean(copiedMethod));
        assertEquals(true, initialized.getBoolean(method));
        assertSame(pool, value.getConstantPool());
        copiedMethod.forceAssembly(copy.getConstantPool());
        assertSame(original, method.ensureCode().getAssembledOps()[0]);
    }

    @Test
    void copyingAnAstCreatesIndependentParameterRegisters() {
        var source = new FileStructure("Source");
        var pool = source.getConstantPool();
        var method = source.getModule().createMethod(true, Access.PUBLIC, null,
                new Parameter[] {new Parameter(pool, pool.typeString(), null, null, true, 0, false)},
                "identity", new Parameter[] {
                    new Parameter(pool, pool.typeString(), "value", null, false, 0, false)}, true, true);
        var parameter = new RegisterAST(pool.typeString(), pool.ensureStringConstant("value"));
        method.setAst(new ReturnStmtAST(parameter), new RegisterAST[] {parameter});
        var copy = new FileStructure(source);
        var copiedMethod = (MethodStructure) method.findThisIn(copy);
        var copiedParameter = (RegisterAST) ((ReturnStmtAST) copiedMethod.getAst()).getExprs()[0];

        assertNotSame(parameter, copiedParameter);
        assertEquals(parameter.getRegId(), copiedParameter.getRegId());
        assertSame(pool, parameter.getNameConstant().getConstantPool());
        assertSame(copy.getConstantPool(), copiedParameter.getNameConstant().getConstantPool());
        copiedMethod.forceAssembly(copy.getConstantPool());
        assertSame(pool, parameter.getNameConstant().getConstantPool());
    }

    private static byte[] serialize(FileStructure file) throws IOException {
        var bytes = new ByteArrayOutputStream();
        file.writeTo(bytes);
        return bytes.toByteArray();
    }
}
