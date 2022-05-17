package org.xvm.runtime.template._native.net;


import java.net.InetAddress;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template._native.reflect.xRTFunction;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.text.xString;


/**
 * Native implementation of a network name service.
 */
public class xRTNameService
        extends xService
    {
    public static xRTNameService INSTANCE;

    public xRTNameService(Container container, ClassStructure structure, boolean fInstance)
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
        BYTE_ARRAY_ARRAY_TYPE = pool().ensureArrayType(pool().typeByteArray());

        markNativeMethod("nativeResolve", null, null);
        markNativeMethod("nativeLookup" , null, null);

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
                    pool.ensureModuleConstant("net.xtclang.org"), "NameService"));
            }
        return type;
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
            case "nativeResolve":   // conditional Byte[][] nativeResolve(String name)
                {
                String sName = ((xString.StringHandle) ahArg[0]).getStringValue();
                try
                    {
                    // TODO GG: immediately return a future, and use the executor pool to finish the work async
                    InetAddress[] aAddrs = InetAddress.getAllByName(sName);
                    if (aAddrs != null && aAddrs.length > 0)
                        {
                        // construct an array of byte arrays
                        int            c  = aAddrs.length;
                        ObjectHandle[] ah = new ObjectHandle[c];
                        for (int i = 0; i < c; ++i)
                            {
                            ah[i] = xArray.makeByteArrayHandle(aAddrs[i].getAddress(), xArray.Mutability.Constant);
                            }

                        TypeComposition clz = ensureByteArrayArrayComposition(frame.f_context.f_container);
                        return frame.assignValues(aiReturn, xBoolean.TRUE, xArray.createImmutableArray(clz, ah));
                        }
                    return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                    }
                catch (Exception e)
                    {
                    return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
                    }
                }

            case "nativeLookup":    // conditional String nativeLookup(Byte[] addressBytes)
                {
                byte[] abIP = xByteArray.getBytes((ArrayHandle) ahArg[0]);
                try
                    {
                    // TODO GG: immediately return a future, and use the executor pool to finish the work async
                    InetAddress inetaddr = InetAddress.getByAddress(abIP);
                    String      sName    = inetaddr.getHostName();
                    String      sNotName = inetaddr.getHostAddress();
                    if (sName != null && sName.length() > 0 && !sName.equals(sNotName))
                        {
                        return frame.assignValues(aiReturn, xBoolean.TRUE, xString.makeHandle(sName));
                        }
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

    static TypeComposition ensureByteArrayArrayComposition(Container container)
        {
        return container.ensureClassComposition(BYTE_ARRAY_ARRAY_TYPE, xArray.INSTANCE);
        }


    // ----- constants and fields ------------------------------------------------------------------

    /**
     * Cached canonical type.
     */
    private TypeConstant m_typeCanonical;

    private static TypeConstant BYTE_ARRAY_ARRAY_TYPE;
    }