package org.xvm.runtime.template._native.mgmt;


import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTModuleTemplate;

import org.xvm.util.Lazy;

import static java.util.Objects.requireNonNull;


/**
 * Native ModuleRepository functionality for the core repository.
 */
public class xCoreRepository
        extends ClassTemplate {
    public xCoreRepository(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        TypeConstant typeInception = getInceptionClassConstant().getType();
        TypeConstant typeMask      = getCanonicalType();

        m_clzRepo = ensureClass(f_container, typeInception, typeMask);

        markNativeProperty("moduleNames");
        markNativeMethod("getModule", null, null);

        typeInception.invalidateTypeInfo();
    }

    @Override
    public TypeConstant getCanonicalType() {
        return pool().ensureEcstasyTypeConstant("mgmt.ModuleRepository");
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        switch (sPropName) {
        case "moduleNames": {
            ModuleRepository repo     = ((CoreRepoHandle) hTarget).f_repository;
            Set<String>      setNames = repo.getModuleNames();

            ArrayHandle hArray = xString.makeArrayHandle(frame.container(),
                    setNames.toArray(Utils.NO_NAMES));
            return xArray.createListSet(frame, hArray, iReturn);
        }
        }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        switch (method.getName()) {
        case "getModule": { // conditional ModuleTemplate getModule(String name, Version? version = Null)
            String           sName  = ((StringHandle) ahArg[0]).getStringValue();
            ModuleRepository repo   = ((CoreRepoHandle) hTarget).f_repository;
            ModuleStructure  module = repo.loadModule(sName);

            if (module != null && !module.isMainModule()
                    && module.getFileStructure().isBundle()) {
                // a non-main module served out of a multi-module container ("bundle") is handed
                // out as a detached copy, so that reflection anchored on its file structure (such
                // as "template.parent.resolve(repo).mainModule" in getResolvedModule) behaves
                // exactly as if the module had been loaded from its own single-module file
                module = module.detachedCopy();
            }

            return module == null
                ? frame.assignValue(aiReturn[0], xBoolean.falseHandle(frame))
                : frame.assignValues(aiReturn, xBoolean.trueHandle(frame),
                        xRTModuleTemplate.makeHandle(frame.f_context.f_container, module));
        }
        }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }

    /**
     * Injection support.
     */
    public ObjectHandle ensureModuleRepository(Frame frame, ObjectHandle hOpts) {
        return f_hRepository.get(this);
    }


    // ----- ObjectHandle --------------------------------------------------------------------------

    private ObjectHandle makeHandle(Container container) {
        if (requireNonNull(container, "container") != f_container) {
            throw new IllegalArgumentException("Repository handle owner does not match template owner");
        }
        return new CoreRepoHandle(requireNonNull(m_clzRepo, "m_clzRepo"),
                container.getModuleRepository());
    }

    /**
     * Create a handle over a SPECIFIC repository, rather than the template container's own.
     *
     * <p>Lifted from the LSPAPI branch. It is what lets a run be handed its own repository as an
     * argument: their {@code runTask(template, repository, consoleId)} passes one of these into
     * Ecstasy, where the container is created with it. Before this, the handle answered from
     * {@code f_container.getModuleRepository()}, so every run necessarily saw the same repository as
     * the container that made the handle.
     *
     * <p><b>Adapted:</b> this branch caches the container's own handle in a {@link Lazy.Bound} cell
     * keyed to the owner, so this path deliberately does NOT use that cache - a per-request handle
     * is not the owner's handle and must not displace it.
     *
     * @param repository  the repository the handle reads from
     *
     * @return a handle over that repository
     */
    public ObjectHandle makeHandle(ModuleRepository repository) {
        return new CoreRepoHandle(requireNonNull(m_clzRepo, "m_clzRepo"),
                requireNonNull(repository, "repository"));
    }

    public static class CoreRepoHandle
            extends ObjectHandle {
        /**
         * The repository this handle reads from. Final, and per handle: a request's repository must
         * not leak into another request's handle.
         */
        protected final ModuleRepository f_repository;

        protected CoreRepoHandle(TypeComposition clazz, ModuleRepository repository) {
            super(clazz);
            f_repository = repository;
        }
    }

    private TypeComposition m_clzRepo;

    /**
     * Cached Repository handle owned by this template's container.
     *
     * The final Lazy cell preserves one-handle-per-owner caching and safely
     * publishes the handle if injection is requested concurrently.
     */
    private final Lazy.Bound<xCoreRepository, ObjectHandle> f_hRepository =
            Lazy.ofBound(owner -> owner.makeHandle(owner.container()));
}
