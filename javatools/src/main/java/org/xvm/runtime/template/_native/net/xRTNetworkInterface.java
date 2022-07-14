package org.xvm.runtime.template._native.net;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template._native.reflect.xRTFunction;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xByteArray;


/**
 * Native implementation of a "NetworkInterface" service.
 */
public class xRTNetworkInterface
        extends xService
    {
    public static xRTNetworkInterface INSTANCE;

    public xRTNetworkInterface(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        markNativeProperty("running");

        markNativeMethod("nativeConnect"     , null, null);
        markNativeMethod("nativeListen"      , null, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        TypeConstant type = m_typeCanonical;
        if (type == null)
            {
            var pool = f_container.getConstantPool();
            m_typeCanonical = type = pool.ensureTerminalTypeConstant(pool.ensureClassConstant(
                    pool.ensureModuleConstant("net.xtclang.org"), "NetworkInterface"));
            }
        return type;
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "nameService":
                {
                BooleanHandle hRunning = xBoolean.FALSE; // TODO
                return frame.assignValue(iReturn, hRunning);
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context != hService.f_context)
            {
            // for now let's make sure all the calls are processed on the service fibers
            return xRTFunction.makeAsyncNativeHandle(method).callN(frame, hTarget, ahArg, aiReturn);
            }

        switch (method.getName())
            {
            case "nativeConnect":   // conditional Socket nativeConnect(Byte[] remoteAddressBytes,
                                    // UInt16 remotePort, Byte[] localAddressBytes, UInt16 localPort)
                // TODO conditional ServerSocket nativeListen(Byte[] localAddressBytes, UInt16 localPort)
                {
                byte[] abRemoteIP  = xByteArray.getBytes((ArrayHandle) ahArg[0]);
                int    nRemotePort = (int) ((JavaLong) ahArg[1]).getValue();
                byte[] abLocalIP   = xByteArray.getBytes((ArrayHandle) ahArg[2]);   // [] == null
                int    nLocalPort  = (int) ((JavaLong) ahArg[3]).getValue();
                try
                    {
                    // TODO
                    return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                    }
                catch (Exception e)
                    {
                    return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
                    }
                }

            case "nativeListen":    // conditional ServerSocket nativeListen(Byte[] localAddressBytes, UInt16 localPort)
                {
                byte[] abLocalIP  = xByteArray.getBytes((ArrayHandle) ahArg[0]);
                int    nLocalPort = (int) ((JavaLong) ahArg[1]).getValue();
                try
                    {
                    // TODO
                    return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                    }
                catch (Exception e)
                    {
                    return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
                    }
                }

            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // -----  helpers ------------------------------------------------------------------------------

    // TODO


    // ----- constants and fields ------------------------------------------------------------------

    /**
     * Cached canonical type.
     */
    private TypeConstant m_typeCanonical;
    }