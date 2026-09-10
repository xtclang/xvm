package org.xvm.runtime;


import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.CompletableFuture;
import java.util.Set;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnionTypeConstant;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;

import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.text.xString;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;

import org.xvm.util.Severity;

import static org.xvm.asm.Constants.RT_MODULE_METHOD_MISSING;


/**
 * The main container (zero) associated with the main module.
 */
public class MainContainer
        extends Container {
    public static MainContainer create(Runtime runtime, NativeContainer containerNative,
                                       ModuleConstant idModule) {
        return runtime.registerContainer(new MainContainer(runtime, containerNative, idModule));
    }

    private MainContainer(Runtime runtime, NativeContainer containerNative, ModuleConstant idModule) {
        super(runtime, containerNative, idModule);
    }

    @Override
    public ObjectHandle getInjectable(Frame frame, String sName, TypeConstant type, ObjectHandle hOpts) {
        // check the custom injections first
        List<String> listValue = m_mapInjections.get(sName);
        if (listValue == null) {
            // there is no matching custom injection
            return getParentInjectable(frame, sName, type, hOpts);
        }

        ConstantPool pool             = frame.poolContext();
        TypeConstant typeDestringable = pool.ensureEcstasyTypeConstant("text.Destringable");
        TypeConstant typeList         = pool.typeList();
        TypeConstant typeString       = pool.typeString();
        TypeConstant typeStrings      = pool.ensureParameterizedTypeConstant(typeList, typeString);
        TypeConstant typeRequired     = type;

        if (typeRequired.isNullable()) {
            // strip nullable from the required type
            typeRequired = typeRequired.removeNullable();
        }

        if (!listValue.isEmpty()) {
            if (typeRequired.equals(typeString)) {
                // require String, return the last element
                return xString.makeHandle(frame, listValue.getLast());
            }
            if (typeRequired.equals(typeStrings)) {
                // require String[], return the whole List<String> as an array
                String[] asValue = listValue.toArray(String[]::new);
                return xString.makeArrayHandle(this, asValue);
            }
            if (typeRequired.isEnum()) {
                TypeComposition clz    = typeRequired.ensureClass(frame);
                xEnum           en     = clz.getTemplate(xEnum.class);
                String          sValue = listValue.getLast();
                ObjectHandle    handle = en.ensureEnumByName(frame, sValue);
                if (handle == null) {
                    // a value was injected that does not match any of the enum values
                    String msg = "Injectable " + sName + "=\"" + sValue
                            + "\" does not match any names in enum "
                            + typeRequired.getSingleUnderlyingClass(true).getName() + " "
                            + en.getNames();
                    return new DeferredCallHandle(xException.makeHandle(frame, msg));
                }
                return handle;
            }
            if (typeRequired.isA(typeDestringable)) {
                // require Destringable, return the converted last String element
                return toDestringable(frame, typeRequired, listValue.getLast());
            }
        }
        return getParentInjectable(frame, sName, type, hOpts);
    }

    private ObjectHandle getParentInjectable(Frame frame, String sName, TypeConstant type,
                                             ObjectHandle hOpts) {
        // If the required type is nullable return null, otherwise return an exception
        ObjectHandle hResource = f_parent.getInjectable(frame, sName, type, hOpts);
        return hResource == null
                ? type.isNullable()
                    ? xNullable.makeHandle(frame)
                    : new DeferredCallHandle(xException.makeHandle(frame, "Invalid resource: " + sName))
                : maskInjection(frame, hResource, type);
    }

    private ObjectHandle toDestringable(Frame frame, TypeConstant type, String sValue) {
        ConstantPool    pool     = frame.poolContext();
        TypeComposition clz      = type.ensureClass(frame);
        ClassTemplate   template = clz.getTemplate();
        MethodStructure ctor     = template.getStructure().findMethod("construct", 1, pool.typeString());
        ObjectHandle[]  ahArgs   = {xString.makeHandle(frame, sValue)};
        int             iResult  = template.construct(frame, ctor, clz, null, ahArgs, Op.A_STACK);
        return frame.popResult(iResult);
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
    private ObjectHandle maskInjection(Frame frame, ObjectHandle hResource, TypeConstant typeInject) {
        if (hResource instanceof DeferredCallHandle hDeferred) {
            hDeferred.addContinuation(frameCaller -> {
                ObjectHandle hR = completeMasking(frameCaller, frameCaller.popStack(), typeInject);
                return Op.isDeferred(hR)
                        ? Op.R_CALL // must be an exception
                        : frameCaller.pushStack(hR);
            });
            return hDeferred;
        }
        return completeMasking(frame, hResource, typeInject);
    }

    private ObjectHandle completeMasking(Frame frame, ObjectHandle hResource, TypeConstant typeInject) {
        // Note: don't use hResource.getType(), since it augments the type!!
        TypeConstant typeResource = hResource.getComposition().getType();
        if (typeResource.isShared(getConstantPool())) {
            if (typeInject.isNullable()) {
                if (xNullable.isNull(hResource)) {
                    return hResource;
                }
                typeInject = typeInject.removeNullable();
            }
            if (typeInject instanceof UnionTypeConstant typeUnion) {
                // the injection's declared type is A|B; this should be extremely rare, if ever
                // used at all; the code below is just for completeness
                Set<TypeConstant> setMatch = typeUnion.collectExtended(typeResource, null);
                assert setMatch.size() == 1;
                typeInject = setMatch.iterator().next();
            }

            if (!typeResource.equals(typeInject)) {
                hResource = hResource.maskAs(this, typeInject);
                if (hResource == null) {
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
     * @param mapInjections a map of custom injections where each key maps to a list of values;
     *                      must not be null, but may be empty
     */
    public void start(Map<String, List<String>> mapInjections) {
        if (m_contextMain != null) {
            throw new IllegalStateException("Already started");
        }

        m_mapInjections = Objects.requireNonNull(mapInjections);

        ensureServiceContext();
    }

    /**
     * Post an asynchronous request to a method on this container's module, and answer with its
     * result.
     *
     * <p>Lifted from the LSPAPI branch, where it is the change that makes a resident host possible
     * at all: {@link #invoke0} is fire-and-forget with no result, so a long-lived container zero
     * could be started but never asked for anything. This posts through the main service context,
     * so the call is an ordinary service request on that container's own fiber and the caller gets
     * a future.
     *
     * <p><b>Adapted:</b> the original wraps its body in {@code ConstantPool.withPool(...)}. This
     * branch deleted the ambient pool (see {@code XvmStructure}: "ownership is always a
     * parameter"), and nothing in the body needs it - {@code findModuleMethod} and
     * {@code resolveClass} work from {@code f_idModule}, and the frame supplies its own pool
     * through {@code frame.poolContext()}.
     *
     * @param sMethodName  the method to invoke on the module
     * @param ahArg        the arguments
     *
     * @return the future result; completes with null for a void method
     *
     * @throws IllegalArgumentException if the method does not exist, or returns more than one value
     * @throws IllegalStateException    if the main service has terminated
     */
    /**
     * Invoke a method on the module and answer its result as the type the caller expects.
     *
     * <p>The untyped overload hands back an {@code ObjectHandle}, so every caller re-establishes
     * the type by hand with a cast the compiler cannot check - and the type is known on BOTH sides,
     * dropped only in between. {@code runner.x} declares {@code Int runTask(...)}; the caller knows
     * what it asked for; only the boundary forgets. Passing the expected type puts that knowledge
     * in one place and turns a signature change in Ecstasy into a {@link ClassCastException} at the
     * boundary that names both types, rather than one further away in whichever site unwrapped it.
     *
     * @param sMethodName  the method to invoke on the module
     * @param typeResult   the handle type the method answers with
     * @param ahArg        the arguments
     *
     * @param <T> the handle type the method answers with
     *
     * @return the future result; completes with null for a void method
     */
    public <T extends ObjectHandle> CompletableFuture<T> invokeAsync(
            String sMethodName, Class<T> typeResult, ObjectHandle... ahArg) {
        // Class.cast rather than an unchecked cast: this is a real check AT the boundary, and
        // Class.cast(null) is null, so a void method still completes cleanly
        return invokeAsync(sMethodName, ahArg).thenApply(typeResult::cast);
    }

    public CompletableFuture<ObjectHandle> invokeAsync(String sMethodName, ObjectHandle... ahArg) {
        MethodConstant idMethod = findModuleMethod(sMethodName, ahArg);
        if (idMethod == null) {
            throw new IllegalArgumentException("no such method \"" + sMethodName
                    + "\" on " + f_idModule.getValueString());
        }

        TypeConstant      typeModule = f_idModule.getType();
        TypeComposition   clzModule  = resolveClass(typeModule);
        SignatureConstant sigMethod  = idMethod.getSignature();
        CallChain         chain      = clzModule.getMethodCallChain(sigMethod);
        int               cReturns   = sigMethod.getReturnCount();
        if (cReturns > 1) {
            throw new IllegalArgumentException("method returns more than one value: "
                    + idMethod.getValueString());
        }

        FunctionHandle hFunction = new NativeFunctionHandle(this, (frame, ahRealArg, iReturn) -> {
            SingletonConstant idModule =
                    frame.poolContext().ensureSingletonConstConstant(f_idModule);
            ObjectHandle hModule = frame.getConstHandle(idModule);
            return Op.isDeferred(hModule)
                    ? hModule.proceed(frame, frameCaller ->
                            chain.invoke(frameCaller, frameCaller.popStack(), ahRealArg, iReturn))
                    : chain.invoke(frame, hModule, ahRealArg, iReturn);
        });

        CompletableFuture<ObjectHandle> future =
                m_contextMain.postRequest(null, hFunction, ahArg, cReturns);
        if (future == null) {
            throw new IllegalStateException("the main service has terminated");
        }
        return future;
    }

    /**
     * Invoke the specified entry point.
     */
    public void invoke0(String sMethodName, ObjectHandle... ahArg) {
        try {
            MethodConstant idMethod = findModuleMethod(sMethodName, ahArg);
            if (idMethod == null) {
                // ERROR: the entry point does not exist, so nothing runs. That is a failure of
                // the operation the caller asked for, not an oddity worth noting.
                getErrorListener().error(RT_MODULE_METHOD_MISSING, sMethodName, f_idModule.getValueString());
                return;
            }

            TypeConstant      typeModule = f_idModule.getType();
            TypeComposition   clzModule  = resolveClass(typeModule);
            SignatureConstant sigMethod  = idMethod.getSignature();
            CallChain         chain      = clzModule.getMethodCallChain(sigMethod);
            boolean           fReturn    = sigMethod.getReturnCount() > 0;

            FunctionHandle hInstantiateModuleAndRun = new NativeFunctionHandle(this,
                    (frame, ah, iRet) -> {
                SingletonConstant idModule =
                        frame.poolContext().ensureSingletonConstConstant(f_idModule);
                ObjectHandle      hModule  = frame.getConstHandle(idModule);
                int               iReturn  = fReturn ? Op.A_STACK : Op.A_IGNORE;

                // Late constant registration during user-code execution is owner-sensitive and can
                // hide stale same-JVM state. The marker is installed only when the diagnostic
                // property is enabled, after entry setup and module singleton resolution.
                Frame.Continuation invoke = frameCaller -> {
                    ObjectHandle target = frameCaller.popStack();
                    return invokeCall(frameCaller, target, chain, ahArg, iReturn, sMethodName);
                };

                int iResult = Op.isDeferred(hModule)
                        ? hModule.proceed(frame, invoke)
                        : invokeCall(frame, hModule, chain, ahArg, iReturn, sMethodName);
                switch (iResult) {
                case Op.R_NEXT:
                    setResult(fReturn ? frame.popStack() : null);
                    break;

                case Op.R_CALL:
                    frame.m_frameNext.addContinuation(frameCaller -> {
                        setResult(fReturn ? frameCaller.popStack() : null);
                        return Op.R_NEXT;
                    });
                    break;

                case Op.R_EXCEPTION:
                    break;

                default:
                    throw new IllegalStateException();
                }
                return iResult;
            });

            // capture the event-driven completion future the service context already produces:
            // it completes normally when the entry point finishes and EXCEPTIONALLY (carrying the
            // XTC ExceptionHandle) if the run threw. Embedders can await this instead of the
            // busy-wait poll, and get a real failure object instead of only the int result.
            m_futureResult = m_contextMain.callLater(hInstantiateModuleAndRun, Utils.OBJECTS_NONE);
        } catch (Exception e) {
            // Keep the original startup/invocation cause. Message-only wrappers hide the owner and
            // module-load failure path that same-JVM diagnostics need.
            throw new RuntimeException("failed to run: " + f_idModule, e);
        }
    }

    /**
     * Mark the pool as runtime-published for diagnostics and invoke the entry call chain.
     */
    private static int invokeCall(Frame frame, ObjectHandle target, CallChain chain,
                                  ObjectHandle[] args, int iReturn, String sMethodName) {
        frame.poolContext().markRuntimePublished(
                "MainContainer.invoke0(" + sMethodName + ')');
        return chain.invoke(frame, target, args, iReturn);
    }

    /**
     * Save the result of "main" method execution.
     */
    private void setResult(ObjectHandle hReturn) {
        m_nResult = hReturn instanceof ObjectHandle.JavaLong hLong
                ? (int) hLong.getValue()
                : 0;
    }

    /**
     * @return an optional result of the "main" method execution.
     */
    public int getResult() {
        return m_nResult;
    }

    /**
     * Map of custom injections where each key maps to a list of string values.
     */
    private Map<String, List<String>> m_mapInjections;

    /**
     * The return value from the "main" method. The value of "1" indicates that the method has
     * completed abnormally.
     */
    private int m_nResult = 1;

    /**
     * The completion future for the current {@link #invoke0} run: completes normally when the
     * entry point finishes, exceptionally (carrying the XTC {@code ExceptionHandle} inside a
     * {@code WrapperException}) if it threw. Null before the first {@code invoke0}. This is the
     * event-driven, failure-carrying signal a first-class embedding API awaits instead of the
     * naive idle poll.
     */
    private CompletableFuture<ObjectHandle> m_futureResult;

    /**
     * @return the completion future for the most recent {@link #invoke0}, or null if none has
     *         been started yet
     */
    public CompletableFuture<ObjectHandle> futureResult() {
        return m_futureResult;
    }
}
