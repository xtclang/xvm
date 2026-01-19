package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Package;

import org.xvm.asm.PackageStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `package` types must extend this class.
 */
public class nPackage
    extends nConst
        implements Package {

    public nPackage(Ctx ctx, TypeConstant type) {
        super(ctx);

        $type    = type;
        $package = (PackageStructure) type.getSingleUnderlyingClass(true).getComponent();
    }

    public final TypeConstant     $type;
    public final PackageStructure $package;

    @Override
    public TypeConstant $xvmType(Ctx ctx) {
        return $type;
    }
}
