package org.xvm.runtime.template._native.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import java.io.IOException;
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

import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509ExtendedKeyManager;

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

import org.xvm.runtime.template.numbers.xUInt16;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.collections.arrays.xRTStringDelegate.StringArrayHandle;

import org.xvm.runtime.template._native.crypto.xRTKeyStore.KeyStoreHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * Native implementation of the RTServer.x service that uses native Java {@link HttpServer}.
 */
public class xRTServer
        extends xService
    {
    public static xRTServer INSTANCE;

    public xRTServer(Container container, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("bindImpl"        , null, VOID);
        markNativeMethod("addRouteImpl"    , null, VOID);
        markNativeMethod("removeRouteImpl" , null, VOID);
        markNativeMethod("replaceRouteImpl", null, BOOLEAN);
        markNativeMethod("respond"         , null, VOID);
        markNativeMethod("closeImpl"       , VOID, VOID);

        markNativeMethod("getReceivedAtAddress",   null, null);
        markNativeMethod("getReceivedFromAddress", null, null);
        markNativeMethod("getHostInfo",            null, null);
        markNativeMethod("getProtocolString",      null, null);
        markNativeMethod("getHeaderNames",         null, null);
        markNativeMethod("getHeaderValuesForName", null, null);
        markNativeMethod("getBodyBytes",           null, null);
        markNativeMethod("containsNestedBodies",   null, null);

        invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        TypeConstant type = m_typeCanonical;
        if (type == null)
            {
            m_typeCanonical = type = f_struct.getChild("HttpServer").getIdentityConstant().getType();
            }
        return type;
        }

    /**
     * Injection support method.
     */
    public ObjectHandle ensureServer(Frame frame, ObjectHandle hOpts)
        {
        TypeComposition clz     = getCanonicalClass();
        MethodStructure ctor    = getStructure().findConstructor();
        ServiceContext  context = f_container.createServiceContext("HttpServer");

        switch (context.sendConstructRequest(frame, clz, ctor, null,
                    new ObjectHandle[ctor.getMaxVars()], Op.A_STACK))
            {
            case Op.R_NEXT:
                return frame.popStack();

            case Op.R_CALL:
                return new ObjectHandle.DeferredCallHandle(frame);

            case Op.R_EXCEPTION:
                return new ObjectHandle.DeferredCallHandle(frame.m_hException);

            default:
                throw new IllegalStateException();
            }
        }

    @Override
    protected ServiceHandle createStructHandle(TypeComposition clazz, ServiceContext context)
        {
        return new HttpServerHandle(clazz.ensureAccess(Constants.Access.STRUCT), context);
        }

    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "getProtocolString":
                return invokeGetProtocol(frame, (HttpContextHandle) hArg, iReturn);

            case "getHeaderNames":
                return invokeGetHeaderNames(frame, (HttpContextHandle) hArg, iReturn);

            case "containsNestedBodies":
                return invokeContainsBodies(frame, (HttpContextHandle) hArg, iReturn);

            case "removeRouteImpl":
                return invokeRemoveRoute(frame, (HttpServerHandle) hTarget, (StringHandle) hArg);
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        HttpServerHandle hServer = (HttpServerHandle) hTarget;
        switch (method.getName())
            {
            case "bindImpl":
                assert frame.f_context == hServer.f_context;
                return invokeBind(frame, hServer, ahArg);

            case "addRouteImpl":
                assert frame.f_context == hServer.f_context;
                return invokeAddRoute(frame, hServer, ahArg);

            case "replaceRouteImpl":
                assert frame.f_context == hServer.f_context;
                return invokeReplaceRoute(frame, hServer, ahArg, iReturn);

            case "respond":
                return frame.f_context == hServer.f_context
                        ? invokeRespond(frame, ahArg)
                        : xRTFunction.makeAsyncNativeHandle(method).
                                call1(frame, hServer, ahArg, iReturn);

            case "closeImpl":
                assert frame.f_context == hServer.f_context;
                return invokeClose(hServer);

            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "getReceivedAtAddress":
                return invokeGetReceivedAtAddress(frame, (HttpContextHandle) ahArg[0], aiReturn);

            case "getReceivedFromAddress":
                return invokeGetReceivedFromAddress(frame, (HttpContextHandle) ahArg[0], aiReturn);

            case "getHostInfo":
                return invokeGetHostInfo(frame, (HttpContextHandle) ahArg[0], aiReturn);

            case "getHeaderValuesForName":
                return invokeGetHeaderValues(frame, (HttpContextHandle) ahArg[0],
                        (StringHandle) ahArg[1], aiReturn);

            case "getBodyBytes":
                return invokeGetBody(frame, (HttpContextHandle) ahArg[0], aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- native implementations ----------------------------------------------------------------

    /**
     * Implementation of
     * "void bindImpl(HostInfo binding, String bindAddr, UInt16 httpPort, UInt16 httpsPort)" method.
     */
    private int invokeBind(Frame frame, HttpServerHandle hServer, ObjectHandle[] ahArg)
        {
        if (hServer.getHttpServer() != null)
            {
            return frame.raiseException(xException.illegalState(frame, "Server is already configured"));
            }

        ObjectHandle hBinding   =                   ahArg[0];
        String       sBindAddr  = ((StringHandle)   ahArg[1]).getStringValue();
        int          nHttpPort  = (int) ((JavaLong) ahArg[2]).getValue();
        int          nHttpsPort = (int) ((JavaLong) ahArg[3]).getValue();

        try
            {
            configureHttpServer (hServer, new InetSocketAddress(sBindAddr, nHttpPort));
            configureHttpsServer(hServer, new InetSocketAddress(sBindAddr, nHttpsPort));
            configureBinding(hServer, hBinding);

            HttpServer  httpServer  = hServer.getHttpServer();
            HttpsServer httpsServer = hServer.getHttpsServer();

            // at the moment we only support a single "binding"; set up the thread pool
            String        sName   = "HttpHandler";
            ThreadGroup   group   = new ThreadGroup(sName);
            ThreadFactory factory = r ->
                {
                Thread thread = new Thread(group, r);
                thread.setDaemon(true);
                thread.setName(sName + "@" + thread.hashCode());
                return thread;
                };

            // We don't actually rely on any scaling here; all requests go to a single natural
            // Handler service instance that needs to demultiplex it as quick as possible
            // (see HttpHandler.x in xenia.xtclang.org module).
            // If necessary, we can change the start() method to take an array of handlers and
            // demultiplex it earlier by the native code
            Executor executor = Executors.newCachedThreadPool(factory);

            httpServer.setExecutor(executor);
            httpServer.start();

            httpsServer.setExecutor(executor);
            httpsServer.start();

            // prevent the container from being terminated
            hServer.f_context.f_container.registerNativeCallback();

            Router router = hServer.getRouter();
            httpServer .createContext("/", router);
            httpsServer.createContext("/", router);

            return Op.R_NEXT;
            }
        catch (Exception e)
            {
            frame.f_context.f_container.terminate(hServer.f_context);
            return frame.raiseException(xException.obscureIoException(frame, e.getMessage()));
            }
        }

    private void configureHttpServer(HttpServerHandle hServer, InetSocketAddress addr)
            throws IOException
        {
        hServer.setHttpServer(HttpServer.create(addr, 0));
        }

    private void configureHttpsServer(HttpServerHandle hServer, InetSocketAddress addr)
            throws IOException, GeneralSecurityException
        {
        HttpsServer httpsServer = HttpsServer.create(addr, 0);
        SSLContext  ctxSSL      = SSLContext.getInstance("TLS");

        KeyManager[] aKeyManagers = new KeyManager[] {new SimpleKeyManager(hServer)};
        ctxSSL.init(aKeyManagers, null, null);

        httpsServer.setHttpsConfigurator(new HttpsConfigurator(ctxSSL)
            {
            @Override
            public void configure(HttpsParameters params)
                {
                try
                    {
                    SSLContext ctxSSL = getSSLContext();
                    SSLEngine  engine = ctxSSL.createSSLEngine();

                    params.setNeedClientAuth(false);
                    params.setCipherSuites(engine.getEnabledCipherSuites());
                    params.setProtocols(engine.getEnabledProtocols());
                    params.setSSLParameters(ctxSSL.getSupportedSSLParameters());
                    }
                catch (Exception ex)
                    {
                    throw new RuntimeException("failed to initialize the SSL context", ex);
                    }
                }
            });
        hServer.setHttpsServer(httpsServer);
        }

    private void configureBinding(HttpServerHandle hServer, ObjectHandle hBinding) {
        hServer.setBinding(hBinding);
    }

    /**
     * Implementation of "void addRouteImpl(String hostName, UInt16 httpPort, UInt16 httpsPort,
     *                     HandlerWrapper wrapper, KeyStore keystore, String? tlsKey=Null)" method.
     */
    private int invokeAddRoute(Frame frame, HttpServerHandle hServer, ObjectHandle[] ahArg)
        {
        String         sHostName  = ((StringHandle) ahArg[0]).getStringValue();
        int            nHttpPort  = (int) ((JavaLong) ahArg[1]).getValue();
        int            nHttpsPort = (int) ((JavaLong) ahArg[2]).getValue();
        ServiceHandle  hWrapper   = (ServiceHandle) ahArg[3];
        KeyStoreHandle hKeystore  = ahArg[4] instanceof KeyStoreHandle hK ? hK : null;
        String         sTlsKey    = ahArg[5] instanceof StringHandle hS ? hS.getStringValue() : null;
        Router         router     = hServer.getRouter();

        if (hKeystore != null)
            {
            KeyStore keystore = hKeystore.f_keyStore;
            if (sTlsKey != null && !isValidPair(hKeystore, sTlsKey))
                {
                return frame.raiseException("Invalid or missing entry for Tls key: \"" + sTlsKey + '"');
                }

            if (sTlsKey == null && router.mapRoutes.isEmpty())
                {
                // find a public/private key pair that could be used to encrypt tls communications
                try
                    {
                    for (Enumeration<String> it = keystore.aliases(); it.hasMoreElements();)
                        {
                        String sName = it.nextElement();
                        if (isValidPair(hKeystore, sName))
                            {
                            sTlsKey = sName;
                            break;
                            }
                        }
                    }
                catch (KeyStoreException ignore) {}

                if (sTlsKey == null)
                    {
                    return frame.raiseException("The Tls key name must be specified");
                    }
                }
            }

        RequestHandler handler = createRequestHandler(frame, hWrapper, hServer);
        RouteInfo      route   = new RouteInfo(handler, nHttpPort, nHttpsPort, hKeystore, sTlsKey);

        if (hServer.getHttpServer().getAddress().getHostName().equals(sHostName))
            {
            // the "direct" route is only used by the KeyManager when a host name is missing
            router.setDirectRoute(route);
            }

        router.mapRoutes.put(sHostName, route);
        return Op.R_NEXT;
        }

    private boolean isValidPair(KeyStoreHandle hKeystore, String sName)
        {
        KeyStore keystore = hKeystore.f_keyStore;
        try
            {
            return keystore.isKeyEntry(sName) &&
                   hKeystore.getKey(sName) instanceof PrivateKey &&
                   keystore.getCertificate(sName) != null;
            }
        catch (GeneralSecurityException e) {return false;}
        }

    /**
     * Implementation of "Boolean replaceRouteImpl(String hostName, HandlerWrapper wrapper)" method.
     */
    private int invokeReplaceRoute(Frame frame, HttpServerHandle hServer, ObjectHandle[] ahArg, int iResult)
        {
        String        sHostName = ((StringHandle) ahArg[0]).getStringValue();
        ServiceHandle hWrapper  = (ServiceHandle) ahArg[1];
        Router        router    = hServer.getRouter();
        RouteInfo     info      = router.mapRoutes.get(sHostName);

        if (info == null)
            {
            return frame.assignValue(iResult, xBoolean.FALSE);
            }

        RequestHandler handler = createRequestHandler(frame, hWrapper, hServer);
        router.mapRoutes.put(sHostName,
            new RouteInfo(handler, info.nHttpPort, info.nHttpPort, info.hKeyStore, info.sTlsKey));
        return frame.assignValue(iResult, xBoolean.TRUE);
        }

    private RequestHandler createRequestHandler(Frame frame, ServiceHandle hWrapper, HttpServerHandle hServer)
        {
        ClassStructure  clzHandler = hWrapper.getTemplate().getStructure();
        MethodStructure method     = clzHandler.findMethodDeep("handle", m -> m.getParamCount() == 5);
        assert method != null;

        FunctionHandle hFunction =
                xRTFunction.makeInternalHandle(frame, method).bindTarget(frame, hWrapper);
        return new RequestHandler(hWrapper.f_context, hFunction, hServer);
        }

    /**
     * Implementation of "void removeRouteImpl(String hostName)" method.
     */
    private int invokeRemoveRoute(Frame frame, HttpServerHandle hServer, StringHandle hHostName)
        {
        hServer.getRouter().mapRoutes.remove(hHostName.getStringValue());
        return Op.R_NEXT;
        }

    /**
     * Implementation of "(Byte[], UInt16) getReceivedAtAddress(RequestContext context)" method.
     */
    private int invokeGetReceivedAtAddress(Frame frame, HttpContextHandle hCtx, int[] aiResult)
        {
        InetSocketAddress addr  = hCtx.f_exchange.getLocalAddress();
        byte[]            ab    = addr.getAddress().getAddress();
        int               nPort = addr.getPort();

        return frame.assignValues(aiResult,
                xByteArray.makeByteArrayHandle(ab, Mutability.Constant),
                xUInt16.INSTANCE.makeJavaLong(nPort));
        }

    /**
     * Implementation of "(Byte[], UInt16) getReceivedFromAddress(RequestContext context)" method.
     */
    private int invokeGetReceivedFromAddress(Frame frame, HttpContextHandle hCtx, int[] aiResult)
        {
        InetSocketAddress addr  = hCtx.f_exchange.getRemoteAddress();
        byte[]            ab    = addr.getAddress().getAddress();
        int               nPort = addr.getPort();

        return frame.assignValues(aiResult,
                xByteArray.makeByteArrayHandle(ab, Mutability.Constant),
                xUInt16.INSTANCE.makeJavaLong(nPort));
        }

    /**
     * Implementation of "conditional (String, Uint16) getHostInfo(RequestContext context)" method.
     */
    private int invokeGetHostInfo(Frame frame, HttpContextHandle hCtx, int[] aiResult)
        {
        HttpExchange exchange = hCtx.f_exchange;
        String       sHost    = exchange.getRequestHeaders().getFirst("Host");
        String       sName    = extractHostName(sHost);
        int          nPort    = extractHostPort(sHost, exchange);

        return sName == null
            ? frame.assignValue(aiResult[0], xBoolean.FALSE)
            : frame.assignValues(aiResult, xBoolean.TRUE,
                    xString.makeHandle(sName), xUInt16.INSTANCE.makeJavaLong(nPort));
        }

    /**
     * Implementation of "String getProtocolString(RequestContext context)" method.
     */
    private int invokeGetProtocol(Frame frame, HttpContextHandle hCtx, int iResult)
        {
        String sProtocol = hCtx.f_exchange.getProtocol();

        return frame.assignValue(iResult, xString.makeHandle(sProtocol));
        }

    /**
     * Implementation of "String[] getHeaderNames(RequestContext context)" method.
     */
    private int invokeGetHeaderNames(Frame frame, HttpContextHandle hCtx, int iResult)
        {
        Headers headers = hCtx.f_exchange.getRequestHeaders();

        return frame.assignValue(iResult,
                xString.makeArrayHandle(headers.keySet().toArray(Utils.NO_NAMES)));
        }

    /**
     * Implementation of "conditional String[] getHeaderValuesForName(RequestContext context,
     * String name)" method.
     */
    private int invokeGetHeaderValues(Frame frame, HttpContextHandle hCtx,
                                      StringHandle hName, int[] aiResult)
        {
        Headers headers = hCtx.f_exchange.getRequestHeaders();
        String  sName   = hName.getStringValue();

        List<String> listValues = headers.get(sName);
        if (listValues == null)
            {
            return frame.assignValue(aiResult[0], xBoolean.FALSE);
            }

        return frame.assignValues(aiResult, xBoolean.TRUE,
                xString.makeArrayHandle(listValues.toArray(Utils.NO_NAMES)));
        }

    /**
     * Implementation of "conditional Byte[] getBodyBytes(RequestContext context)" method.
     */
    private int invokeGetBody(Frame frame, HttpContextHandle hCtx, int[] aiResult)
        {
        try
            {
            byte[] ab = hCtx.f_exchange.getRequestBody().readAllBytes();

            return ab.length == 0
                ? frame.assignValue(aiResult[0], xBoolean.FALSE)
                : frame.assignValues(aiResult, xBoolean.TRUE,
                    xByteArray.makeByteArrayHandle(ab, Mutability.Constant));
            }
        catch (IOException e)
            {
            return frame.assignValue(aiResult[0], xBoolean.FALSE);
            }
        }

    /**
     * Implementation of
     *  "Boolean containsNestedBodies(RequestContext context)" method.
     */
    private int invokeContainsBodies(Frame frame, HttpContextHandle hCtx, int iResult)
        {
        // TODO implement
        return frame.assignValue(iResult, xBoolean.FALSE);
        }

    /**
     * Implementation of "closeImpl()" method.
     */
    private int invokeClose(HttpServerHandle hServer)
        {
        HttpServer httpServer  = hServer.getHttpServer();
        HttpServer httpsServer = hServer.getHttpsServer();
        if (httpServer != null)
            {
            if (httpServer.getExecutor() == null)
                {
                // we need to compensate for a bug in com.sun.net.httpserver.HttpServer that doesn't
                // properly close the server socket that hasn't been established
                httpServer.start();
                httpServer.stop(0);
                httpsServer.start();
                httpsServer.stop(0);
                }
            else
                {
                httpServer.removeContext("/");
                httpServer.stop(0);
                httpsServer.removeContext("/");
                httpsServer.stop(0);
                ((ExecutorService) httpServer.getExecutor()).shutdown();
                hServer.f_context.f_container.unregisterNativeCallback();
                }
            hServer.getRouter().mapRoutes.clear();
            hServer.clear();
            }

        return Op.R_NEXT;
        }

    /**
     * Implementation of "respond(
     *   RequestContext ctx, Int status, String[] headerNames, String[] headerValues, Byte[] body)"
     * method.
     */
    private int invokeRespond(Frame frame, ObjectHandle[] ahArg)
        {
        HttpExchange      exchange      = ((HttpContextHandle) ahArg[0]).f_exchange;
        long              nStatus       = ((JavaLong) ahArg[1]).getValue();
        StringArrayHandle hHeaderNames  = (StringArrayHandle) ((ArrayHandle) ahArg[2]).m_hDelegate;
        StringArrayHandle hHeaderValues = (StringArrayHandle) ((ArrayHandle) ahArg[3]).m_hDelegate;
        ArrayHandle       hBody         = (ArrayHandle) ahArg[4];
        byte[]            abBody        = xByteArray.getBytes(hBody);
        int               cbBody        = abBody.length;

        try
            {
            Headers headers = exchange.getResponseHeaders();
            for (long i = 0, c = hHeaderNames.m_cSize; i < c; i++)
                {
                headers.add(hHeaderNames.get(i), hHeaderValues.get(i));
                }

            exchange.sendResponseHeaders((int) nStatus, cbBody > 0 ? cbBody : -1);
            }
        catch (IOException e)
            {
            return frame.raiseException(xException.obscureIoException(frame, e.getMessage()));
            }

        if (cbBody > 0)
            {
            try (OutputStream out = exchange.getResponseBody())
                {
                out.write(abBody);
                }
            catch (Throwable e)
                {
                return frame.raiseException(xException.makeObscure(frame, e.getMessage()));
                }
            }
        return Op.R_NEXT;
        }


    // ----- helper methods ------------------------------------------------------------------------

    protected static String extractHostName(String sHost)
        {
        if (sHost != null)
            {
            int ofPort = sHost.lastIndexOf(':');
            if (ofPort >= 0)
                {
                sHost = sHost.substring(0, ofPort);
                }
            }
        return sHost;
        }

    protected static int extractHostPort(String sHost, HttpExchange exchange)
        {
        if (sHost == null)
            {
            return exchange.getRemoteAddress().getPort();
            }

        int ofPort = sHost.lastIndexOf(':');
        return ofPort >= 0
            ? Integer.valueOf(sHost.substring(ofPort + 1))
            : exchange instanceof HttpsExchange ? 443 : 80;
        }


    // ----- helper classes ------------------------------------------------------------------------

    /**
     * The {@link HttpHandler} that handles all request from the Java {@link HttpServer} and calls
     * the natural HttpServer.Handler "handle()" method.
     */
    protected static class RequestHandler
            implements HttpHandler
        {
        public RequestHandler(ServiceContext context, FunctionHandle hFunction, HttpServerHandle hServer)
            {
            f_context   = context;
            f_hFunction = hFunction;
            f_hServer   = hServer;
            }

        @Override
        public void handle(HttpExchange exchange)
            {
            try (var ignore = ConstantPool.withPool(f_context.f_pool))
                {
                // call the Handler handle method
                ObjectHandle[] hArgs = createArguments(exchange);
                f_context.postRequest(null, f_hFunction, hArgs, 0).handle((response, err) ->
                    {
                    // process the response (or error) from calling the Handler handle method
                    // TODO: this should be sent to the natural "unhandledException" handler
                    if (err != null)
                        {
                        sendError(exchange, err);
                        }
                    return null;
                    });
                }
            catch (Throwable t)
                {
                sendError(exchange, t);
                }
            }

        private ObjectHandle[] createArguments(HttpExchange exchange)
            {
            ObjectHandle      hBinding  = f_hServer.getBinding();
            HttpContextHandle hContext  = new HttpContextHandle(exchange);
            StringHandle      hURI      = xString.makeHandle(exchange.getRequestURI().toASCIIString());
            StringHandle      hMethod   = xString.makeHandle(exchange.getRequestMethod());
            BooleanHandle     hTls      = xBoolean.makeHandle(exchange instanceof HttpsExchange);
            return new ObjectHandle[]{hBinding, hContext, hURI, hMethod, hTls};
            }

        private void sendError(HttpExchange exchange, Throwable t)
            {
            t.printStackTrace();
            try
                {
                exchange.sendResponseHeaders(500, -1);
                }
            catch (IOException e)
                {
                e.printStackTrace();
                }
            }

        private final ServiceContext   f_context;
        private final FunctionHandle   f_hFunction;
        private final HttpServerHandle f_hServer;
        }

    /**
     * X509ExtendedKeyManager based on a single key/certificate.
     */
    protected static class SimpleKeyManager
            extends X509ExtendedKeyManager
        {
        public SimpleKeyManager(HttpServerHandle hServer)
            {
            f_hServer = hServer;
            }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers)
            {
            throw new UnsupportedOperationException();
            }

        @Override
        public String chooseEngineClientAlias(String[] asKeyType, Principal[] issuers, SSLEngine engine)
            {
            throw new UnsupportedOperationException();
            }

        @Override
        public String chooseClientAlias(String[] asKeyType, Principal[] issuers, Socket socket)
            {
            throw new UnsupportedOperationException();
            }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers)
            {
            throw new UnsupportedOperationException();
            }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine)
            {
            SSLSession session = engine.getHandshakeSession();
            if (session instanceof ExtendedSSLSession sessionEx)
                {
                List<SNIServerName> listNames = sessionEx.getRequestedServerNames();

                String sHost = listNames.isEmpty()
                        ? null
                        : ((SNIHostName) listNames.get(0)).getAsciiName();

                // the main reason the server name may be missing is for an HTTPS request that
                // goes directly against the IP address (e.g. "https://129.168.1.30:8081/nginx")
                RouteInfo route = sHost == null
                        ? f_hServer.getRouter().getDirectRoute()
                        : f_hServer.getRouter().mapRoutes.get(sHost);
                if (route == null)
                    {
                    // TODO: REMOVE
                    System.err.println("*** Handshake with unknown host: " + sHost);
                    }
                else
                    {
                    f_tloKeyStore.set(route.hKeyStore);
                    return route.sTlsKey;
                    }
                }
            else
                {
                // TODO: REMOVE
                System.err.println("*** Handshake from unknown session");
                }
            return null;
            }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket)
            {
            throw new UnsupportedOperationException();
            }

        @Override
        public X509Certificate[] getCertificateChain(String sAlias)
            {
            try
                {
                Certificate[] aCerts = f_tloKeyStore.get().f_keyStore.getCertificateChain(sAlias);
                if (aCerts instanceof X509Certificate[] aX509Certs)
                    {
                    return aX509Certs;
                    }
                int               cCerts     = aCerts.length;
                X509Certificate[] aX509Certs = new X509Certificate[cCerts];

                // this call also asserts that all certificates are X509Certificate instances
                System.arraycopy(aCerts, 0, aX509Certs, 0, cCerts);
                return aX509Certs;
                }
            catch (KeyStoreException e)
                {
                return new X509Certificate[0];
                }
            }

        @Override
        public PrivateKey getPrivateKey(String sAlias)
            {
            try
                {
                KeyStoreHandle hKeyStore = f_tloKeyStore.get();
                return (PrivateKey) hKeyStore.getKey(sAlias);
                }
            catch (GeneralSecurityException e)
                {
                return null;
                }
            }

        // ----- data fields -----------------------------------------------------------------------

        /**
         * The HttpServer handle.
         */
        private final HttpServerHandle f_hServer;

        /**
         * The key store handle used by the current thread.
         */
        private final ThreadLocal<KeyStoreHandle> f_tloKeyStore = new ThreadLocal<>();
        }


    // ---- Router ---------------------------------------------------------------------------------

    protected static class Router
            implements HttpHandler
        {
        public final Map<String, RouteInfo> mapRoutes = new ConcurrentHashMap<>();

        private ObjectHandle m_hBinding;
        private RouteInfo    m_routeDirect;

        @Override
        public void handle(HttpExchange exchange)
                throws IOException
            {
            String    sHost = exchange.getRequestHeaders().getFirst("Host");
            String    sName = extractHostName(sHost);
            int       nPort = extractHostPort(sHost, exchange);
            boolean   fTls  = exchange instanceof HttpsExchange;
            RouteInfo route = mapRoutes.get(sName);
            if (route == null || nPort != (fTls ? route.nHttpsPort : route.nHttpPort))
                {
                System.err.println("*** Request for unregistered route: "
                    + (fTls ? "https://" : "http://") + sHost + exchange.getRequestURI());
                exchange.sendResponseHeaders(421, -1); // HttpStatus.MisdirectedRequest
                }
            else
                {
                route.handler.handle(exchange);
                }
            }

        protected ObjectHandle getBinding()
            {
            return m_hBinding;
            }

        protected void setBinding(ObjectHandle binding)
            {
            m_hBinding = binding;
            }

        protected RouteInfo getDirectRoute()
            {
            return m_routeDirect;
            }

        protected void setDirectRoute(RouteInfo route)
            {
            m_routeDirect = route;
            }
        }

    protected record RouteInfo(RequestHandler handler, int nHttpPort, int nHttpsPort,
                               KeyStoreHandle hKeyStore, String sTlsKey) {}


    // ----- ObjectHandles -------------------------------------------------------------------------

    /**
     * A {@link ServiceHandle} for the HttpServer service.
     */
    protected static class HttpServerHandle
            extends ServiceHandle
        {
        /**
         * The underlying native state needs to be kept in an array, so cloning the handle would
         * not splinter the state.
         */
        private final Object[] f_aoNative = new Object[3];

        protected HttpServerHandle(TypeComposition clazz, ServiceContext context)
            {
            super(clazz, context);

            f_aoNative[0] = new Router();
            }

        /**
         * @return the request router
         */
        protected Router getRouter()
            {
            return (Router) f_aoNative[0];
            }

        protected void setRouter(Router router)
            {
            Router routerPrev = getRouter();
            if (router == routerPrev)
                {
                return;
                }

            ObjectHandle binding = getBinding();
            if (binding != null)
                {
                // clean up the reference while we still can
                setBinding(null);
                }

            if (router != null)
                {
                router.setBinding(binding);
                }

            f_aoNative[0] = router;
            }

        /**
         * @return underlying {@link HttpServer}.
         */
        protected HttpServer getHttpServer()
            {
            return (HttpServer) f_aoNative[1];
            }

        protected void setHttpServer(HttpServer httpServer)
            {
            f_aoNative[1] = httpServer;
            }

        /**
         * @return the underlying {@link HttpsServer}
         */
        protected HttpsServer getHttpsServer()
            {
            return (HttpsServer) f_aoNative[2];
            }

        protected void setHttpsServer(HttpsServer httpsServer)
            {
            f_aoNative[2] = httpsServer;
            }

        protected ObjectHandle getBinding()
            {
            Router router = getRouter();
            return router == null
                    ? null
                    : router.getBinding();
            }

        protected void setBinding(ObjectHandle binding)
            {
            Router router = getRouter();
            if (router == null)
                {
                assert binding == null;
                }
            else
                {
                router.setBinding(binding);
                }
            }

        protected void clear()
            {
            setRouter(null);
            setHttpServer(null);
            setHttpsServer(null);
            }

        @Override
        public String toString()
            {
            return "HttpServer" +
                    (getHttpServer() == null
                        ? ""
                        : "@" + getHttpServer().getAddress().getHostString());
            }
        }

    /**
     * Native handle holding the HttpExchange reference.
     */
    protected static class HttpContextHandle
                extends ObjectHandle
        {
        public HttpContextHandle(HttpExchange exchange)
            {
            super(xObject.INSTANCE.getCanonicalClass());

            f_exchange = exchange;
            m_fMutable = false;
            }

        /**
         * The wrapped {@link HttpExchange}.
         */
        public final HttpExchange f_exchange;
        }


    // ----- data fields and constants -------------------------------------------------------------

    /**
     * Cached canonical type.
     */
    private TypeConstant m_typeCanonical;
    }