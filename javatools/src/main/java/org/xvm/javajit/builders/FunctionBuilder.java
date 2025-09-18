package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;

import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Builder;
import org.xvm.javajit.TypeSystem;

/**
 * The builder for Function types.
 */
public class FunctionBuilder
        extends Builder {

    public FunctionBuilder(TypeSystem typeSystem, TypeConstant functionType) {
        super(typeSystem);

        this.functionType = functionType;
    }

    protected final TypeConstant functionType;

    /**
     * Create an abstract function that in general should look like:
     * <pre><code>
     *   public abstract static class {name} extends xFunction {
     *       abstract xObj $call(Ctx ctx, xObj... params);
     *
     *       // @Override
     *       public Tuple invoke(Ctx ctx, Tuple args) {
     *          xObj retValue = $call(ctx, Tuple.unwrap(ctx, args));
     *          return xTuple.wrap(ctx, retValue, $ctx.o0, ctx.o1, ...);
     *       }
     *   }
     * </code></pre>
     */
    @Override
    public void assemblePure(String className, ClassBuilder classBuilder) {
        ConstantPool   pool        = typeSystem.pool();
        TypeConstant[] paramTypes  = pool.extractFunctionParams(functionType);
        TypeConstant[] returnTypes = pool.extractFunctionReturns(functionType);

        MethodTypeDesc callMD   = computeMethodDesc(paramTypes, returnTypes);
        MethodTypeDesc invokeMD = computeMethodDesc(paramTypes, returnTypes);

        classBuilder
            .withSuperclass(CD_xFunction)
            .withMethod("$call", callMD, ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT, methodBuilder -> {})
            .withMethodBody("$invoke", invokeMD, ClassFile.ACC_PUBLIC, code -> {
                // TODO: implement the wrapper
                Builder.defaultLoad(code, callMD.returnType());
                Builder.addReturn(code, callMD.returnType());
            }
        );
    }
}
