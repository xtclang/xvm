package org.xvm.javajit;

import java.io.IOException;
import java.io.PrintWriter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.api.Connector;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import static java.nio.charset.StandardCharsets.UTF_8;

public class JitConnector extends Connector {
    public JitConnector(final ModuleRepository repo) {
        super(repo);
        xvm = new Xvm(repo);
    }

    @Override
    public void loadModule(final String appName) {
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
    public void start(final Map<String, String> mapInjections) {
        try {
            var loader = xvm.nativeTypeSystem.loader;
            var clz = loader.loadClass("org.xtclang._native.mgmt.nMainInjector");
            var injector = (Injector) clz.getDeclaredConstructor(Xvm.class).newInstance(xvm);
            try (var _ = ConstantPool.withPool(xvm.nativeTypeSystem.pool())) {
                clz.getMethod("addNativeResources").invoke(injector);
            }
            container = xvm.createContainer(ts, injector);
        } catch (final ClassNotFoundException | NoClassDefFoundError e) {
            throw new RuntimeException("Failed to load nMainInjector", e);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke \"addNativeResources()\" method", e);
        }
    }

    @Override
    public Set<MethodStructure> findMethods(final String sMethodName) {
        return findMethods(module.getIdentityConstant(), sMethodName);
    }

    @Override
    public void invoke0(final MethodStructure method, final List<String> args) {
        ScopedValue.where(Ctx.Current, new Ctx(xvm, container)).run(() -> invoke0Impl(method, args));
    }

    private void invoke0Impl(final MethodStructure methodStructure, final List<String> args) {
        String typeName = ts.owned[0].module.getIdentityConstant().getType().ensureJitClassName(ts);
        TypeSystemLoader loader = ts.loader;

        try {
            var clz = loader.loadClass(typeName);
            var ctx = Ctx.get();
            var mod = clz.getDeclaredConstructor(Ctx.class).newInstance(ctx);

            Object res;
            if (args.isEmpty()) {
                Method method = clz.getMethod("run", Ctx.class);
                res = method.invoke(mod, ctx);
            } else {
                Method method = clz.getMethod("run", Ctx.class, String[].class);
                res = method.invoke(mod, ctx, args.toArray(String[]::new));
            }
            if (res instanceof final Long l) {
                this.result = l;
            }
        } catch (final ClassNotFoundException | NoClassDefFoundError e) {
            e.printStackTrace(System.err);
            throw new RuntimeException("Failed to load class \"" + typeName + '"', e);
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException("No \"run()\" method", e);
        } catch (final InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Failed to invoke \"run()\" method", e);
        } catch (final InvocationTargetException e) {
            handleInvocationException(e);
        } finally {
            dumpGeneratedClasses(loader);
        }
    }

    private static void handleInvocationException(final InvocationTargetException e) {
        Throwable cause = e.getCause();
        String name = cause.getClass().getSimpleName();
        if ("xException".equals(name) || name.startsWith(TypeSystem.ClassfileShape.Exception.prefix)) {
            try {
                // TODO: add the service info; see Utils.log()
                System.out.println("\nUnhandled exception: " + cause.getClass().getField("exception").get(cause));
            } catch (final ReflectiveOperationException _) {
                // ignored
            }
        } else {
            e.printStackTrace(System.err);
            throw new RuntimeException(cause);
        }
    }

    private void dumpGeneratedClasses(final TypeSystemLoader loader) {
        dumpToFile(loader, loader.typeSystem.mainModule().getSimpleName() + ".jasm");
        dumpToFile(xvm.nativeTypeSystem.loader, "ecstasy.jasm");
    }

    private static void dumpToFile(final TypeSystemLoader loader, final String filename) {
        try (var out = new PrintWriter(Files.newBufferedWriter(Path.of(filename), UTF_8))) {
            loader.dump(out);
        } catch (final IOException ignored) {}
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
