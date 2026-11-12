package org.xvm.javajit.builders;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.constants.PropertyClassTypeConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

/**
 * Builds self-contained annotated static properties, with storage and methods on the Ref itself.
 */
public class StaticPropertyRefBuilder extends RefBuilder {
    public StaticPropertyRefBuilder(TypeSystem typeSystem, Artifact art) {
        super(typeSystem, art);

        this.propertyType = (PropertyClassTypeConstant) art.type();
    }

    private final PropertyClassTypeConstant propertyType;

    @Override
    public TypeConstant getSuperType() {
        // all annotation bodies belong here; the superclass supplies only native storage
        return propertyType.getRefType().removeAnnotations();
    }

    @Override
    protected void callSuperInitializer(CodeBuilder code) {
        PropertyInfo prop = propertyType.getPropertyInfo();

        // this Ref owns the static property's value and annotation state; its get/set methods use
        // this storage, so initialize nRef with its value-backed constructor leaving the referent
        // unassigned; it will be set by annotation constructors or later annotation operations
        // (e.g. Lazy.get() calling set(calc()) on the first read)

        // super(ctx, null, referentType, isVar);
        code.aload(0)
            .aload(code.parameterSlot(0))
            .aconst_null();
        loadTypeConstant(code, prop.getType());
        loadBoolean(code, prop.isVar());
        code.invokespecial(CD_nRef, INIT_NAME,
                md(CD_void, CD_Ctx, CD_nObject, CD_TypeConstant, CD_boolean));
    }
}
