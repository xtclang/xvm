package org.xvm.runtime.template._native.net;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
    public static final int CONNECT_TIMEOUT_MS = 15_000;

    public xRTSocket(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        markNativeMethod("readBytesImpl",      null, null);
        markNativeMethod("writeBytesImpl",     null, null);
        markNativeMethod("availableImpl",      null, null);
        markNativeMethod("shutdownInputImpl",  null, null);
        markNativeMethod("shutdownOutputImpl", null, null);
        markNativeMethod("closeImpl",          null, null);

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
            return xRTFunction.makeAsyncNativeHandle(frame, method).
                    call1(frame, hTarget, new ObjectHandle[] {hArg}, iReturn);
        }

        switch (method.getName()) {
        case "readBytesImpl":
            return invokeReadBytesImpl(frame, hSocket, (int) ((JavaLong) hArg).getValue(), iReturn);
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
            return xRTFunction.makeAsyncNativeHandle(frame, method).call1(frame, hTarget, ahArg, iReturn);
        }

        switch (method.getName()) {
        case "readBytesImpl":
            return invokeReadBytesImpl(frame, hSocket, (int) ((JavaLong) ahArg[0]).getValue(), iReturn);

        case "writeBytesImpl":
            return invokeWriteBytesImpl(frame, hSocket, ahArg);

        case "availableImpl":
            return invokeAvailableImpl(frame, hSocket, iReturn);

        case "shutdownInputImpl":
            return invokeShutdownImpl(frame, hSocket, true);

        case "shutdownOutputImpl":
            return invokeShutdownImpl(frame, hSocket, false);

        case "closeImpl":
            return invokeCloseImpl(frame, hSocket);
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
                Socket      socket  = cf.get();
                InetAddress local   = socket.getLocalAddress();
                byte[]      abLocal = local == null ? new byte[0] : local.getAddress();
                int         nLocal  = socket.getLocalPort();
                return frameCaller.container().nativeTemplates().socket().
                        constructSocket(frameCaller, socket, abLocal, nLocal,
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
        Socket  socket = new Socket();
        boolean owned = false;
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            if (abLocalIP != null && abLocalIP.length > 0) {
                socket.bind(new InetSocketAddress(InetAddress.getByAddress(abLocalIP), nLocalPort));
            } else if (nLocalPort != 0) {
                socket.bind(new InetSocketAddress(nLocalPort));
            }
            socket.connect(new InetSocketAddress(InetAddress.getByAddress(abRemoteIP), nRemotePort),
                    CONNECT_TIMEOUT_MS);
            owned = true;
            return socket;
        } finally {
            if (!owned) {
                closeQuietly(socket);
            }
        }
    }

    protected int constructSocket(Frame frame, Socket socket, byte[] abLocal, int nLocalPort,
                                  byte[] abRemote, int nRemotePort, int[] aiReturn) {
        ConstantPool     pool         = frame.poolContext();
        ClassTemplate    template     = this;
        ClassComposition clz          = template.getCanonicalClass();
        MethodStructure  constructor  = template.getStructure().findConstructor(
                pool.typeByteArray(), pool.typeUInt16(),
                pool.typeByteArray(), pool.typeUInt16());
        ObjectHandle[]   ahParams     = new ObjectHandle[constructor.getMaxVars()];
        ahParams[0] = xArray.makeByteArrayHandle(frame.container(), abLocal, Mutability.Constant);
        ahParams[1] = xUInt16.INSTANCE.makeJavaLong(nLocalPort);
        ahParams[2] = xArray.makeByteArrayHandle(frame.container(), abRemote, Mutability.Constant);
        ahParams[3] = xUInt16.INSTANCE.makeJavaLong(nRemotePort);

        switch (template.construct(frame, constructor, clz, null, ahParams, Op.A_STACK)) {
        case Op.R_NEXT:
            return finishConnect(frame, socket, aiReturn);

        case Op.R_EXCEPTION:
            closeQuietly(socket);
            return Op.R_EXCEPTION;

        case Op.R_CALL:
            frame.m_frameNext.addContinuation(frameCaller ->
                    finishConnect(frameCaller, socket, aiReturn));
            return Op.R_CALL;

        default:
            closeQuietly(socket);
            throw new IllegalStateException();
        }
    }

    private static int finishConnect(Frame frame, Socket socket, int[] aiReturn) {
        ObjectHandle h = frame.popStack();
        SocketHandle hSocket = requireSocketHandle(h);
        if (hSocket == null) {
            closeQuietly(socket);
            return frame.raiseException(xException.illegalState(frame, "socket construct failed"));
        }
        hSocket.socket = socket;
        return frame.assignValues(aiReturn, xBoolean.TRUE, hSocket);
    }


    // ----- I/O -----------------------------------------------------------------------------------

    /**
     * Implementation of "immutable Byte[] readBytesImpl(Int count)" method.
     */
    private static int invokeReadBytesImpl(Frame frame, SocketHandle hSocket, int cBytes, int iReturn) {
        Socket socket = hSocket.socket;
        if (socket == null || socket.isClosed()) {
            return frame.raiseException(xException.ioException(frame, "socket closed"));
        }
        if (cBytes <= 0) {
            return frame.assignValue(iReturn,
                    xArray.makeByteArrayHandle(frame.container(), new byte[0], Mutability.Constant));
        }

        Callable<byte[]> task = () -> {
            InputStream in  = socket.getInputStream();
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
        };
        CompletableFuture<byte[]> cf = frame.f_context.f_container.scheduleIO(task);
        Frame.Continuation continuation = frameCaller -> {
            try {
                return frameCaller.assignValue(iReturn,
                        xArray.makeByteArrayHandle(frameCaller.container(), cf.get(),
                                Mutability.Constant));
            } catch (Throwable e) {
                return frameCaller.raiseException(
                        xException.ioException(frameCaller, unwrap(e).getMessage()));
            }
        };
        return frame.waitForIO(cf, continuation);
    }

    /**
     * Implementation of "void writeBytesImpl(Byte[] bytes, Int offset, Int count)" method.
     */
    private static int invokeWriteBytesImpl(Frame frame, SocketHandle hSocket, ObjectHandle[] ahArg) {
        Socket socket = hSocket.socket;
        if (socket == null || socket.isClosed()) {
            return frame.raiseException(xException.ioException(frame, "socket closed"));
        }
        byte[] ab = xByteArray.getBytes((ArrayHandle) ahArg[0]);
        long   of = ((JavaLong) ahArg[1]).getValue();
        long   n  = ((JavaLong) ahArg[2]).getValue();

        if (n != 0 && (of < 0 || n < 0
                || of > Integer.MAX_VALUE || n > Integer.MAX_VALUE
                || of + n > ab.length)) {
            return frame.raiseException(xException.outOfBounds(frame,
                    "write offset " + of + " count " + n + " length " + ab.length));
        }
        if (n == 0) {
            return Op.R_NEXT;
        }

        int ofWrite = (int) of;
        int nWrite  = (int) n;
        Callable<Void> task = () -> {
            OutputStream out = socket.getOutputStream();
            out.write(ab, ofWrite, nWrite);
            out.flush();
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
     * Implementation of "Int availableImpl()" method.
     */
    private static int invokeAvailableImpl(Frame frame, SocketHandle hSocket, int iReturn) {
        Socket socket = hSocket.socket;
        if (socket == null || socket.isClosed()) {
            return frame.assignValue(iReturn, xInt64.INSTANCE.makeJavaLong(0));
        }
        try {
            int n = socket.getInputStream().available();
            return frame.assignValue(iReturn, xInt64.INSTANCE.makeJavaLong(Math.max(n, 0)));
        } catch (IOException e) {
            return frame.assignValue(iReturn, xInt64.INSTANCE.makeJavaLong(0));
        }
    }

    /**
     * Implementation of "void shutdownInputImpl()" and "void shutdownOutputImpl()" methods.
     */
    private static int invokeShutdownImpl(Frame frame, SocketHandle hSocket, boolean fInput) {
        Socket socket = hSocket.socket;
        if (socket == null || socket.isClosed()) {
            return Op.R_NEXT;
        }
        try {
            if (fInput) {
                socket.shutdownInput();
            } else {
                socket.shutdownOutput();
            }
            return Op.R_NEXT;
        } catch (SocketException ignore) {
            return Op.R_NEXT;
        } catch (IOException e) {
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
        }
    }

    /**
     * Implementation of "void closeImpl()" method.
     */
    private static int invokeCloseImpl(Frame frame, SocketHandle hSocket) {
        closeQuietly(hSocket.socket);
        hSocket.socket = null;
        return Op.R_NEXT;
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignore) {}
        }
    }

    private static Throwable unwrap(Throwable e) {
        return e instanceof ExecutionException && e.getCause() != null
                ? e.getCause()
                : e;
    }

    private static SocketHandle requireSocketHandle(ObjectHandle h) {
        ObjectHandle origin = h.revealOrigin();
        return origin instanceof SocketHandle hSocket ? hSocket :
               h      instanceof SocketHandle hSocket ? hSocket : null;
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
