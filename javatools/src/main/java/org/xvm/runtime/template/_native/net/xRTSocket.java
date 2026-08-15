package org.xvm.runtime.template._native.net;


import java.io.IOException;
import java.io.InputStream;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template._native.reflect.xRTFunction;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.numbers.xInt64;
import org.xvm.runtime.template.numbers.xUInt16;


/**
 * Native implementation of a {@code Socket} service ({@code RTSocket}).
 */
public class xRTSocket
        extends xService {
    public static xRTSocket INSTANCE;

    public static final int CONNECT_TIMEOUT_MS = 15_000;

    public xRTSocket(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public void initNative() {
        markNativeMethod("nativeReadBytes",      null, null);
        markNativeMethod("nativeWriteBytes",     null, null);
        markNativeMethod("nativeAvailable",      null, null);
        markNativeMethod("nativeShutdownInput",  null, null);
        markNativeMethod("nativeShutdownOutput", null, null);
        markNativeMethod("nativeClose",          null, null);

        invalidateTypeInfo();
    }

    @Override
    public TypeConstant getCanonicalType() {
        TypeConstant type = m_typeCanonical;
        if (type == null) {
            var pool = f_container.getConstantPool();
            m_typeCanonical = type = pool.ensureTerminalTypeConstant(pool.ensureClassConstant(
                    pool.ensureModuleConstant("net.xtclang.org"), "Socket"));
        }
        return type;
    }

    @Override
    protected ServiceHandle createStructHandle(TypeComposition clazz, ServiceContext context) {
        return new SocketHandle(clazz.ensureAccess(Access.STRUCT), context);
    }

    @Override
    public ServiceHandle createServiceHandle(ServiceContext context,
                                             ClassComposition clz, TypeConstant typeMask) {
        SocketHandle hService = new SocketHandle(clz.maskAs(typeMask), context);
        context.setService(hService);
        return hService;
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        SocketHandle hSocket = requireSocketHandle(hTarget);
        if (hSocket == null) {
            return frame.raiseException(xException.illegalState(frame, "not a native socket"));
        }

        if (frame.f_context != hSocket.f_context) {
            return xRTFunction.makeAsyncNativeHandle(method).
                    call1(frame, hTarget, new ObjectHandle[] {hArg}, iReturn);
        }

        switch (method.getName()) {
        case "nativeReadBytes":
            return nativeReadBytes(frame, hSocket, (int) ((JavaLong) hArg).getValue(), iReturn);
        }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn) {
        SocketHandle hSocket = requireSocketHandle(hTarget);
        if (hSocket == null) {
            return frame.raiseException(xException.illegalState(frame, "not a native socket"));
        }

        if (frame.f_context != hSocket.f_context) {
            return xRTFunction.makeAsyncNativeHandle(method).call1(frame, hTarget, ahArg, iReturn);
        }

        switch (method.getName()) {
        case "nativeReadBytes":
            return nativeReadBytes(frame, hSocket, (int) ((JavaLong) ahArg[0]).getValue(), iReturn);

        case "nativeWriteBytes":
            return nativeWriteBytes(frame, hSocket, ahArg);

        case "nativeAvailable":
            return nativeAvailable(frame, hSocket, iReturn);

        case "nativeShutdownInput":
            return nativeShutdown(frame, hSocket, true);

        case "nativeShutdownOutput":
            return nativeShutdown(frame, hSocket, false);

        case "nativeClose":
            return nativeClose(frame, hSocket);
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }


    // ----- connect -------------------------------------------------------------------------------

    /**
     * TCP connect used by {@code RTNetwork.nativeConnect} / {@code RTNetworkInterface.nativeConnect}.
     *
     * @return one of {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION}
     */
    public static int connect(Frame frame, byte[] abRemoteIP, int nRemotePort,
                              byte[] abLocalIP, int nLocalPort, int[] aiReturn) {
        Callable<Socket> task = () ->
                openConnectedSocket(abRemoteIP, nRemotePort, abLocalIP, nLocalPort);

        CompletableFuture<Socket> cf = frame.f_context.f_container.scheduleIO(task);
        Frame.Continuation continuation = frameCaller -> {
            try {
                Socket      sock    = cf.get();
                InetAddress local   = sock.getLocalAddress();
                byte[]      abLocal = local == null ? new byte[0] : local.getAddress();
                int         nLocal  = sock.getLocalPort();
                return INSTANCE.constructSocket(frameCaller, sock, abLocal, nLocal,
                        abRemoteIP, nRemotePort, aiReturn);
            } catch (Throwable e) {
                Throwable cause = unwrap(e);
                if (cause instanceof IOException) {
                    return frameCaller.assignValue(aiReturn[0], xBoolean.FALSE);
                }
                return frameCaller.raiseException(
                        xException.makeHandle(frameCaller, cause.getMessage()));
            }
        };

        return frame.waitForIO(cf, continuation);
    }

    /**
     * Open a client TCP socket with the same options {@link #connect} uses.
     * On bind, option, or connect failure the Java socket is closed before this returns.
     */
    static Socket openConnectedSocket(byte[] abRemoteIP, int nRemotePort,
                                      byte[] abLocalIP, int nLocalPort)
            throws IOException {
        Socket  sock  = new Socket();
        boolean owned = false;
        try {
            sock.setTcpNoDelay(true);
            sock.setKeepAlive(true);
            if (abLocalIP != null && abLocalIP.length > 0) {
                sock.bind(new InetSocketAddress(InetAddress.getByAddress(abLocalIP), nLocalPort));
            } else if (nLocalPort != 0) {
                sock.bind(new InetSocketAddress(nLocalPort));
            }
            sock.connect(new InetSocketAddress(InetAddress.getByAddress(abRemoteIP), nRemotePort),
                    CONNECT_TIMEOUT_MS);
            owned = true;
            return sock;
        } finally {
            if (!owned) {
                closeQuietly(sock);
            }
        }
    }

    protected int constructSocket(Frame frame, Socket sock, byte[] abLocal, int nLocalPort,
                                  byte[] abRemote, int nRemotePort, int[] aiReturn) {
        ClassTemplate    template     = this;
        ClassComposition clz          = template.getCanonicalClass();
        ConstantPool     pool         = pool();
        MethodStructure  constructor  = template.getStructure().findConstructor(
                pool.typeByteArray(), pool.typeUInt16(),
                pool.typeByteArray(), pool.typeUInt16());
        ObjectHandle[]   ahParams     = new ObjectHandle[constructor.getMaxVars()];
        ahParams[0] = xArray.makeByteArrayHandle(abLocal, Mutability.Constant);
        ahParams[1] = xUInt16.INSTANCE.makeJavaLong(nLocalPort);
        ahParams[2] = xArray.makeByteArrayHandle(abRemote, Mutability.Constant);
        ahParams[3] = xUInt16.INSTANCE.makeJavaLong(nRemotePort);

        switch (template.construct(frame, constructor, clz, null, ahParams, Op.A_STACK)) {
        case Op.R_NEXT:
            return finishConnect(frame, sock, aiReturn);

        case Op.R_EXCEPTION:
            closeQuietly(sock);
            return Op.R_EXCEPTION;

        case Op.R_CALL:
            frame.m_frameNext.addContinuation(frameCaller ->
                    finishConnect(frameCaller, sock, aiReturn));
            return Op.R_CALL;

        default:
            closeQuietly(sock);
            throw new IllegalStateException();
        }
    }

    private static int finishConnect(Frame frame, Socket sock, int[] aiReturn) {
        ObjectHandle h = frame.popStack();
        SocketHandle hSocket = requireSocketHandle(h);
        if (hSocket == null) {
            closeQuietly(sock);
            return frame.raiseException(xException.illegalState(frame, "socket construct failed"));
        }
        hSocket.socket = sock;
        if (hSocket.f_context.getService() instanceof SocketHandle hSvc && hSvc != hSocket) {
            hSvc.socket = sock;
        }
        return frame.assignValues(aiReturn, xBoolean.TRUE, hSocket);
    }


    // ----- I/O -----------------------------------------------------------------------------------

    private static Socket javaSocket(SocketHandle hSocket) {
        if (hSocket.socket != null) {
            return hSocket.socket;
        }
        if (hSocket.f_context.getService() instanceof SocketHandle hSvc) {
            return hSvc.socket;
        }
        return null;
    }

    private static int nativeReadBytes(Frame frame, SocketHandle hSocket, int cBytes, int iReturn) {
        Socket sock = javaSocket(hSocket);
        if (sock == null || sock.isClosed()) {
            return frame.raiseException(xException.ioException(frame, "socket closed"));
        }
        if (cBytes <= 0) {
            return frame.assignValue(iReturn, xArray.makeByteArrayHandle(new byte[0], Mutability.Constant));
        }

        Callable<byte[]> task = () -> readBytes(sock, cBytes);
        CompletableFuture<byte[]> cf = frame.f_context.f_container.scheduleIO(task);
        Frame.Continuation continuation = frameCaller -> {
            try {
                return frameCaller.assignValue(iReturn,
                        xArray.makeByteArrayHandle(cf.get(), Mutability.Constant));
            } catch (Throwable e) {
                return frameCaller.raiseException(
                        xException.ioException(frameCaller, unwrap(e).getMessage()));
            }
        };
        return frame.waitForIO(cf, continuation);
    }

    private static int nativeWriteBytes(Frame frame, SocketHandle hSocket, ObjectHandle[] ahArg) {
        Socket sock = javaSocket(hSocket);
        if (sock == null || sock.isClosed()) {
            return frame.raiseException(xException.ioException(frame, "socket closed"));
        }
        byte[] ab = xByteArray.getBytes((ArrayHandle) ahArg[0]);
        long   of = ((JavaLong) ahArg[1]).getValue();
        long   n  = ((JavaLong) ahArg[2]).getValue();

        String sInvalid = invalidWriteRange(ab.length, of, n);
        if (sInvalid != null) {
            return frame.raiseException(xException.outOfBounds(frame, sInvalid));
        }
        if (n <= 0) {
            return Op.R_NEXT;
        }

        int ofWrite = (int) of;
        int nWrite  = (int) n;
        Callable<Void> task = () -> {
            writeBytes(sock, ab, ofWrite, nWrite);
            return null;
        };
        CompletableFuture<Void> cf = frame.f_context.f_container.scheduleIO(task);
        Frame.Continuation continuation = frameCaller -> {
            try {
                cf.get();
                return Op.R_NEXT;
            } catch (Throwable e) {
                return frameCaller.raiseException(
                        xException.ioException(frameCaller, unwrap(e).getMessage()));
            }
        };
        return frame.waitForIO(cf, continuation);
    }

    /**
     * Validate a write {@code offset}/{@code count} coming from Ecstasy {@code Int}.
     *
     * @return {@code null} if the range is valid (including a zero-length no-op);
     *         otherwise a message for an XVM {@code OutOfBounds}
     */
    static String invalidWriteRange(int nLength, long lOffset, long lCount) {
        if (lCount == 0) {
            return null;
        }
        if (lOffset < 0 || lCount < 0
                || lOffset > Integer.MAX_VALUE || lCount > Integer.MAX_VALUE
                || lOffset + lCount > nLength) {
            return "write offset " + lOffset + " count " + lCount + " length " + nLength;
        }
        return null;
    }

    static byte[] readBytes(Socket sock, int cBytes)
            throws IOException {
        if (cBytes <= 0) {
            return new byte[0];
        }
        InputStream in  = sock.getInputStream();
        byte[]      buf = new byte[cBytes];
        int         off = 0;
        while (off < cBytes) {
            int n = in.read(buf, off, cBytes - off);
            if (n < 0) {
                break;
            }
            off += n;
        }
        if (off == cBytes) {
            return buf;
        }
        byte[] actual = new byte[off];
        System.arraycopy(buf, 0, actual, 0, off);
        return actual;
    }

    static void writeBytes(Socket sock, byte[] ab, int of, int n)
            throws IOException {
        if (n <= 0) {
            return;
        }
        var out = sock.getOutputStream();
        out.write(ab, of, n);
        out.flush();
    }

    private static int nativeAvailable(Frame frame, SocketHandle hSocket, int iReturn) {
        Socket sock = javaSocket(hSocket);
        if (sock == null || sock.isClosed()) {
            return frame.assignValue(iReturn, xInt64.INSTANCE.makeJavaLong(0));
        }
        try {
            int n = sock.getInputStream().available();
            return frame.assignValue(iReturn, xInt64.INSTANCE.makeJavaLong(Math.max(n, 0)));
        } catch (IOException e) {
            return frame.assignValue(iReturn, xInt64.INSTANCE.makeJavaLong(0));
        }
    }

    private static int nativeShutdown(Frame frame, SocketHandle hSocket, boolean fInput) {
        Socket sock = javaSocket(hSocket);
        if (sock == null || sock.isClosed()) {
            return Op.R_NEXT;
        }
        try {
            if (fInput) {
                sock.shutdownInput();
            } else {
                sock.shutdownOutput();
            }
            return Op.R_NEXT;
        } catch (SocketException e) {
            return Op.R_NEXT;
        } catch (IOException e) {
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
        }
    }

    private static int nativeClose(Frame frame, SocketHandle hSocket) {
        closeQuietly(javaSocket(hSocket));
        hSocket.socket = null;
        if (hSocket.f_context.getService() instanceof SocketHandle hSvc) {
            hSvc.socket = null;
        }
        return Op.R_NEXT;
    }

    private static void closeQuietly(Socket sock) {
        if (sock != null) {
            try {
                sock.close();
            } catch (IOException ignore) {
            }
        }
    }

    private static Throwable unwrap(Throwable e) {
        return e instanceof ExecutionException && e.getCause() != null
                ? e.getCause()
                : e;
    }

    private static SocketHandle requireSocketHandle(ObjectHandle h) {
        ObjectHandle origin = h.revealOrigin();
        if (origin instanceof SocketHandle socketHandle) {
            return socketHandle;
        }
        return h instanceof SocketHandle socketHandle ? socketHandle : null;
    }


    // ----- handle --------------------------------------------------------------------------------

    public static class SocketHandle
            extends ServiceHandle {
        public volatile Socket socket;

        public SocketHandle(TypeComposition clazz, ServiceContext context) {
            super(clazz, context);
        }
    }


    // ----- fields --------------------------------------------------------------------------------

    private TypeConstant m_typeCanonical;
}
