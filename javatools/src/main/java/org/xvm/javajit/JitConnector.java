package org.xvm.javajit;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.api.Connector;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.TypeConstant;

public class JitConnector
        extends Connector {
    public JitConnector(ModuleRepository repo) {
        super(repo);

        xvm = new Xvm(repo);
    }

    @Override
    public void loadModule(String appName) {
        module = f_repository.loadModule(appName);
        if (module == null) {
            throw new IllegalStateException("Unable to load module \"" + appName + "\"");
        }

        ts = xvm.createLinker().addModule(module).link();
        // TODO add error reporting
    }

    @Override
    public ConstantPool getConstantPool() {
        return module.getConstantPool();
    }

    @Override
    public void start(Map<String, List<String>> mapInjections) {
        try {
            var loader   = xvm.nativeTypeSystem.loader;
            var clz      = loader.loadClass("org.xtclang._native.mgmt.nMainInjector")
                                 .asSubclass(Injector.class);
            var injector = clz.getDeclaredConstructor(Xvm.class).newInstance(xvm);
            try (var ignore = ConstantPool.withPool(xvm.nativeTypeSystem.pool())) {
                clz.getMethod("addNativeResources").invoke(injector);
            }
            container = xvm.createContainer(ts, injector);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            throw new RuntimeException("Failed to load nMainInjector", e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke \"addNativeResources()\" method", e);
        }
    }

    @Override
    public Set<MethodStructure> findMethods(String sMethodName) {
        return findMethods(module.getIdentityConstant(), sMethodName);
    }

    @Override
    public void invoke0(MethodStructure methodStructure, String... args) {
        ScopedValue.where(Ctx.Current, new Ctx(xvm, container)).run(
            () -> invoke0Impl(methodStructure, args));
    }

    public void invoke0Impl(MethodStructure methodStructure, String... args) {
        String typeName = ts.owned[0].module.getIdentityConstant().getType().ensureJitClassName(ts);

        TypeSystemLoader loader = ts.loader;
        ConstantPool     pool   = ts.pool();
        try {
            Class  mainClass = loader.loadClass(typeName);
            Ctx    ctx       = Ctx.get();
            Object module    = mainClass.getDeclaredConstructor(Ctx.class).newInstance(ctx);

            Object result;
            if (methodStructure.getParamCount() == 0) {
                Method runMethod = mainClass.getMethod("run", Ctx.class);
                result = runMethod.invoke(module, ctx);
            } else {
                TypeConstant stringArrayType = pool.ensureArrayType(pool.typeString());

                assert methodStructure.getParamCount() == 1 &&
                    methodStructure.getParam(0).getType().equals(stringArrayType);

                // ArrayᐸObjectᐳ stringArray = ArrayᐸObjectᐳ.new$p(ctx, arrayType, capacity, false);
                Class       arrayClass  = loader.loadClass(Builder.N_ArrayObj);
                Method      newMethod   = arrayClass.getDeclaredMethod("$new$p", Ctx.class, TypeConstant.class, Long.TYPE, Boolean.TYPE);
                Object      stringArray = newMethod.invoke(null, ctx, stringArrayType, (long) args.length, false);
                Class       stringClass = loader.loadClass(Builder.N_String);
                Class       objectClass = loader.loadClass(Builder.N_nObj);
                Constructor stringCtor  = stringClass.getDeclaredConstructor(Ctx.class, String.class);
                Method      addMethod   = arrayClass.getDeclaredMethod("add", Ctx.class, objectClass);
                for (String arg : args) {
                    // stringArray.add(arg);
                    addMethod.invoke(stringArray, ctx, stringCtor.newInstance(ctx, arg));
                }

                // stringArray.makeImmutable();
                Class  baseArrayClass  = loader.loadClass(Builder.N_Array);
                Method makeImmutMethod = baseArrayClass.getDeclaredMethod("makeImmutable", Ctx.class);
                stringArray = makeImmutMethod.invoke(stringArray, ctx);

                // run(stringArray);
                Method runMethod = mainClass.getMethod("run$p", Ctx.class, arrayClass);
                result = runMethod.invoke(module, ctx, stringArray);
            }
            switch (result) {
                case Long        l -> this.result = l;
                case null, default -> this.result = 0;
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            e.printStackTrace(System.err);
            throw new RuntimeException("Failed to load class \"" + typeName + '"', e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("No \"run()\" method", e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Failed to invoke \"run()\" method", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            String    name  = cause.getClass().getSimpleName();
            if (name.startsWith(TypeSystem.ClassfileShape.Exception.prefix)) {
                try {
                    // TODO: add the service info; see Utils.log()
                    System.out.println("\nUnhandled exception: " +
                        cause.getClass().getField("exception").get(cause));
                } catch (Throwable ignore) {}
            } else {
                e.printStackTrace(System.err);
                throw new RuntimeException(cause);
            }
        } finally {
            try {
                // dump the generated classes
                loader.dump(new PrintStream(
                    new FileOutputStream(loader.typeSystem.mainModule().getSimpleName() + ".jasm")));
                xvm.nativeTypeSystem.loader.dump(
                            new PrintStream(new FileOutputStream("ecstasy.jasm")));
            } catch (IOException ignore) {}
        }
    }

    @Override
    public int join() {
        return (int) result;
    }

    /**
     * The XVM within which this TypeSystem exists
     */
    public final Xvm xvm;

    /**
     * The type system for the main container.
     */
    private TypeSystem ts;

    /**
     * The main module.
     */
    private ModuleStructure module;

    /**
     * The main container.
     */
    private Container container;

    /**
     * The result of "main" method invocation.
     */
    private long result = 1;
}
