package org.xvm.runtime;


import java.util.Map;
import java.util.Set;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnionTypeConstant;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.text.xString;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;


/**
 * The main container (zero) associated with the main module.
 */
public class MainContainer
        extends Container
    {
    public MainContainer(Runtime runtime, NativeContainer containerNative, ModuleConstant idModule)
        {
        super(runtime, containerNative, idModule);
        }

    @Override
    public ObjectHandle getInjectable(Frame frame, String sName, TypeConstant type, ObjectHandle hOpts)
        {
        // check the custom injections first
        if (m_mapInjections != null)
            {
            String sValue = m_mapInjections.get(sName);
            if (sValue != null)
                {
                if (type.isNullable())
                    {
                    type = type.removeNullable();
                    }
                if (type.equals(frame.poolContext().typeString()))
                    {
                    return xString.makeHandle(sValue);
                    }
                }
            }

        ObjectHandle hResource = f_parent.getInjectable(frame, sName, type, hOpts);
        return hResource == null
                ? new DeferredCallHandle(xException.makeHandle(frame, "Invalid resource: " + sName))
                : maskInjection(frame, hResource, type);
        }

    /**
     * Mask the resource given to us by the native container if necessary.
     *
     * @param frame       the current frame
     * @param hResource   the resource handle
     * @param typeInject  the desired injection type
     *
     * @return the injected resource of the specified type
     */
    private ObjectHandle maskInjection(Frame frame, ObjectHandle hResource, TypeConstant typeInject)
        {
        if (hResource instanceof DeferredCallHandle hDeferred)
            {
            hDeferred.addContinuation(frameCaller ->
                {
                ObjectHandle hR = completeMasking(frameCaller, frameCaller.popStack(), typeInject);
                return Op.isDeferred(hR)
                        ? Op.R_CALL // must be an exception
                        : frameCaller.pushStack(hR);
                });
            return hDeferred;
            }
        return completeMasking(frame, hResource, typeInject);
        }

    private ObjectHandle completeMasking(Frame frame, ObjectHandle hResource, TypeConstant typeInject)
        {
        // Note: don't use hResource.getType(), since it augments the type!!
        TypeConstant typeResource = hResource.getComposition().getType();
        if (typeResource.isShared(getConstantPool()))
            {
            if (typeInject.isNullable())
                {
                if (hResource == xNullable.NULL)
                    {
                    return hResource;
                    }
                typeInject = typeInject.removeNullable();
                }
            if (typeInject instanceof UnionTypeConstant typeUnion)
                {
                // the injection's declared type is A|B; this should be extremely rare, if ever
                // used at all; the code below is just for completeness
                Set<TypeConstant> setMatch = typeUnion.collectExtended(typeResource, null);
                assert setMatch.size() == 1;
                typeInject = setMatch.iterator().next();
                }

            if (!typeResource.equals(typeInject))
                {
                hResource = hResource.maskAs(this, typeInject);
                if (hResource == null)
                    {
                    return new DeferredCallHandle(xException.makeHandle(frame,
                            "Invalid resource type: " + typeResource.getValueString()));
                    }
                }
            return hResource;
            }

        return new DeferredCallHandle(xException.makeHandle(frame,
                "Injection type is not a shared: \"" + typeResource.getValueString() + '"'));
        }


    // ----- MainContainer specific functionality --------------------------------------------------

    /**
     * Start the main container.
     *
     * @param (optional) a map of custom injections
     */
    public void start(Map<String, String> mapInjections)
        {
        if (m_contextMain != null)
            {
            throw new IllegalStateException("Already started");
            }

        if (mapInjections != null && !mapInjections.isEmpty())
            {
            m_mapInjections = mapInjections;
            }

        ensureServiceContext();
        }

    /**
     * Invoke the specified entry point.
     */
    public void invoke0(String sMethodName, ObjectHandle... ahArg)
        {
        try (var ignore = ConstantPool.withPool(f_idModule.getConstantPool()))
            {
            MethodConstant idMethod = findModuleMethod(sMethodName, ahArg);
            if (idMethod == null)
                {
                System.err.println("Missing: " +  sMethodName + " method for " + f_idModule.getValueString());
                return;
                }

            TypeConstant    typeModule = f_idModule.getType();
            TypeComposition clzModule  = resolveClass(typeModule);
            CallChain       chain      = clzModule.getMethodCallChain(idMethod.getSignature());

            FunctionHandle hInstantiateModuleAndRun = new NativeFunctionHandle((frame, ah, iReturn) ->
                {
                SingletonConstant idModule = frame.poolContext().ensureSingletonConstConstant(f_idModule);
                ObjectHandle      hModule  = frame.getConstHandle(idModule);

                return Op.isDeferred(hModule)
                        ? hModule.proceed(frame, frameCaller ->
                            chain.invoke(frameCaller, frameCaller.popStack(), ahArg, Op.A_IGNORE))
                        : chain.invoke(frame, hModule, ahArg, Op.A_IGNORE);
                });

            m_contextMain.callLater(hInstantiateModuleAndRun, Utils.OBJECTS_NONE);
            }
        catch (Exception e)
            {
            throw new RuntimeException("failed to run: " + f_idModule + ". Cause: " + e.getMessage());
            }
        }

    /**
     * Map of custom injections.
     */
    private Map<String, String> m_mapInjections;
    }