package org.xtclang.ecstasy.text;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.xtclang.ecstasy.nType;
import org.xtclang.ecstasy.reflect.Class;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.javajit.Ctx;

/**
 * Native representation of `ecstasy.text.Destringable`.
 */
public interface Destringable {

    /**
     * The native declaration of the constructor {@code construct(String text);}.
     */
    Destringable $new(Ctx ctx, String s);

    /**
     * A helper method to create an instance of a {@link Destringable} type.
     *
     * @param ctx          the current context
     * @param type         the type of the destringable to create an instance of
     * @param stringValue  the string value to pass to the type's constructor
     *
     * @return a new instance of the specified {@link Destringable} type
     */
    static Object $createInstance(Ctx ctx, TypeConstant type, java.lang.String stringValue) {
        nType nType = org.xtclang.ecstasy.nType.$ensureType(ctx, type);
        Class clz   = nType.$xvmClass(ctx);

// TODO class cXXX does have method "XXX $new(Ctx ctx, String s)" but does not have method
//  "Destringable $new(Ctx ctx, String s)"
//            return opts -> ((Destringable) clz).$new(ctx, String.of(ctx, value));
        try {
            Method $new  = clz.getClass().getDeclaredMethod("$new", Ctx.class, String.class);
            return $new.invoke(clz, ctx, String.of(ctx, stringValue));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
