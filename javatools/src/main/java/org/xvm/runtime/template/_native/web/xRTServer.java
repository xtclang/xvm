package org.xvm.runtime.template._native.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.InetSocketAddress;
import java.net.Socket;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Principal;
import java.security.PrivateKey;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509ExtendedKeyManager;

import org.eclipse.jetty.http.HostPortHttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;

import org.eclipse.jetty.io.Content;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.IO;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xByteArray;
import org.xvm.runtime.template.collections.xTuple;

import org.xvm.runtime.template.numbers.xUInt16;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.collections.arrays.xRTStringDelegate.StringArrayHandle;

import org.xvm.runtime.template._native.crypto.xRTKeyStore.KeyStoreHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;

import org.xvm.util.Handy;

/**
 * Native implementation of the RTServer.x service that uses Jetty Core.
 */
public class xRTServer
        extends xService {
    public static xRTServer INSTANCE;

    public xRTServer(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public void initNative() {
        markNativeMethod("bindImpl"        , null, VOID);
        markNativeMethod("addRouteImpl"    , null, VOID);
        markNativeMethod("removeRouteImpl" , null, VOID);
        markNativeMethod("replaceRouteImpl", null, BOOLEAN);
        markNativeMethod("setHeaders"      , null, VOID);
        markNativeMethod("setBodyBytes"    , null, VOID);
        markNativeMethod("closeImpl"       , VOID, VOID);

        markNativeMethod("getReceivedAtAddress",   null, null);
        markNativeMethod("getReceivedFromAddress", null, null);
        markNativeMethod("getHostInfo",            null, null);
        markNativeMethod("getProtocolString",      null, null);
        markNativeMethod("getHeaderNames",         null, null);
        markNativeMethod("getHeaderValuesForName", null, null);
        markNativeMethod("readBody",               null, null);
        markNativeMethod("containsNestedBodies",   null, null);

        invalidateTypeInfo();
    }

    @Override
    public TypeConstant getCanonicalType() {
        TypeConstant type = m_typeCanonical;
        if (type == null) {
            m_typeCanonical = type = f_struct.getChild("HttpServer").getIdentityConstant().getType();
        }
        return type;
    }

    /**
     * Injection support method.
     */
    public ObjectHandle ensureServer(Frame frame, ObjectHandle hOpts) {
        TypeComposition clz     = getCanonicalClass();
        MethodStructure ctor    = getStructure().findConstructor();
        ServiceContext  context = f_container.createServiceContext("HttpServer");

        int iResult = context.sendConstructRequest(frame, clz, ctor, null,
                            new ObjectHandle[ctor.getMaxVars()], Op.A_STACK);
        return frame.popResult(iResult);
    }

    @Override
    protected ServiceHandle createStructHandle(TypeComposition clazz, ServiceContext context) {
        return new HttpServerHandle(clazz.ensureAccess(Constants.Access.STRUCT), context);
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        switch (method.getName()) {
        case "getProtocolString":
            return invokeGetProtocol(frame, (HttpContextHandle) hArg, iReturn);

        case "getHeaderNames":
            return invokeGetHeaderNames(frame, (HttpContextHandle) hArg, iReturn);

        case "containsNestedBodies":
            return invokeContainsBodies(frame, (HttpContextHandle) hArg, iReturn);

        case "removeRouteImpl":
            return invokeRemoveRoute((HttpServerHandle) hTarget, (StringHandle) hArg);
        }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn) {
        HttpServerHandle hServer = (HttpServerHandle) hTarget;
        switch (method.getName()) {
        case "bindImpl":
            assert frame.f_context == hServer.f_context;
            return invokeBind(frame, hServer, ahArg);

        case "addRouteImpl":
            assert frame.f_context == hServer.f_context;
            return invokeAddRoute(frame, hServer, ahArg);

        case "replaceRouteImpl":
            assert frame.f_context == hServer.f_context;
            return invokeReplaceRoute(frame, hServer, ahArg, iReturn);

        case "setHeaders":
            return frame.f_context == hServer.f_context
                    ? invokeSetHeaders(frame, ahArg)
                    : xRTFunction.makeAsyncNativeHandle(method).
                            call1(frame, hServer, ahArg, iReturn);

        case "setBodyBytes":
            return frame.f_context == hServer.f_context
                    ? invokeSetBodyBytes(frame, ahArg)
                    : xRTFunction.makeAsyncNativeHandle(method).
                            call1(frame, hServer, ahArg, iReturn);

        case "readBody":
            return frame.f_context == hServer.f_context
                    ? invokeReadBody(frame, ahArg, iReturn)
                    : xRTFunction.makeAsyncNativeHandle(method).
                            call1(frame, hServer, ahArg, iReturn);

        case "closeImpl":
            assert frame.f_context == hServer.f_context;
            return invokeClose(frame, hServer);
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        switch (method.getName()) {
        case "getReceivedAtAddress":
            return invokeGetReceivedAtAddress(frame, (HttpContextHandle) ahArg[0], aiReturn);

        case "getReceivedFromAddress":
            return invokeGetReceivedFromAddress(frame, (HttpContextHandle) ahArg[0], aiReturn);

        case "getHostInfo":
            return invokeGetHostInfo(frame, (HttpContextHandle) ahArg[0], aiReturn);

        case "getHeaderValuesForName":
            return invokeGetHeaderValues(frame, (HttpContextHandle) ahArg[0],
                    (StringHandle) ahArg[1], aiReturn);
        }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }

    // ----- native implementations ----------------------------------------------------------------

    /**
     * Implementation of
     * "void bindImpl(HostInfo binding, String bindAddr, UInt16 httpPort, UInt16 httpsPort)" method.
     */
    private int invokeBind(Frame frame, HttpServerHandle hServer, ObjectHandle[] ahArg) {
        if (hServer.getBinding() != null) {
            return frame.raiseException(xException.illegalState(frame, "Server is already configured"));
        }

        ObjectHandle hBinding   =                   ahArg[0];
        String       sBindAddr  = ((StringHandle)   ahArg[1]).getStringValue();
        int          nHttpPort  = (int) ((JavaLong) ahArg[2]).getValue();
        int          nHttpsPort = (int) ((JavaLong) ahArg[3]).getValue();
        Container    container  = frame.f_context.f_container;

        Router           router = new Router(hBinding);
        QueuedThreadPool pool   = new QueuedThreadPool();
        pool.setName("HttpHandler");
        pool.setDaemon(true);

        Server  server  = new Server(pool);
        Binding binding = new Binding(server, router, null);
        server.setHandler(router);
        hServer.setBinding(binding);

        try {
            configureHttpServer (binding, new InetSocketAddress(sBindAddr, nHttpPort));
            configureHttpsServer(binding, new InetSocketAddress(sBindAddr, nHttpsPort));
        } catch (GeneralSecurityException e) {
            hServer.setBinding(null);
            return frame.raiseException(xException.obscureIoException(frame, e.getMessage()));
        }

        CompletableFuture<Void> cf = container.scheduleIO(() -> {
            try {
                server.start();
            } catch (Exception e) {
                try {
                    server.stop();
                } catch (Exception eStop) {
                    e.addSuppressed(eStop);
                }
                throw e;
            }
            return null;
        });

        return frame.waitForIO(cf, frameCaller -> {
            try {
                cf.get();
                if (hServer.getBinding() != binding || !server.isStarted()) {
                    return frameCaller.raiseException(xException.obscureIoException(frameCaller,
                            "Server closed while binding"));
                }

                // prevent the container from being terminated
                container.registerNativeCallback();
                router.f_fRegistered.set(true);
                return Op.R_NEXT;
            } catch (Exception e) {
                hServer.clear(binding);
                return raiseIOException(frameCaller, e);
            }
        });
    }

    private void configureHttpServer(Binding binding, InetSocketAddress addr) {
        HttpConfiguration config    = new HttpConfiguration();
        Server            server    = binding.server();
        ServerConnector   connector = new ServerConnector(server, new HttpConnectionFactory(config));

        config.setSendServerVersion(false);
        connector.setHost(addr.getHostString());
        connector.setPort(addr.getPort());
        server.addConnector(connector);
    }

    private void configureHttpsServer(Binding binding, InetSocketAddress addr)
            throws GeneralSecurityException {
        SSLContext ctxSSL = SSLContext.getInstance("TLS");
        Server     server = binding.server();

        KeyManager[] aKeyManagers = new KeyManager[] {new SimpleKeyManager(binding.router())};
        ctxSSL.init(aKeyManagers, null, null);

        SslContextFactory.Server ssl = new SslContextFactory.Server();
        ssl.setSslContext(ctxSSL);
        ssl.setNeedClientAuth(false);

        HttpConfiguration       config     = new HttpConfiguration();
        SecureRequestCustomizer customizer = new SecureRequestCustomizer();

        // certificate selection and host routing are handled by our key manager and router
        customizer.setSniHostCheck(false);
        config.addCustomizer(customizer);
        config.setSendServerVersion(false);

        HttpConnectionFactory http11    = new HttpConnectionFactory(config);
        ServerConnector       connector = new ServerConnector(server,
                new SslConnectionFactory(ssl, http11.getProtocol()), http11);
        connector.setHost(addr.getHostString());
        connector.setPort(addr.getPort());
        server.addConnector(connector);
    }

    /**
     * Implementation of "void addRouteImpl(String hostName, UInt16 httpPort, UInt16 httpsPort,
     *                     HandlerWrapper wrapper, KeyStore keystore, String? tlsKey=Null)" method.
     */
    private int invokeAddRoute(Frame frame, HttpServerHandle hServer, ObjectHandle[] ahArg) {
        String         sHostName  = ((StringHandle) ahArg[0]).getStringValue();
        int            nHttpPort  = (int) ((JavaLong) ahArg[1]).getValue();
        int            nHttpsPort = (int) ((JavaLong) ahArg[2]).getValue();
        ServiceHandle  hWrapper   = (ServiceHandle) ahArg[3];
        KeyStoreHandle hKeystore  = ahArg[4] instanceof KeyStoreHandle hK ? hK : null;
        String         sTlsKey    = ahArg[5] instanceof StringHandle hS ? hS.getStringValue() : null;
        Binding        binding    = hServer.getBinding();

        if (binding == null) {
            return frame.raiseException(xException.illegalState(frame, "Server is not bound"));
        }

        Router router = binding.router();

        if (hKeystore != null) {
            KeyStore keystore = hKeystore.f_keyStore;
            if (sTlsKey != null && !isValidPair(hKeystore, sTlsKey)) {
                return frame.raiseException("Invalid or missing entry for Tls key: \"" + sTlsKey + '"');
            }

            if (sTlsKey == null && !router.hasRoutes()) {
                // find a public/private key pair that could be used to encrypt tls communications
                try {
                    for (Enumeration<String> it = keystore.aliases(); it.hasMoreElements();) {
                        String sName = it.nextElement();
                        if (isValidPair(hKeystore, sName)) {
                            sTlsKey = sName;
                            break;
                        }
                    }
                } catch (KeyStoreException ignore) {}

                if (sTlsKey == null) {
                    return frame.raiseException("The Tls key name must be specified");
                }
            }
        }

        RequestHandler handler = createRequestHandler(frame, hWrapper, router.getBinding());
        RouteInfo      route   = new RouteInfo(handler, nHttpPort, nHttpsPort, hKeystore, sTlsKey);

        ServerConnector connector = (ServerConnector) binding.server().getConnectors()[0];
        if (connector.getHost().equals(sHostName)) {
            // the "direct" route is only used by the KeyManager when a host name is missing
            router.setDirectRoute(route);
        }

        router.addRoute(sHostName, route);
        return Op.R_NEXT;
    }

    private boolean isValidPair(KeyStoreHandle hKeystore, String sName) {
        KeyStore keystore = hKeystore.f_keyStore;
        try {
            return keystore.isKeyEntry(sName) &&
                   hKeystore.getKey(sName) instanceof PrivateKey &&
                   keystore.getCertificate(sName) != null;
        } catch (GeneralSecurityException e) {return false;}
    }

    /**
     * Implementation of "Boolean replaceRouteImpl(String hostName, HandlerWrapper wrapper)" method.
     */
    private int invokeReplaceRoute(Frame frame, HttpServerHandle hServer, ObjectHandle[] ahArg, int iResult) {
        String        sHostName = ((StringHandle) ahArg[0]).getStringValue();
        ServiceHandle hWrapper  = (ServiceHandle) ahArg[1];
        Binding       binding   = hServer.getBinding();

        if (binding == null) {
            return frame.assignValue(iResult, xBoolean.FALSE);
        }

        Router    router = binding.router();
        RouteInfo info   = router.getRoute(sHostName);
        if (info == null) {
            return frame.assignValue(iResult, xBoolean.FALSE);
        }

        RequestHandler handler = createRequestHandler(frame, hWrapper, router.getBinding());
        router.addRoute(sHostName,
            new RouteInfo(handler, info.nHttpPort, info.nHttpsPort, info.hKeyStore, info.sTlsKey));
        return frame.assignValue(iResult, xBoolean.TRUE);
    }

    private RequestHandler createRequestHandler(Frame frame, ServiceHandle hWrapper, ObjectHandle hBinding) {
        ClassStructure  clzHandler = hWrapper.getTemplate().getStructure();
        MethodStructure method     = clzHandler.findMethodDeep("handle", m -> m.getParamCount() == 5);
        assert method != null;

        FunctionHandle hFunction =
                xRTFunction.makeInternalHandle(frame, method).bindTarget(frame, hWrapper);
        return new RequestHandler(hWrapper.f_context, hFunction, hBinding);
    }

    /**
     * Implementation of "void removeRouteImpl(String hostName)" method.
     */
    private int invokeRemoveRoute(HttpServerHandle hServer, StringHandle hHostName) {
        Binding binding = hServer.getBinding();
        if (binding != null) {
            binding.router().removeRoute(hHostName.getStringValue());
        }
        return Op.R_NEXT;
    }

    /**
     * Implementation of "(Byte[], UInt16) getReceivedAtAddress(RequestContext context)" method.
     */
    private int invokeGetReceivedAtAddress(Frame frame, HttpContextHandle hCtx, int[] aiResult) {
        InetSocketAddress addr  = (InetSocketAddress)
                                    hCtx.f_request.getConnectionMetaData().getLocalSocketAddress();
        byte[]            ab    = addr.getAddress().getAddress();
        int               nPort = addr.getPort();

        return frame.assignValues(aiResult,
                xByteArray.makeByteArrayHandle(ab, Mutability.Constant),
                xUInt16.INSTANCE.makeJavaLong(nPort));
    }

    /**
     * Implementation of "(Byte[], UInt16) getReceivedFromAddress(RequestContext context)" method.
     */
    private int invokeGetReceivedFromAddress(Frame frame, HttpContextHandle hCtx, int[] aiResult) {
        InetSocketAddress addr  = (InetSocketAddress)
                                    hCtx.f_request.getConnectionMetaData().getRemoteSocketAddress();
        byte[]            ab    = addr.getAddress().getAddress();
        int               nPort = addr.getPort();

        return frame.assignValues(aiResult,
                xByteArray.makeByteArrayHandle(ab, Mutability.Constant),
                xUInt16.INSTANCE.makeJavaLong(nPort));
    }

    /**
     * Implementation of "conditional (String, Uint16) getHostInfo(RequestContext context)" method.
     */
    private int invokeGetHostInfo(Frame frame, HttpContextHandle hCtx, int[] aiResult) {
        Request           request = hCtx.f_request;
        HostPortHttpField host    = (HostPortHttpField) request.getHeaders().getField(HttpHeader.HOST);
        String            sName   = extractHostName(host);
        int               nPort   = extractHostPort(host, request.isSecure());

        return sName == null
            ? frame.assignValue(aiResult[0], xBoolean.FALSE)
            : frame.assignValues(aiResult, xBoolean.TRUE,
                    xString.makeHandle(sName), xUInt16.INSTANCE.makeJavaLong(nPort));
    }

    /**
     * Implementation of "String getProtocolString(RequestContext context)" method.
     */
    private int invokeGetProtocol(Frame frame, HttpContextHandle hCtx, int iResult) {
        String sProtocol = hCtx.f_request.getConnectionMetaData().getProtocol();

        return frame.assignValue(iResult, xString.makeHandle(sProtocol));
    }

    /**
     * Implementation of "String[] getHeaderNames(RequestContext context)" method.
     */
    private int invokeGetHeaderNames(Frame frame, HttpContextHandle hCtx, int iResult) {
        HttpFields headers = hCtx.f_request.getHeaders();

        return frame.assignValue(iResult,
                xString.makeArrayHandle(headers.getFieldNamesCollection().toArray(Utils.NO_NAMES)));
    }

    /**
     * Implementation of "conditional String[] getHeaderValuesForName(RequestContext context,
     * String name)" method.
     */
    private int invokeGetHeaderValues(Frame frame, HttpContextHandle hCtx,
                                      StringHandle hName, int[] aiResult) {
        HttpFields headers = hCtx.f_request.getHeaders();
        String     sName   = hName.getStringValue();

        List<String> listValues = headers.getValuesList(sName);
        if (listValues.isEmpty()) {
            return frame.assignValue(aiResult[0], xBoolean.FALSE);
        }

        return frame.assignValues(aiResult, xBoolean.TRUE,
                xString.makeArrayHandle(listValues.toArray(Utils.NO_NAMES)));
    }

    /**
     * Implementation of "Byte[] readBody(RequestContext context, Int size)" method.
     */
    private int invokeReadBody(Frame frame, ObjectHandle[] ahArg, int iResult) {
        HttpContextHandle hCtx = (HttpContextHandle) ahArg[0];
        long              cb   = ((JavaLong) ahArg[1]).getValue();

        CompletableFuture<byte[]> cf = frame.f_context.f_container.scheduleIO(
                () -> hCtx.readBody((int) Math.min(cb, Integer.MAX_VALUE)));
        return frame.waitForIO(cf, frameCaller -> {
            try {
                byte[] ab = cf.get();
                return frameCaller.assignValue(iResult, ab.length == 0
                        ? xByteArray.ensureEmptyByteArray()
                        : xByteArray.makeByteArrayHandle(ab, Mutability.Constant));
            } catch (Exception e) {
                return raiseIOException(frameCaller, e);
            }
        });
    }

    /**
     * Implementation of
     *  "Boolean containsNestedBodies(RequestContext context)" method.
     */
    private int invokeContainsBodies(Frame frame, HttpContextHandle hCtx, int iResult) {
        // TODO implement
        return frame.assignValue(iResult, xBoolean.FALSE);
    }

    /**
     * Implementation of "closeImpl()" method.
     */
    private int invokeClose(Frame frame, HttpServerHandle hServer) {
        Binding binding = hServer.getBinding();
        if (binding == null) {
            return Op.R_NEXT;
        }

        if (binding.closing() != null) {
            return frame.waitForExternalCompletion(binding.closing(), Op.A_IGNORE, _ -> Op.R_NEXT);
        }

        CompletableFuture<ObjectHandle> cfClosing = new CompletableFuture<>();
        Binding                         closing   = binding.stopping(cfClosing);
        hServer.setBinding(closing);

        CompletableFuture<Void> cfStop = frame.f_context.f_container.scheduleIO(() -> {
            binding.server().stop();
            return null;
        });

        return frame.waitForIO(cfStop, frameCaller -> {
            try {
                cfStop.get();
                return Op.R_NEXT;
            } catch (Exception e) {
                return raiseIOException(frameCaller, e);
            } finally {
                Router router = closing.router();
                if (router.f_fRegistered.compareAndSet(true, false)) {
                    frameCaller.f_context.f_container.unregisterNativeCallback();
                }
                router.clearRoutes();
                hServer.clear(closing);

                // notify other close callers after cleanup
                if (frameCaller.m_hException == null) {
                    cfClosing.complete(xTuple.H_VOID);
                } else {
                    cfClosing.completeExceptionally(frameCaller.m_hException.getException());
                }
            }
        });
    }

    /**
     * Implementation of "void setHeaders(RequestContext context, Int status, String[] names,
     *                                    String[] values, Int responseLength)" method.
     */
    private int invokeSetHeaders(Frame frame, ObjectHandle[] ahArg) {
        HttpContextHandle hCtx          = (HttpContextHandle) ahArg[0];
        long              nStatus       = ((JavaLong) ahArg[1]).getValue();
        StringArrayHandle hHeaderNames  = (StringArrayHandle) ((ArrayHandle) ahArg[2]).m_hDelegate;
        StringArrayHandle hHeaderValues = (StringArrayHandle) ((ArrayHandle) ahArg[3]).m_hDelegate;
        long              nLength       = ((JavaLong) ahArg[4]).getValue();

        try {
            synchronized (hCtx) {
                if (hCtx.m_stage == Stage.COMPLETED) {
                    throw new IOException("HTTP exchange already completed");
                }
                if (hCtx.m_stage != Stage.OPEN) {
                    throw new IOException("HTTP response already started");
                }

                Response           response = hCtx.f_response;
                HttpFields.Mutable headers  = response.getHeaders();

                response.setStatus((int) nStatus);
                for (long i = 0, c = hHeaderNames.m_cSize; i < c; i++) {
                    headers.add(hHeaderNames.get(i), hHeaderValues.get(i));
                }
                if (nLength > 0) {
                    headers.put(HttpHeader.CONTENT_LENGTH, nLength);
                }
                hCtx.m_stage = Stage.HEADERS_SET;
            }
            return Op.R_NEXT;
        } catch (Exception e) {
            hCtx.complete(e);
            return raiseIOException(frame, e);
        }
    }

    /**
     * Implementation of "void setBodyBytes(RequestContext context, Byte[] bytes, Boolean final)"
     * method.
     */
    private int invokeSetBodyBytes(Frame frame, ObjectHandle[] ahArg) {
        HttpContextHandle hCtx   = (HttpContextHandle) ahArg[0];
        ArrayHandle       hBody  = (ArrayHandle) ahArg[1];
        boolean           fFinal = ((BooleanHandle) ahArg[2]).get();

        byte[]                  abBody = xByteArray.getBytes(hBody);
        CompletableFuture<Void> cf     = frame.f_context.f_container.scheduleIO(() -> {
            hCtx.writeBody(abBody, fFinal);
            return null;
        });

        return frame.waitForIO(cf, frameCaller -> {
            try {
                cf.get();
                return Op.R_NEXT;
            } catch (Exception e) {
                return raiseIOException(frameCaller, e);
            }
        });
    }

    // ----- helper methods ------------------------------------------------------------------------

    private static int raiseIOException(Frame frame, Exception e) {
        Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
        return frame.raiseException(xException.obscureIoException(frame, cause.getMessage()));
    }

    private static String extractHostName(HostPortHttpField host) {
        if (host == null) {
            return null;
        }

        // match HostInfo.host.toString(), which omits IPv6 brackets
        String sName = host.getHost();
        return sName != null && sName.startsWith("[")
                ? sName.substring(1, sName.length() - 1)
                : sName;
    }

    private static int extractHostPort(HostPortHttpField host, boolean fTls) {
        int nDefaultPort = fTls ? 443 : 80;
        return host == null ? nDefaultPort : host.getPort(nDefaultPort);
    }

    // ----- helper classes ------------------------------------------------------------------------

    /**
     * The bridge that handles requests from Jetty and calls the natural HttpServer.Handler
     * "handle()" method.
     */
    protected static class RequestHandler {
        public RequestHandler(ServiceContext context, FunctionHandle hFunction, ObjectHandle hBinding) {
            f_context   = context;
            f_hFunction = hFunction;
            f_hBinding  = hBinding;
        }

        public void handle(HttpContextHandle hCtx) {
            try (var ignore = ConstantPool.withPool(f_context.f_pool)) {
                // call the Handler handle method
                ObjectHandle[] hArgs = createArguments(hCtx);
                f_context.postRequest(null, f_hFunction, hArgs, 0).handle((response, err) -> {
                    // process the response (or error) from calling the Handler handle method
                    // TODO: this should be sent to the natural "unhandledException" handler
                    if (err != null) {
                        sendError(hCtx, err);
                    }
                    return null;
                });
            } catch (Throwable t) {
                sendError(hCtx, t);
            }
        }

        private ObjectHandle[] createArguments(HttpContextHandle hCtx) {
            Request       request = hCtx.f_request;
            StringHandle  hURI    = xString.makeHandle(request.getHttpURI().getPathQuery());
            StringHandle  hMethod = xString.makeHandle(request.getMethod());
            BooleanHandle hTls    = xBoolean.makeHandle(request.isSecure());
            return new ObjectHandle[]{f_hBinding, hCtx, hURI, hMethod, hTls};
        }

        private void sendError(HttpContextHandle hCtx, Throwable t) {
            t.printStackTrace();
            boolean fStarted;
            synchronized (hCtx) {
                if (hCtx.m_stage == Stage.COMPLETED) {
                    return;
                }
                fStarted = hCtx.f_response.isCommitted() || hCtx.m_stage != Stage.OPEN;
                if (!fStarted) {
                    hCtx.f_response.setStatus(500);
                    hCtx.m_stage = Stage.HEADERS_SET;
                }
            }
            if (fStarted) {
                hCtx.complete(t);
            } else {
                f_context.f_container.scheduleIO(() -> {
                    hCtx.writeBody(Handy.EMPTY_BYTE_ARRAY, true);
                    return null;
                });
            }
        }

        private final ServiceContext f_context;
        private final FunctionHandle f_hFunction;
        private final ObjectHandle   f_hBinding;
    }

    /**
     * X509ExtendedKeyManager based on a single key/certificate.
     */
    protected static class SimpleKeyManager
            extends X509ExtendedKeyManager {
        public SimpleKeyManager(Router router) {
            f_router = router;
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String chooseEngineClientAlias(String[] asKeyType, Principal[] issuers, SSLEngine engine) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String chooseClientAlias(String[] asKeyType, Principal[] issuers, Socket socket) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            SSLSession session = engine.getHandshakeSession();
            if (session instanceof ExtendedSSLSession sessionEx) {
                List<SNIServerName> listNames = sessionEx.getRequestedServerNames();

                String sHost = listNames.isEmpty()
                        ? null
                        : ((SNIHostName) listNames.get(0)).getAsciiName();

                // the main reason the server name may be missing is for an HTTPS request that
                // goes directly against the IP address (e.g. "https://129.168.1.30:8081/nginx")
                RouteInfo route = sHost == null
                        ? f_router.getDirectRoute()
                        : f_router.getRoute(sHost);
                if (route == null) {
                    // TODO: REMOVE
                    System.err.println(Handy.logTime() + " Trace: Handshake with unknown host: " + sHost);
                } else {
                    f_tloKeyStore.set(route.hKeyStore);
                    return route.sTlsKey;
                }
            } else {
                // TODO: REMOVE
                System.err.println(Handy.logTime() + " Trace: Handshake from unknown session " +
                    (session == null ? "" : session.getClass().getName()));
            }
            return null;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            throw new UnsupportedOperationException();
        }

        @Override
        public X509Certificate[] getCertificateChain(String sAlias) {
            try {
                Certificate[] aCerts = f_tloKeyStore.get().f_keyStore.getCertificateChain(sAlias);
                if (aCerts instanceof X509Certificate[] aX509Certs) {
                    return aX509Certs;
                }
                int               cCerts     = aCerts.length;
                X509Certificate[] aX509Certs = new X509Certificate[cCerts];

                // this call also asserts that all certificates are X509Certificate instances
                System.arraycopy(aCerts, 0, aX509Certs, 0, cCerts);
                return aX509Certs;
            } catch (KeyStoreException e) {
                return new X509Certificate[0];
            }
        }

        @Override
        public PrivateKey getPrivateKey(String sAlias) {
            try {
                KeyStoreHandle hKeyStore = f_tloKeyStore.get();
                return (PrivateKey) hKeyStore.getKey(sAlias);
            } catch (GeneralSecurityException e) {
                return null;
            }
        }

        // ----- data fields -----------------------------------------------------------------------

        /**
         * The router for this server binding.
         */
        private final Router f_router;

        /**
         * The key store handle used by the current thread.
         */
        private final ThreadLocal<KeyStoreHandle> f_tloKeyStore = new ThreadLocal<>();
    }

    // ---- Router ---------------------------------------------------------------------------------

    protected static class Router
            extends Handler.Abstract.NonBlocking {
        public Router(ObjectHandle hBinding) {
            f_hBinding = hBinding;
        }

        private final Map<String, RouteInfo> mapRoutes = new ConcurrentHashMap<>();

        private final AtomicBoolean f_fRegistered = new AtomicBoolean();

        private final ObjectHandle f_hBinding;

        private RouteInfo m_routeDirect;

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            boolean           fTls  = request.isSecure();
            HostPortHttpField host  = (HostPortHttpField) request.getHeaders().getField(HttpHeader.HOST);
            String            sName = extractHostName(host);
            int               nPort = extractHostPort(host, fTls);
            RouteInfo         route = sName == null ? null : getRoute(sName);

            if (route == null || nPort != (fTls ? route.nHttpsPort : route.nHttpPort)) {
                String sHost = host == null ? null : host.getValue();
                System.err.println(Handy.logTime() + " Trace: Request for unregistered route: "
                    + (fTls ? "https://" : "http://") + sHost + request.getHttpURI().getPathQuery() + " from "
                    + Request.getRemoteAddr(request));
                response.setStatus(421); // HttpStatus.MisdirectedRequest
                response.write(true, null, callback);
            } else {
                route.handler.handle(new HttpContextHandle(request, response, callback));
            }
            return true;
        }

        protected boolean hasRoutes() {
            return !mapRoutes.isEmpty();
        }

        protected RouteInfo getRoute(String sHostName) {
            return mapRoutes.get(sHostName);
        }

        protected void addRoute(String sHostName, RouteInfo route) {
            mapRoutes.put(sHostName, route);
        }

        protected void removeRoute(String sHostName) {
            mapRoutes.remove(sHostName);
        }

        protected void clearRoutes() {
            mapRoutes.clear();
        }

        protected ObjectHandle getBinding() {
            return f_hBinding;
        }

        protected RouteInfo getDirectRoute() {
            return m_routeDirect;
        }

        protected void setDirectRoute(RouteInfo route) {
            m_routeDirect = route;
        }
    }

    protected record RouteInfo(RequestHandler handler, int nHttpPort, int nHttpsPort,
                               KeyStoreHandle hKeyStore, String sTlsKey) {}

    /**
     * The native state associated with a server binding. Replaced when shutdown starts and
     * cleared when it completes.
     */
    protected record Binding(Server server, Router router, CompletableFuture<ObjectHandle> closing) {
        Binding stopping(CompletableFuture<ObjectHandle> cf) {
            return new Binding(server, router, cf);
        }
    }

    // ----- ObjectHandles -------------------------------------------------------------------------

    /**
     * A {@link ServiceHandle} for the HttpServer service.
     */
    protected static class HttpServerHandle
            extends ServiceHandle {
        /**
         * The underlying native state is shared, so cloning the handle does not splinter it.
         * Updates are serialized by the server's service context.
         */
        private final AtomicReference<Binding> f_binding = new AtomicReference<>();

        protected HttpServerHandle(TypeComposition clazz, ServiceContext context) {
            super(clazz, context);
        }

        /**
         * @return the current server binding, or null if unbound
         */
        protected Binding getBinding() {
            return f_binding.get();
        }

        /**
         * Replace the current server binding.
         */
        protected void setBinding(Binding binding) {
            f_binding.set(binding);
        }

        /**
         * Clear the binding only if it is still the specified one.
         */
        protected void clear(Binding binding) {
            f_binding.compareAndSet(binding, null);
        }

        @Override
        public String toString() {
            Binding binding = getBinding();
            return "HttpServer" + (binding == null
                    ? ""
                    : "@" + ((ServerConnector) binding.server().getConnectors()[0]).getHost());
        }
    }

    /**
     * Native handle holding the Jetty request and its response completion.
     */
    protected static class HttpContextHandle
                extends ObjectHandle {
        public HttpContextHandle(Request request, Response response, Callback callback) {
            super(xObject.INSTANCE.getCanonicalClass());

            f_request  = request;
            f_response = response;
            f_callback = callback;
            m_fMutable = false;

            // allow slow handlers; pending reads and writes still time out
            request.addIdleTimeoutListener(_ -> false);
            request.addFailureListener(this::complete);
        }

        /**
         * Read at most the requested number of body bytes using Jetty's blocking input stream.
         * Wait for the requested byte count or the end of the body. A partly consumed chunk is
         * retained for the next read.
         */
        protected byte[] readBody(int cb)
                throws IOException {
            InputStream in;
            synchronized (this) {
                if (cb < 0 || m_fReading || m_stage == Stage.COMPLETED) {
                    throw new IOException("Invalid HTTP body read");
                }
                if (cb == 0) {
                    return Handy.EMPTY_BYTE_ARRAY;
                }
                if (m_in == null) {
                    m_in = Content.Source.asInputStream(f_request);
                }
                in         = m_in;
                m_fReading = true;
            }

            try {
                if (cb == Integer.MAX_VALUE) {
                    return in.readAllBytes();
                }
                // we cannot simply call
                //     return in.readNBytes(cb);
                // due to a bug in Jetty's ContentSourceInputStream that can block on that extra
                // zero-length read issued by readNBytes(int)
                byte[] ab     = new byte[cb];
                int    cbRead = in.readNBytes(ab, 0, cb);
                return cbRead == cb ? ab : Arrays.copyOf(ab, cbRead);
            } catch (IOException e) {
                complete(e);
                throw e;
            } finally {
                boolean fClose;
                synchronized (this) {
                    m_fReading = false;
                    fClose     = m_stage == Stage.COMPLETED;
                }
                if (fClose) {
                    IO.close(in);
                }
            }
        }

        /**
         * Write response body bytes using Jetty's blocking output stream. A final write closes the
         * stream and completes the HTTP exchange.
         *
         * @param ab      the response body bytes
         * @param fFinal  true if this is the last write
         */
        protected void writeBody(byte[] ab, boolean fFinal)
                throws IOException {
            try {
                OutputStream out;
                synchronized (this) {
                    if (m_stage != Stage.HEADERS_SET || m_fWriting) {
                        throw new IOException("Invalid HTTP response write");
                    }
                    if (m_out == null) {
                        m_out = Content.Sink.asOutputStream(f_response);
                    }
                    out        = m_out;
                    m_fWriting = true;
                    m_stage    = fFinal ? Stage.FINAL_WRITE_PENDING : Stage.HEADERS_SET;
                }

                try {
                    out.write(ab);
                    if (fFinal) {
                        out.close();
                        complete(null);
                    }
                } finally {
                    m_fWriting = false;
                }
            } catch (IOException e) {
                complete(e);
                throw e;
            }
        }

        /**
         * Complete Jetty's handler only when the response is sent or the exchange fails.
         */
        protected void complete(Throwable e) {
            InputStream in;
            synchronized (this) {
                if (m_stage == Stage.COMPLETED) {
                    return;
                }
                m_stage = Stage.COMPLETED;
                in      = m_fReading ? null : m_in;
                m_in    = null;
                m_out   = null;
            }
            IO.close(in);
            if (e == null) {
                f_callback.succeeded();
            } else {
                f_callback.failed(e);
            }
        }

        /**
         * The wrapped Jetty request, response, and completion callback.
         */
        public final Request  f_request;
        public final Response f_response;
        public final Callback f_callback;

        /**
         * State guarded by this handle's monitor.
         */
        private Stage        m_stage = Stage.OPEN;
        private InputStream  m_in;
        private OutputStream m_out;
        private boolean      m_fReading;

        /**
         * Indicates whether a response write is in progress.
         */
        private volatile boolean m_fWriting;
    }

    private enum Stage {OPEN, HEADERS_SET, FINAL_WRITE_PENDING, COMPLETED}

    // ----- data fields and constants -------------------------------------------------------------

    /**
     * Cached canonical type.
     */
    private TypeConstant m_typeCanonical;
}
