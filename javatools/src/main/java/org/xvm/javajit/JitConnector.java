package org.xvm.javajit;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.Map;
import java.util.Set;

import org.xvm.api.Connector;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

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
    public ConstantPool getConstantPool()
        {
        return module.getConstantPool();
        }

    @Override
    public void start(Map<String, String> mapInjections) {
        try {
            ClassLoader loader = xvm.nativeTypeSystem.loader;
            Class       clz    = loader.loadClass("org.xtclang._native.mgmt.xMainInjector");

            Injector injector = (Injector) clz.getDeclaredConstructor(Xvm.class).newInstance(xvm);
            try (var ignore = ConstantPool.withPool(xvm.nativeTypeSystem.pool())) {
                clz.getMethod("addNativeResources").invoke(injector);
            }

            container = xvm.createContainer(ts, injector);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            throw new RuntimeException("Failed to load xMainInjector", e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke \"addNativeResources()\" method", e);
        }
    }

    @Override
    public Set<MethodStructure> findMethods(String sMethodName) {
        return findMethods(module.getIdentityConstant(), sMethodName);
    }

    @Override
    public void invoke0(MethodStructure methodStructure, String... asArg) {
        ScopedValue.where(Ctx.Current, new Ctx(xvm, container)).run(
            () -> invoke0Impl(methodStructure, asArg));
    }

    public void invoke0Impl(MethodStructure methodStructure, String... asArg) {
        String typeName = ts.owned[0].module.getIdentityConstant().getType().ensureJitClassName(ts);

        try {
            TypeSystemLoader loader = ts.loader;
            Class            clz    = loader.loadClass(typeName);

            Ctx    ctx    = Ctx.get();
            Object module = clz.getDeclaredConstructor(Ctx.class).newInstance(ctx);

            // dump the generated classes
            loader.dump(new PrintStream(
                new FileOutputStream(loader.typeSystem.mainModule().getSimpleName() + ".jasm")));
            xvm.nativeTypeSystem.loader.dump(
                        new PrintStream(new FileOutputStream("ecstasy.jasm")));

            Object result;
            if (asArg == null || asArg.length == 0) {
                Method method = clz.getMethod("run", Ctx.class);
                result = method.invoke(module, ctx);
            } else {
                Method method = clz.getMethod("run", Ctx.class, String.class.arrayType());
                result = method.invoke(module, ctx, asArg); // TODO create xStr args
            }
            if (result instanceof Long lr) {
                this.result = lr;
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load class \"" + typeName + '"', e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("No \"run()\" method", e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Failed to invoke \"run()\" method", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause.getClass().getSimpleName().equals("xException")) {
                try {
                    System.out.println(cause.getClass().getDeclaredField("exception").get(cause));
                } catch (Throwable ignore) {}
            } else {
                e.printStackTrace();
                throw new RuntimeException(cause);
            }
        }
    }

    /**
     * Print a JIT exception.
     */
    private void printFilteredException(Throwable e) {
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
