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

import org.xvm.javajit.intrinsic.xObj;


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

        container = xvm.createContainer(ts, xvm.DefaultMainInjector);
        // TODO reflect on container to get the main module and find the appropriate run() (or other) method

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
        String typeName = "jit." + ts.owned[0].pkg + ".$module";
        try {
            Class        typeClz    = Class.forName(typeName);
            xObj module = (xObj) typeClz.getDeclaredConstructor(Long.TYPE).newInstance(-1L);
            if (asArg == null || asArg.length == 0) {
                Method method = typeClz.getMethod("run");
                method.invoke(module);
            } else {
                Method method = typeClz.getMethod("run", String.class.arrayType());
                method.invoke(module); // TODO create xStr args
            }
        } catch (ClassNotFoundException e) {
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
