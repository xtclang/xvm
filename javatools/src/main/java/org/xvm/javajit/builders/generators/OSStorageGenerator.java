package org.xvm.javajit.builders.generators;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.builders.AugmentingBuilder;

import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.md;

/**
 * The {@link JitGenerated.Generator} implementation for the {@code ecstasy.fs.OSStorage} type.
 */
public class OSStorageGenerator
        implements JitGenerated.Generator<MethodModel> {

    @Override
    public void generate(AugmentingBuilder builder, MethodModel model, ClassBuilder classBuilder) {
        assert model.methodName().equalsString("instance");

        MethodTypeDesc methodTypeDesc = model.methodTypeSymbol();
        String         name           = model.methodName().stringValue();
        int            flags          = model.flags().flagsMask();
        ClassDesc      cd             = builder.art.CD();

        classBuilder.withMethodBody(name, methodTypeDesc, flags, code -> {
            // generate the following:
            // return new OSStorage(ctx);
            code.new_(cd)
                .dup()
                .aload(code.parameterSlot(0))
                .invokespecial(cd, INIT_NAME, md(CD_void, CD_Ctx))
                .areturn();
        });
    }
}
