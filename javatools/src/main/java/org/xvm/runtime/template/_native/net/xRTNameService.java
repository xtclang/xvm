package org.xvm.runtime.template._native.net;


import java.net.InetAddress;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NameNotFoundException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template._native.reflect.xRTFunction;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * Native implementation of a network name service.
 */
public class xRTNameService
        extends xService {
    public static xRTNameService INSTANCE;

    public xRTNameService(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public void initNative() {
        BYTE_ARRAY_ARRAY_TYPE = pool().ensureArrayType(pool().typeByteArray());

        markNativeMethod("nativeResolve", null, null);
        markNativeMethod("nativeRecords", null, null);
        markNativeMethod("nativeLookup" , null, null);

        invalidateTypeInfo();
    }

    @Override
    public TypeConstant getCanonicalType() {
        TypeConstant type = m_typeCanonical;
        if (type == null) {
            var pool = f_container.getConstantPool();
            m_typeCanonical = type = pool.ensureTerminalTypeConstant(pool.ensureClassConstant(
                    pool.ensureModuleConstant("net.xtclang.org"), "NameService"));
        }
        return type;
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        ServiceHandle  hService = (ServiceHandle) hTarget;

        ObjectHandle[] ahArg = new ObjectHandle[method.getMaxVars()];
        ahArg[0] = hArg;

        if (frame.f_context != hService.f_context) {
            // for now let's make sure all the calls are processed on the service fibers
            return xRTFunction.makeAsyncNativeHandle(method).call1(frame, hTarget, ahArg, iReturn);
        }

        switch (method.getName()) {
            case "nativeRecords": { // String[] nativeRecords(String name)
                Container container = frame.f_context.f_container;
                String    sName     = ((StringHandle) ahArg[0]).getStringValue();

                Callable<String[]> task = () -> getAllRecords(sName);

                CompletableFuture<String[]> cfRecords = container.scheduleIO(task);
                Frame.Continuation continuation = frameCaller -> {
                    try {
                        return frameCaller.assignValue(iReturn,
                                xString.makeArrayHandle(cfRecords.get()));
                    } catch (Throwable e) {
                        return frameCaller.raiseException(
                            xException.obscureIoException(frameCaller, exceptionMessage(e)));
                    }
                };

                return frame.waitForIO(cfRecords, continuation);
            }
        }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        ServiceHandle hService = (ServiceHandle) hTarget;

        if (frame.f_context != hService.f_context) {
            // for now let's make sure all the calls are processed on the service fibers
            return xRTFunction.makeAsyncNativeHandle(method).callN(frame, hTarget, ahArg, aiReturn);
        }

        switch (method.getName()) {
        case "nativeResolve": { // conditional Byte[][] nativeResolve(String name)
            Container container = frame.f_context.f_container;
            String    sName     = ((StringHandle) ahArg[0]).getStringValue();

            Callable<InetAddress[]> task = () -> InetAddress.getAllByName(sName);

            CompletableFuture<InetAddress[]> cfResolve = container.scheduleIO(task);
            Frame.Continuation continuation = frameCaller -> {
                try {
                    InetAddress[] aAddr = cfResolve.get();
                    if (aAddr != null && aAddr.length > 0) {
                        // construct an array of byte arrays
                        int            c  = aAddr.length;
                        ObjectHandle[] ah = new ObjectHandle[c];
                        for (int i = 0; i < c; ++i) {
                            ah[i] = xArray.makeByteArrayHandle(
                                        aAddr[i].getAddress(), Mutability.Constant);
                        }

                        TypeComposition clz = ensureByteArrayArrayComposition(container);
                        return frameCaller.assignValues(aiReturn,
                                xBoolean.TRUE, xArray.createImmutableArray(clz, ah));
                    }
                } catch (Throwable ignore) {
                    // REVIEW CP: do we want to report the reason somehow?
                    // return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
                }
                return frameCaller.assignValue(aiReturn[0], xBoolean.FALSE);
            };

            return frame.waitForIO(cfResolve, continuation);
        }

        case "nativeLookup": { // conditional String nativeLookup(Byte[] addressBytes)
            byte[] abIP = xByteArray.getBytes((ArrayHandle) ahArg[0]);

            Callable<InetAddress> task = () -> InetAddress.getByAddress(abIP);

            CompletableFuture<InetAddress> cfLookup = frame.f_context.f_container.scheduleIO(task);
            Frame.Continuation continuation = frameCaller -> {
                try {
                    InetAddress addr     = cfLookup.get();
                    String      sName    = addr.getHostName();
                    String      sNotName = addr.getHostAddress();
                    if (sName != null && !sName.isEmpty() && !sName.equals(sNotName)) {
                        return frameCaller.assignValues(aiReturn,
                                xBoolean.TRUE, xString.makeHandle(sName));
                    }
                } catch (Exception ignore) {
                    // REVIEW CP: do we want to report the reason somehow?
                    // return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
                }
                return frameCaller.assignValue(aiReturn[0], xBoolean.FALSE);
            };

            return frame.waitForIO(cfLookup, continuation);
        }
        }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }


    // -----  helpers ------------------------------------------------------------------------------

    static TypeComposition ensureByteArrayArrayComposition(Container container) {
        return container.ensureClassComposition(BYTE_ARRAY_ARRAY_TYPE, xArray.INSTANCE);
    }

    static String[] getAllRecords(String sName)
            throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");

        DirContext context = new InitialDirContext(env);
        try {
            List<String> listFields = new ArrayList<>();

            // query explicit record types; many resolvers reject ANY-style lookups
            for (String sType : RECORD_TYPES) {
                addRecords(context, sName, sType, listFields);
            }

            return listFields.toArray(NO_RECORD_FIELDS);
        } finally {
            context.close();
        }
    }

    static void addRecords(DirContext context, String sName, String sType,
                           List<String> listFields)
            throws NamingException {
        try {
            Attributes attributes = context.getAttributes(sName, new String[] {sType});
            Attribute  attribute  = attributes.get(sType);
            if (attribute == null) {
                return;
            }

            NamingEnumeration<?> valueEnum = attribute.getAll();

            // Note: the JNDI DNS provider discards the TTL value before it reaches our code;
            //       if that ever becomes an issue we should use a real DNS library, e.g.
            //       (https://github.com/dnsjava/dnsjava), which exposes tbe TTL;
            //       the "netClass" attribute for all practocal purposes is always "Internet"
            try {
                while (valueEnum.hasMore()) {
                    String sData = valueEnum.next().toString();

                    listFields.add(normalizeDnsName(sName));
                    listFields.add("0");
                    listFields.add("IN");
                    listFields.add(sType);
                    listFields.add(normalizeRecordData(sType, sData));
                }
            } finally {
                valueEnum.close();
            }
        } catch (NameNotFoundException ignore) {}
    }

    static String exceptionMessage(Throwable e) {
        if (e instanceof ExecutionException exec && exec.getCause() != null) {
            e = exec.getCause();
        }
        String sMessage = e.getMessage();
        return sMessage == null || sMessage.isEmpty()
                ? e.toString()
                : sMessage;
    }

    static String normalizeDnsName(String sName) {
        return sName.endsWith(".")
                ? sName.substring(0, sName.length() - 1)
                : sName;
    }

    static String normalizeRecordData(String sType, String sData) {
        return switch (sType) {
            case "CNAME", "NS", "PTR" -> normalizeDnsName(sData);
            default                   -> sData;
        };
    }


    // ----- constants and fields ------------------------------------------------------------------

    /**
     * Cached canonical type.
     */
    private TypeConstant m_typeCanonical;

    private static TypeConstant BYTE_ARRAY_ARRAY_TYPE;

    private static final String[] NO_RECORD_FIELDS = new String[0];

    private static final String[] RECORD_TYPES = new String[] {
            "A", "AAAA", "CNAME", "MX", "NS", "PTR", "SOA", "SRV", "TXT"
    };
}