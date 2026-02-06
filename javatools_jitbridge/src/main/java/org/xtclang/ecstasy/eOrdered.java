package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;
import org.xtclang.ecstasy.text.String;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Native Enumeration<Ordered>.
 */
public class eOrdered extends Enumeration {
    private eOrdered() {
        super(null);
    }

    public static final eOrdered $INSTANCE = new eOrdered();

    public static final String[]  $names  = new String[] {Ordered.Lesser.$name, Ordered.Equal.$name, Ordered.Greater.$name};
    public static final Ordered[] $values = new Ordered[] {Ordered.Lesser, Ordered.Equal, Ordered.Greater};

    @Override public TypeConstant $xvmType(Ctx ctx) {
        ConstantPool pool = ctx.container.typeSystem.pool();
        return pool.ensureClassTypeConstant(pool.clzClass(), null, pool.typeOrdered());
    }

    @Override
    public long count$get$p() {
        return 3;
    }

    @Override
    public Ordered[] values$get() {
        return $values;
    }

    @Override
    public String[] names$get() {
        return $names;
    }
}
