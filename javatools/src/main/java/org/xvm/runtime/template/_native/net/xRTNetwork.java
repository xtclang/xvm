package org.xvm.runtime.template._native.net;


import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.Op;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template._native.reflect.xRTFunction;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.text.xString;

/**
 * Native implementation of a "Network" service.
 */
public class xRTNetwork
        extends xService
    {
    public static xRTNetwork INSTANCE;

    public xRTNetwork(Container container, ClassStructure structure, boolean fInstance)
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
        markNativeProperty("interfaces");

        markNativeMethod("defaultInterface"      , null, null);
        markNativeMethod("instantiateNameService", null, null);
        markNativeMethod("interfaceByName"       , null, null);
        markNativeMethod("isSecure"              , null, null);
        markNativeMethod("nativeConnect"         , null, null);
        markNativeMethod("nativeListen"          , null, null);
        markNativeMethod("nativeNicByAddress"    , null, null);

        invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        TypeConstant type = m_typeCanonical;
        if (type == null)
            {
            var pool = f_container.getConstantPool();
            m_typeCanonical = type = pool.ensureTerminalTypeConstant(pool.ensureClassConstant(
                    pool.ensureModuleConstant("net.xtclang.org"), "Network"));
            }
        return type;
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "interfaces":
                {
                ObjectHandle[] ahNic = new ObjectHandle[0]; // TODO
                return frame.assignValue(iReturn, xArray.makeObjectArrayHandle(ahNic, xArray.Mutability.Constant));
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context != hService.f_context)
            {
            // for now let's make sure all the calls are processed on the service fibers
            return xRTFunction.makeAsyncNativeHandle(method).call1(frame, hTarget, ahArg, iReturn);
            }

        switch (method.getName())
            {
            case "instantiateNameService":
                {
                return instantiateNameService(frame, hService, iReturn);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
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
            case "isSecure":    // conditional Algorithms isSecure()
                {
                try
                    {
                    if (isSecure(frame, hService))
                        {
                        return frame.assignValues(aiReturn, xBoolean.TRUE, /*TODO Algorithms*/ null);
                        }
                    return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                    }
                catch (Exception e)
                    {
                    return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
                    }
                }

            case "defaultInterface":    // conditional NetworkInterface defaultInterface()
                {
                try
                    {
                    return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                    }
                catch (Exception e)
                    {
                    return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
                    }
                }

            case "interfaceByName":    // conditional NetworkInterface interfaceByName(String name)
                {
                String sName = ((xString.StringHandle) ahArg[0]).getStringValue();
                try
                    {
                    NetworkInterface nic = NetworkInterface.getByName(sName);
                    if (nic != null)
                        {
                        return frame.assignValues(aiReturn, xBoolean.TRUE, /*TODO NetworkInterface*/ null);
                        }
                    return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                    }
                catch (SocketException e)
                    {
                    return frame.raiseException(xException.ioException(frame, e.getMessage()));
                    }
                catch (Exception e)
                    {
                    return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
                    }
                }

            case "nativeNicByAddress":    // conditional NetworkInterface nativeNicByAddress(Byte[] addressBytes)
                {
                byte[] abIP = xByteArray.getBytes((ArrayHandle) ahArg[0]);
                try
                    {
                    InetAddress      addr = InetAddress.getByAddress(abIP);
                    NetworkInterface nic  = NetworkInterface.getByInetAddress(addr);
                    if (nic != null)
                        {
                        return frame.assignValues(aiReturn, xBoolean.TRUE, /*TODO NetworkInterface*/ null);
                        }
                    return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                    }
                catch (Exception e)
                    {
                    return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
                    }
                }

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

    /**
     * @return true iff the "Network" is configured to use TLS for security
     */
    public boolean isSecure(Frame frame, ServiceHandle hNetwork)
        {
        return ((BooleanHandle) hNetwork.getField(frame, "secure")).get();
        }

    /**
     * Implementation of "NameService instantiateNameService()" method;
     *
     * @return one of Op.R_NEXT, Op.R_CALL or Op.R_EXCEPTION values
     */
    protected int instantiateNameService(Frame frame, ServiceHandle hNetwork, int iReturn)
        {
        ClassTemplate    templateSvc  = xRTNameService.INSTANCE;
        ClassComposition clz          = templateSvc.getCanonicalClass();
        MethodStructure  constructor  = templateSvc.getStructure().findConstructor(getCanonicalType());
        ObjectHandle[]   ahParams     = new ObjectHandle[constructor.getMaxVars()];
        ahParams[0] = hNetwork;

        switch (templateSvc.construct(frame, constructor, clz, null, ahParams, Op.A_STACK))
            {
            case Op.R_NEXT:
                return frame.assignValue(iReturn, frame.popStack());

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    frameCaller.assignValue(iReturn, frameCaller.popStack()));
                return Op.R_CALL;

            default:
                throw new IllegalStateException();
            }
        }


    // ----- constants and fields ------------------------------------------------------------------

    /**
     * Cached canonical type.
     */
    private TypeConstant m_typeCanonical;
    }