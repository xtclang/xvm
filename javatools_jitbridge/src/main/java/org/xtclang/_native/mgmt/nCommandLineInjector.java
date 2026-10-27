package org.xtclang._native.mgmt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.List;
import java.util.Map;

import java.util.function.Function;

import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.nEnum;
import org.xtclang.ecstasy.nType;

import org.xtclang.ecstasy.collections.ArrayᐸObjectᐳ;

import org.xtclang.ecstasy.reflect.Class;
import org.xtclang.ecstasy.reflect.Enumeration;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;
import org.xvm.javajit.Injector.Resource;
import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.Xvm;

import static org.xvm.javajit.Builder.Instance;

/**
 * A helper class to handle command line injectables.
 */
public class nCommandLineInjector {

    private final Xvm xvm;

    /**
     * The map of injections, usually parsed from the command line.
     */
    private final Map<java.lang.String, List<java.lang.String>> cliInjectables;

    /**
     * Create a {@link nCommandLineInjector}.
     *
     * @param xvm             the {@link Xvm} instance
     * @param cliInjectables  the map of injectable key/value pairs
     */
    public nCommandLineInjector(Xvm xvm, Map<java.lang.String, List<java.lang.String>> cliInjectables) {
        this.xvm            = xvm;
        this.cliInjectables = cliInjectables;
    }

    /**
     * Return a {@link Function} that will supply an instance of the required type and name
     * produced from a command line injectable key/value pair.
     *
     * <p>If there is no command line injectable key/value matching the required type and name,
     * {@code null} will be returned.
     *
     * <p>Supported injectable types are single {@link String} instances or {@link String}
     * arrays, enumeration values, and destringable types.
     *
     * @param res  the {@link Resource} containing the type and name of the injectable to produce
     *
     * @return a {@link Function} that will supply the required type from a command line
     *         injection key and value, or return {@code null} if there is no injection value for
     *         the specified name.
     */
    public Function<?, ?> getInjectable(Resource res) {
        java.lang.String name = res.name();

        // check the custom injections first
        List<java.lang.String> values = cliInjectables.get(name);
        if (values == null || values.isEmpty()) {
            // there is no matching custom injection
            return null;
        }

        // strip nullable from the required type
        TypeConstant type = res.type().removeNullable();

        Ctx ctx = xvm.getCtx();
        ConstantPool pool       = xvm.ecstasyPool;
        TypeConstant stringType = pool.typeString();

        if (type.equals(stringType)) {
            // require a single String, return the last String element
            return _ -> String.of(ctx, values.getLast());
        }

        TypeConstant stringArrayType = pool.ensureParameterizedTypeConstant(pool.typeList(), stringType);
        if (type.equals(stringArrayType)) {
            // require String[], return the whole List<String> as an array
            String[] allValues = values.stream()
                    .map(s -> String.of(ctx, s))
                    .toArray(String[]::new);
            return _ -> ArrayᐸObjectᐳ.$makeStringArray(ctx, allValues);
        }

        if (type.isEnum()) {
            // require enum value, use the last String element as the enum value name
            return _ -> createEnumValue(ctx, type, values.getLast());
        }

        TypeConstant destringableType = pool.ensureEcstasyTypeConstant("text.Destringable");
        if (type.isA(destringableType)) {
            // require Destringable, use the last String element as the Destringable value
            return _ -> createDestringable(xvm.getCtx(), type, values.getLast());
        }
        return null;
    }

    /**
     * Create an instance of an enum value.
     *
     * @param ctx    the current context
     * @param type   the enum type
     * @param value  the name of the enum value to return
     *
     * @return the enum value with the requested name
     *
     * @throws org.xtclang.ecstasy.IllegalArgument if the enum type does not have a value with
     *         the specified name
     */
    public static nEnum createEnumValue(Ctx ctx, TypeConstant type, java.lang.String value) {
        TypeSystem       typeSystem = ctx.container.typeSystem;
        java.lang.String className  = type.getJitCCName(typeSystem);
        java.lang.String enumClass  = TypeSystem.enumerationClass(className);
        ArrayᐸObjectᐳ values;

        try {
            var         clz         = typeSystem.loader.loadClass(enumClass);
            Field       field       = clz.getDeclaredField(Instance);
            Enumeration enumeration = (Enumeration) field.get(null);
            Method      method      = clz.getDeclaredMethod("values$get", Ctx.class);

            values = (ArrayᐸObjectᐳ) method.invoke(enumeration, ctx);

            for (long i = 0, c = values.size$get$p(ctx); i < c; i++) {
                nEnum e = (nEnum) values.getElement$p(ctx, i);
                if (e.name$get(ctx).toString().equals(value)) {
                    return e;
                }
            }
        } catch (ReflectiveOperationException e) {
            // this should never happen as the JIT should have generated the code for the fields
            // and methods being accessed via reflection
            throw Exception.$xvmError(ctx, "Error creating enum value", e);
        }

        java.lang.String name = type.getSingleUnderlyingClass(false).getName();
        throw Exception.$illegalArg(ctx, "Injectable " + name + "=\""
                + value + "\" does not match any names in enum " + className + " "
                + values.toString(ctx));
    }

    /**
     * Create an instance of a {@code Destringable} type.
     *
     * @param ctx    the current context
     * @param type   the type of the destringable to create an instance of
     * @param value  the string value to pass to the type's constructor
     *
     * @return a new instance of the specified {@code Destringable} type
     */
    protected Object createDestringable(Ctx ctx, TypeConstant type, java.lang.String value) {
        nType xvmType = nType.$ensureType(ctx, type);
        Class clz     = xvmType.$xvmClass(ctx);

// TODO class cXXX implements Destringable and does have a method "XXX $new(Ctx ctx, String s)" but
//  does not have the "synthetic" method "Destringable $new(Ctx ctx, String s)" that Java
//  requires to make an invoke interface call
//        return ((Destringable) clz).$new(ctx, String.of(ctx, value));
        try {
            Method $new = clz.getClass().getDeclaredMethod("$new", Ctx.class, String.class);
            return $new.invoke(clz, ctx, String.of(ctx, value));
        } catch (ReflectiveOperationException e) {
            throw Exception.$xvmError(ctx, "Error creating Destringable instance", e);
        }
    }
}
