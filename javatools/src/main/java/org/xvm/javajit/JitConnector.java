package org.xvm.javajit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.function.Predicate;

import java.util.stream.Stream;

import org.xvm.api.Connector;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.TypeConstant;

public class JitConnector
        extends Connector {
    public JitConnector(ModuleRepository repo) {
        this(repo, (Path) null);
    }

    /**
     * Create a JIT runtime with an optional explicit template location.
     */
    public JitConnector(ModuleRepository repo, Path jitBridge) {
        this(repo, new Xvm(repo, jitBridge), null, true);
    }

    /**
     * Create one application connector within an existing runtime. Embedded execution writes only
     * to the supplied console and does not dump classes into the process working directory.
     */
    public JitConnector(ModuleRepository repo, Xvm xvm, PrintWriter console) {
        this(repo, xvm, console, false);
    }

    private JitConnector(ModuleRepository repo, Xvm xvm, PrintWriter console, boolean dumpClasses) {
        super(repo);
        this.xvm         = xvm;
        this.console     = console;
        this.dumpClasses = dumpClasses;
    }

    @Override
    public void loadModule(String appName) {
        ModuleStructure loaded = f_repository.loadModule(appName);
        if (loaded == null) {
            throw new IllegalStateException("Unable to load module \"" + appName + "\"");
        }
        loadModule(loaded);
    }

    /**
     * Load a private copy: linking and code generation must not mutate the caller's module.
     *
     * <p>Deserialize the application into a new pool before linking, so cached handles and
     * materialized AST objects from the source are not reused as execution state. Bind the new
     * pool while linking for helpers that still use ambient selection. If linking reuses a type
     * system, its selected main module owns the entry definitions and generated classes; the
     * temporary candidate pool must not supply those identities.
     */
    public void loadModule(ModuleStructure loaded) {
        try {
            var bytes = new ByteArrayOutputStream();
            loaded.getFileStructure().writeTo(bytes);
            var file = new FileStructure(new ByteArrayInputStream(bytes.toByteArray()), true, false);
            try (var ignore = ConstantPool.withPool(file.getConstantPool())) {
                var linker = xvm.createLinker().withRepo(f_repository).addModule(file.getModule());
                ts = linker.link();
                if (ts == null) {
                    throw new IllegalStateException("Unable to link module: " + linker.errorList().getErrors());
                }
                // A matching type system may already exist. Entry methods must come from the
                // module that owns its generated classes, not the discarded candidate copy.
                module = ts.mainModule();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ConstantPool getConstantPool() {
        return ts == null ? xvm.ecstasyPool : ts.pool();
    }

    @Override
    public void start(Map<String, List<String>> mapInjections) {
        try {
            var loader   = xvm.nativeTypeSystem.loader;
            var clz      = loader.loadClass("org.xtclang._native.mgmt.nMainInjector")
                                 .asSubclass(Injector.class);
            var injector = clz.getDeclaredConstructor(Xvm.class, PrintWriter.class, Map.class)
                              .newInstance(xvm, console, mapInjections == null ? Map.of() : mapInjections);
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
        try (var ignore = ConstantPool.withPool(getConstantPool())) {
            return findMethods(module.getIdentityConstant(), sMethodName);
        }
    }

    @Override
    public void invoke0(MethodStructure methodStructure, String... args) {
        try (var ignore = ConstantPool.withPool(getConstantPool())) {
            container.newFiber(() -> invoke0Impl(methodStructure, args));
        }
    }

    private void invoke0Impl(MethodStructure methodStructure, String... args) {
        String typeName = ts.owned[0].module.getIdentityConstant().getType().ensureJitClassName(ts);

        TypeSystemLoader loader    = ts.loader;
        ConstantPool     pool      = ts.pool();
        Set<String>      dumpNames = new HashSet<>(Arrays.asList(CLASS_DUMP_LIST));
        try {
            Class  mainClass = loader.loadClass(typeName);
            Ctx    ctx       = xvm.getCtx();
            Object module    = mainClass.getDeclaredConstructor(Ctx.class).newInstance(ctx);

            // Reflection boxes the optimized Java long as Long, which join() uses as the exit code.
            String runName = methodStructure.getIdentityConstant().ensureJitMethodName(ts);
            if (methodStructure.getReturnCount() == 1 &&
                    methodStructure.getReturn(0).getType().equals(pool.typeInt64())) {
                runName += "$p";
            }
            Object result;
            if (methodStructure.getParamCount() == 0) {
                Method runMethod = mainClass.getMethod(runName, Ctx.class);
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
                Class       objectClass = loader.loadClass(Builder.N_Object);
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
                Method runMethod = mainClass.getMethod(runName, Ctx.class, arrayClass);
                result = runMethod.invoke(module, ctx, stringArray);
            }
            switch (result) {
                case Long        l -> this.result = l;
                case null, default -> this.result = 0;
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            throw new RuntimeException("Failed to load class \"" + typeName + '"', e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("No entry method: " + methodStructure.getName(), e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Failed to invoke " + methodStructure.getName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            String    name  = cause.getClass().getSimpleName();
            if (name.startsWith(TypeSystem.ClassfileShape.Exception.prefix) ||
                name.charAt(0) == TypeSystem.NO_MOD) {

                this.result = 1;
                try {
                    failure = "Unhandled exception: " + cause.getClass().getField("exception").get(cause);
                } catch (ReflectiveOperationException ignored) {
                    failure = "Unhandled exception: " + cause;
                }
                if (console == null) {
                    System.out.println(failure);
                } else {
                    console.println(failure);
                    console.flush();
                }
            } else {
                if (cause instanceof VerifyError) {
                    dumpNames.add(extractVerifyErrorClassName(cause.getMessage()));
                }
                throw new RuntimeException(cause);
            }
        } finally {
            if (dumpClasses) {
                dumpClasses(loader, dumpNames);
            }
        }
    }

    private void dumpClasses(TypeSystemLoader loader, Set<String> dumpNames) {
        // dump the generated classes.
        // each class will be dumped to a separate file under a directory
        // ./jasm/<module-name>/<class-name>.jasm
        Predicate<String> filter     = s -> dumpNames.stream().anyMatch(s::contains);
        File              curDir     = new File(".").getAbsoluteFile();
        File              jasmDir    = new File(curDir, "jasm");
        String            moduleName = loader.typeSystem.mainModule().getSimpleName();
        File              moduleDir  = new File(jasmDir, moduleName);
        File              ecstasyDir = new File(jasmDir, "ecstasy");

        // delete the existing jasm directory
        try (Stream<Path> paths = Files.walk(jasmDir.toPath())) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        moduleDir.mkdirs();
        loader.dump(moduleDir, filter);
        ecstasyDir.mkdirs();
        xvm.nativeTypeSystem.loader.dump(ecstasyDir, filter);
    }

    /**
     * Extract the class name from the VerifyError message that looks like:
     *
     *      VarifyError
     *      Location:
     *          org/xtclang/ecstasy/[ClassName].[MethodName]([MethodSignature])
     */
    private String extractVerifyErrorClassName(String message) {
        int locationIndex = message.indexOf("Location:");
        if (locationIndex == -1) {
            return "";
        }

        String tail     = message.substring(locationIndex + "Location:".length());
        int    dotIndex = tail.indexOf('.');
        return dotIndex == -1 ? "" : tail.substring(0, dotIndex).trim();
    }

    @Override
    public int join() {
        return (int) result;
    }

    /**
     * @return the complete Ecstasy Int result, without narrowing to a process exit code
     */
    public long result() {
        return result;
    }

    /**
     * @return an uncaught Ecstasy exception description, or null after normal completion
     */
    public String failure() {
        return failure;
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

    private String failure;
    private final PrintWriter console;
    private final boolean dumpClasses;

    // TEMPORARY: manually added names
    private static final String[] CLASS_DUMP_LIST = new String[] {
            "¤module"
    };
}
