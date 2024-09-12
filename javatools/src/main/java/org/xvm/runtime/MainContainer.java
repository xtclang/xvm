package org.xvm.runtime;


import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodKind;

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
        ObjectHandle hResource = f_parent.getInjectable(frame, sName, type, hOpts);

        if (hResource != null)
            {
            return maskInjection(frame, hResource, type);
            }

        // check the custom injections
        if (m_mapInjections != null)
            {
            Object oValue = m_mapInjections.get(sName);
            if (oValue != null)
                {
                if (type.equals(frame.poolContext().typeString()))
                    {
                    return xString.makeHandle(String.valueOf(oValue));
                    }
                if (type.equals(frame.poolContext().typeStringArray()))
                    {
                    return xString.makeArrayHandle((String[]) oValue);
                    }
                }
            }
        return null;
        }


    // ----- MainContainer specific functionality --------------------------------------------------

    /**
     * Start the main container.
     * <p/>
     * The injection map values must be either {@code String} or {@code String[]}.
     *
     * @param mapInjections (optional) a map of custom injections
     */
    public void start(Map<String, ?> mapInjections)
        {
        if (m_contextMain != null)
            {
            throw new IllegalStateException("Already started");
            }

        m_mapInjections = mapInjections;

        ensureServiceContext();
        }

    /**
     * Helper method to find any possible entry points for a given name.
     */
    public Set<MethodStructure> findMethods(String sMethodName)
        {
        try (var ignore = ConstantPool.withPool(f_idModule.getConstantPool()))
            {
            TypeInfo             infoModule = getModule().getType().ensureTypeInfo();
            Set<MethodConstant>  setIds     = infoModule.findMethods(sMethodName, -1, MethodKind.Any);
            Set<MethodStructure> setMethods = new HashSet<>();
            for (MethodConstant idMethod : setIds)
                {
                setMethods.add(infoModule.getMethodById(idMethod).
                        getTopmostMethodStructure(infoModule));
                }
            return setMethods;
            }
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
     * Invoke the test entry point.
     * <p/>
     * The entry point for executing tests is the {@code test} method in the core
     * Ecstasy module, not the module being tested.
     *
     * @return {@code true} if XUnit was present to execute tests, otherwise {@code false}
     */
    public boolean invokeTest0()
        {
        try (var ignore = ConstantPool.withPool(f_idModule.getConstantPool()))
            {
            ConstantPool   pool     = ConstantPool.getCurrentPool();
            ModuleConstant modXUnit = pool.ensureModuleConstant("xunit.xtclang.org");
            if (modXUnit == null || modXUnit.getComponent() == null)
                {
                return false;
                }

            TypeInfo       infoModule  = modXUnit.getType().ensureTypeInfo();
            MethodConstant idMethod    = findModuleMethod(infoModule, "test", Utils.OBJECTS_NONE);
            if (idMethod == null)
                {
                // we should not get here as we know there is the correct method in XUnit.
                System.err.println("Missing: test method for " + modXUnit.getName());
                return false;
                }

            TypeConstant    typeModule = modXUnit.getType();
            TypeComposition clzModule  = resolveClass(typeModule);
            CallChain       chain      = clzModule.getMethodCallChain(idMethod.getSignature());

            FunctionHandle hInstantiateModuleAndRun = new NativeFunctionHandle((frame, ah, iReturn) ->
                {
                SingletonConstant idModule = frame.poolContext().ensureSingletonConstConstant(modXUnit);
                ObjectHandle      hModule  = frame.getConstHandle(idModule);
                ObjectHandle[]    ahArg    = new ObjectHandle[]{frame.getConstHandle(f_idModule)};

                return Op.isDeferred(hModule)
                        ? hModule.proceed(frame, frameCaller ->
                            chain.invoke(frameCaller, frameCaller.popStack(), ahArg, Op.A_IGNORE))
                        : chain.invoke(frame, hModule, ahArg, Op.A_IGNORE);
                });

            m_contextMain.callLater(hInstantiateModuleAndRun, Utils.OBJECTS_NONE);
            return true;
            }
        catch (Exception e)
            {
            throw new RuntimeException("failed to run: " + f_idModule, e);
            }
        }

    /**
     * Map of custom injections.
     * <p/>
     * The map values will either be {@code String} or {@code String[]}.
     */
    private Map<String, ?> m_mapInjections;
    }