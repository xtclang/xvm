package org.xvm.javajit;


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

        container = xvm.createContainer(ts, xvm.mainInjector);
    }

    @Override
    public ConstantPool getConstantPool()
        {
        return module.getConstantPool();
        }

    @Override
    public void start(Map<String, String> mapInjections) {
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
        String typeName = ts.owned[0].pkg + ".$module";
        // uncomment for testing
        // typeName = "jit." + typeName;

        try {
            xvm.mainInjector.addNativeResources();

            TypeSystemLoader loader = Ctx.get().container.typeSystem.loader;
            Class            clz    = Class.forName(typeName, true, loader);

            Object module = clz.getDeclaredConstructor(Long.TYPE).newInstance(-1L);
            if (asArg == null || asArg.length == 0) {
                Method method = clz.getMethod("run", Ctx.class);
                method.invoke(module, Ctx.get());
            } else {
                Method method = clz.getMethod("run", String.class.arrayType(), Ctx.class);
                method.invoke(module, Ctx.get()); // TODO create xStr args
            }
            loader.dump();
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            throw new RuntimeException("Failed to load class \"" + typeName + '"', e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("No \"run()\" method", e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke \"run()\" method", e);
        }
    }

    @Override
    public void join() throws InterruptedException {}

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
}
