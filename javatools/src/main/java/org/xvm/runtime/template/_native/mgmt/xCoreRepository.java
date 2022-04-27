package org.xvm.runtime.template._native.mgmt;


import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTModuleTemplate;


/**
 * Native ModuleRepository functionality for the core repository.
 */
public class xCoreRepository
        extends ClassTemplate
    {
    public static xCoreRepository INSTANCE;

    public xCoreRepository(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        ConstantPool pool          = pool();
        TypeConstant typeInception = getInceptionClassConstant().getType();
        TypeConstant typeMask      = getCanonicalType();

        m_clzRepo = ensureClass(pool, typeInception, typeMask);

        markNativeProperty("moduleNames");
        markNativeMethod("getModule", STRING, null);

        typeInception.invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        return pool().ensureEcstasyTypeConstant("mgmt.ModuleRepository");
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "moduleNames":
                {
                ModuleRepository repo     = frame.f_context.f_container.getModuleRepository();
                Set<String>      setNames = repo.getModuleNames();
                StringHandle[]   ahName   = new StringHandle[setNames.size()];

                int i = 0;
                for (String sName : setNames)
                    {
                    ahName[i++] = xString.makeHandle(sName);
                    }

                ArrayHandle hArray = xArray.makeStringArrayHandle(ahName);
                return xArray.createListSet(frame, hArray, iReturn);
                }
            }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "getModule":
                {
                String           sName  = ((StringHandle) hArg).getStringValue();
                ModuleRepository repo   = frame.f_context.f_container.getModuleRepository();
                ModuleStructure  module = repo.loadModule(sName);

                if (module == null)
                    {
                    return frame.raiseException(xException.illegalArgument(frame,
                        "Invalid module name " + sName));
                    }

                return frame.assignValue(iReturn, xRTModuleTemplate.makeHandle(module));
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    /**
     * Injection support.
     */
    public ObjectHandle ensureModuleRepository(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hRepository = m_hRepository;
        if (hRepository == null)
            {
            m_hRepository = hRepository = makeHandle();
            }

        return hRepository;
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    public ObjectHandle makeHandle()
        {
        return new CoreRepoHandle(m_clzRepo);
        }

    public static class CoreRepoHandle
            extends ObjectHandle
        {
        protected CoreRepoHandle(TypeComposition clazz)
            {
            super(clazz);
            }
        }

    private TypeComposition m_clzRepo;

    /**
     * Cached Repository handle.
     */
    private ObjectHandle m_hRepository;
    }