package org.xvm.javajit;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.function.Function;

import org.xvm.asm.LinkerContext;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.VersionConstant;

import static org.xvm.util.Handy.require;

/**
 * Represents an Ecstasy `Container`.
 */
public class Container
        implements LinkerContext {
    /**
     * Construct a new Ecstasy container.
     *
     * @param parent      the parent Container that is creating this Container
     * @param id          the internal id of this Container; -1 is the native container, 0 is the
     *                    main container, and all other values (where n>0) indicate child containers
     *                    created by Ecstasy code
     * @param typeSystem  the TypeSystem for this Container
     * @param injector    the resource provider
     */
    Container(Container parent, long id, TypeSystem typeSystem, Injector injector) {
        if (id < 0) {
            if (id == -1) {
                if (parent != null) {
                    throw new IllegalArgumentException("the native container (-1) can not have a parent");
                }
            } else {
                throw new IllegalArgumentException("illegal id: " + id);
            }
        } else {
            require("parent", parent);
        }
        require("typeSystem", typeSystem);
        require("injector", injector);

        this.xvm        = typeSystem.xvm;
        this.parent     = parent;
        this.id         = id;
        this.typeSystem = typeSystem;
        this.injector   = injector;
    }

    /**
     * The Xvm instance within which this Container exists.
     */
    public final Xvm xvm;

    /**
     * The Container within which this Container was created. The parent Container can only be null
     * iff this is the native Container (id == -1).
     */
    public final Container parent;

    /**
     * The Ecstasy TypeSystem used by this Container.
     */
    public final TypeSystem typeSystem;

    /**
     * The Injector that provides the values for dependency injection into this Container.
     */
    public final Injector injector;

    /**
     * The internal numeric identity of the Container, with -1 being the "native" container, 0 being
     * the "main" container, and >0 being child containers.
     */
    public final long id;

    /**
     * Static property values scoped to this Container.
     */
    private final Map<MethodHandle, Object> staticValues = new ConcurrentHashMap<>();

    /**
     * Handle used to adapt a static property initializer by obtaining the current Ctx from its
     * Container argument.
     */
    private static final MethodHandle GetCtx;

    /**
     * Handles used to compose: container -> container.injector.supplierOf(type, name).apply(opts)
     */
    private static final MethodHandle GetInjector;
    private static final MethodHandle SupplierOf;
    private static final MethodHandle Apply;

    // ----- Container API -------------------------------------------------------------------------

    /**
     * @return true iff the Container is the "core" (or "native") container, which is responsible
     *         for loading the core Ecstasy type system and interfacing with the "native" world
     */
    public boolean isCore() {
        return id == -1;
    }

    /**
     * @return true iff the Container is a "main" container (often referred to as "container zero",
     *         although this implementation allows for more than one main container)
     */
    public boolean isMain() {
        return parent.isCore();
    }

    /**
     * @return true iff the Container is a nested Container, which means that it is neither a "main"
     *         Container nor the "core" Container
     */
    public boolean isNested() {
        return !isCore() && !isMain();
    }

    // TODO create child container
    // TODO control surface area
    // TODO stats surface area

    // ----- memory accounting ---------------------------------------------------------------------

    // TODO
    // public long committed()
    // public long allocated()

    // ----- invocation ----------------------------------------------------------------------------

    public void newFiber(Runnable task) {
        ScopedValue.where(xvm.Current, new Ctx(xvm, this)).run(task);
    }

    // ----- static property support ---------------------------------------------------------------

    /**
     * Obtain the value produced by the specified computation handle, computing it at most once for
     * this container. Every container uses the same handle to obtain and cache its own value.
     */
    public Object computeStatic(MethodHandle computation) {
        return staticValues.computeIfAbsent(computation, handle -> {
            try {
                return handle.invokeExact(this);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * Create a MethodHandle of type {@code (Container)Object} that computes an injected value as:
     * {@code container.injector.supplierOf(resourceType, resourceName).apply(opts)}.
     *
     * The container remains an argument rather than being bound into the handle, allowing the
     * generated class containing the handle to be shared by multiple containers.
     *
     * This method is used by {@link org.xvm.javajit.builders.CommonBuilder#assembleCLInit}.
     */
    public static MethodHandle createInjectionHandle(
            TypeConstant resourceType, String resourceName, Object opts) {
        MethodHandle supplier = MethodHandles.insertArguments(
                SupplierOf, 1, resourceType, resourceName);
        supplier = MethodHandles.filterArguments(supplier, 0, GetInjector);

        MethodHandle apply = MethodHandles.insertArguments(Apply, 1, opts);
        return MethodHandles.filterArguments(apply, 0, supplier);
    }

    /**
     * Create a MethodHandle of type {@code (Container)Object} that computes a call to the static
     * property initializer "init" as {@code init(Ctx)}.
     *
     * The container remains an argument rather than being bound into the handle, allowing the
     * generated class containing the handle to be shared by multiple containers.
     *
     * This method is used by {@link org.xvm.javajit.builders.CommonBuilder#assembleCLInit}.
     */
    public static MethodHandle createInitializerHandle(MethodHandle initializer) {
        initializer = initializer.asType(MethodType.methodType(Object.class, Ctx.class));
        return MethodHandles.filterArguments(initializer, 0, GetCtx);
    }

    // ----- LinkerContext interface ---------------------------------------------------------------

    @Override
    public boolean isSpecified(String sName) {
        // TODO CP: environment based?
        return switch (sName) {
            case "debug", "test" -> true;
            default              -> false;
        };
    }

    @Override
    public boolean isPresent(IdentityConstant constId) {
        // TODO CP: is this sufficient - part of the Ecstasy module?
        return constId.getModuleConstant().equals(typeSystem.mainModule().getIdentityConstant());
    }

    @Override
    public boolean isVersionMatch(ModuleConstant constModule, VersionConstant constVer) {
        // TODO CP
        return true;
    }

    @Override
    public boolean isVersion(VersionConstant constVer) {
        // TODO CP:
        return true;
    }

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            MethodHandle getXvm = lookup.findGetter(Container.class, "xvm", Xvm.class);
            MethodHandle getCtx = lookup.findVirtual(Xvm.class, "getCtx",
                                        MethodType.methodType(Ctx.class));

            GetCtx      = MethodHandles.filterArguments(getCtx, 0, getXvm);
            GetInjector = lookup.findGetter(Container.class, "injector", Injector.class);
            SupplierOf  = lookup.findVirtual(Injector.class, "supplierOf",
                                MethodType.methodType(Function.class, TypeConstant.class, String.class));
            Apply       = lookup.findVirtual(Function.class, "apply",
                                MethodType.methodType(Object.class, Object.class));
        } catch (NoSuchFieldException | NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
