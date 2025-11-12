package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Type;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `Type` types must extend this class.
 */
public class xType
        extends xConst
        implements Type {

    private xType(Ctx ctx, TypeConstant type) {
        super(ctx);

        $type = type;
    }

    public final TypeConstant $type;

    public xObj alloc(Ctx ctx) {
        throw Exception.$unsupported(ctx, "Type " + $type);
    }

    @Override public TypeConstant $xvmType(Ctx ctx) {
        return $type.getType();
    }

    @Override public boolean $isA(Ctx ctx, xType t) {
        return $type.isA(t.$type);
    }

    @Override
    public Boolean structConstructor(Ctx ctx, Type OuterType, xObj outer) {
        throw Exception.$unsupported(ctx, "Type " + $type);
    }

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, $type.getValueString());
    }

    /**
     * @return xType instance for the specified type
     */
    public static xType $ensureType(Ctx ctx, TypeConstant type) {
        return (xType) type.ensureXType(() -> new xType(ctx, type));
    }
}
