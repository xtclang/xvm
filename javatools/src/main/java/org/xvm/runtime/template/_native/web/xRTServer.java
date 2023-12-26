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
import org.xvm.runtime.template.xNullable;
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
        markNativeMethod("configureImpl"  , null, VOID);
        markNativeMethod("addRouteImpl"   , null, VOID);
        markNativeMethod("removeRouteImpl", null, VOID);
        markNativeMethod("start"          , null, VOID);
        markNativeMethod("send"           , null, VOID);
        markNativeMethod("close"          , null, VOID);

        markNativeMethod("getClientAddressBytes",  null, null);
        markNativeMethod("getClientHostName",      null, null);
        markNativeMethod("getClientPort",          null, null);
        markNativeMethod("getServerAddressBytes",  null, null);
        markNativeMethod("getServerPort",          null, null);
        markNativeMethod("getMethodString",        null, null);
        markNativeMethod("getUriString",           null, null);
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
        ServiceContext context  = f_container.createServiceContext("HttpServer");
        ServiceHandle  hService = new HttpServerHandle(getCanonicalClass(f_container), context);

        context.setService(hService);
        return hService;
        }

    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "getClientHostName":
                return invokeGetClientHostName(frame, (HttpContextHandle) hArg, iReturn);

            case "getClientAddressBytes":
                return invokeGetClientAddress(frame, (HttpContextHandle) hArg, iReturn);

            case "getClientPort":
                return invokeGetClientPort(frame, (HttpContextHandle) hArg, iReturn);

            case "getServerAddressBytes":
                return invokeGetServerAddress(frame, (HttpContextHandle) hArg, iReturn);

            case "getServerPort":
                return invokeGetServerPort(frame, (HttpContextHandle) hArg, iReturn);

            case "getMethodString":
                return invokeGetMethod(frame, (HttpContextHandle) hArg, iReturn);

            case "getUriString":
                return invokeGetUri(frame, (HttpContextHandle) hArg, iReturn);

            case "getProtocolString":
                return invokeGetProtocol(frame, (HttpContextHandle) hArg, iReturn);

            case "getHeaderNames":
                return invokeGetHeaderNames(frame, (HttpContextHandle) hArg, iReturn);

            case "removeRouteImpl":
                return invokeRemoveRoute(frame, (HttpServerHandle) hTarget, (StringHandle) hArg);

            case "close":
                {
                HttpServerHandle hService = (HttpServerHandle) hTarget;
                return frame.f_context == hService.f_context
                        ? invokeClose(hService)
                        : xRTFunction.makeAsyncNativeHandle(method).
                                call1(frame, hService, new ObjectHandle[]{hArg}, iReturn);
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "start":
                {
                HttpServerHandle hService = (HttpServerHandle) hTarget;
                return frame.f_context == hService.f_context
                        ? invokeStart(frame, hService)
                        : xRTFunction.makeAsyncNativeHandle(method).
                                call1(frame, hService, Utils.OBJECTS_NONE, iReturn);
                }

            case "configureImpl":
                return invokeConfigure(frame, (HttpServerHandle) hTarget, ahArg);

            case "addRouteImpl":
                return invokeAddRoute(frame, (HttpServerHandle) hTarget, ahArg);

            case "send":
                {
                HttpServerHandle hService = (HttpServerHandle) hTarget;
                return frame.f_context == hService.f_context
                        ? invokeSend(frame, ahArg)
                        : xRTFunction.makeAsyncNativeHandle(method).
                                call1(frame, hService, ahArg, iReturn);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "getHeaderValuesForName":
                return invokeGetHeaderValues(frame, (HttpContextHandle) ahArg[0],
                        (StringHandle) ahArg[1], aiReturn);

            case "getBodyBytes":
                return invokeGetBody(frame, (HttpContextHandle) ahArg[0], aiReturn);

            case "containsNestedBodies":
                return invokeContainsBodies(frame, (HttpContextHandle) ahArg[0], aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- native implementations ----------------------------------------------------------------

    /**
     * Implementation of "void configureImpl(String bindAddr, UInt16 httpPort, UInt16 httpsPort)" method.
     */
    private int invokeConfigure(Frame frame, HttpServerHandle hServer, ObjectHandle[] ahArg)
        {
        if (hServer.getHttpServer() != null)
            {
            return frame.raiseException(xException.illegalState(frame, "Server is already configured"));
            }

        String sBindAddr  = ((StringHandle)   ahArg[0]).getStringValue();
        int    nHttpPort  = (int) ((JavaLong) ahArg[1]).getValue();
        int    nHttpsPort = (int) ((JavaLong) ahArg[2]).getValue();

        try
            {
            configureHttpServer (hServer, new InetSocketAddress(sBindAddr, nHttpPort));
            configureHttpsServer(hServer, new InetSocketAddress(sBindAddr, nHttpsPort));
            return Op.R_NEXT;
            }
        catch (Exception e)
            {
            frame.f_context.f_container.terminate(hServer.f_context);
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
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

                    params.setNeedClientAuth(true);
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

    /**
     * Implementation of "void addRouteImpl(String hostName, Handler handler, KeyStore keystore,
     *                    String? tlsKey=Null)" method.
     */
    private int invokeAddRoute(Frame frame, HttpServerHandle hServer, ObjectHandle[] ahArg)
        {
        StringHandle   hHostName  = (StringHandle) ahArg[0];
        ServiceHandle  hHandler   = (ServiceHandle) ahArg[1];
        KeyStoreHandle hKeystore  = (KeyStoreHandle)  ahArg[2];
        String         sTlsKey    = ahArg[3] instanceof StringHandle hS ? hS.getStringValue() : null;
        Router         router     = hServer.getRouter();

        if (sTlsKey == null && router.mapRoutes.isEmpty())
            {
            // find a public/private key pair that could be used to encrypt tls communications
            try
                {
                KeyStore keystore = hKeystore.f_keyStore;
                for (Enumeration<String> it = keystore.aliases(); it.hasMoreElements();)
                    {
                    String sName = it.nextElement();
                    if (keystore.isKeyEntry(sName) &&
                            hKeystore.getKey(sName) instanceof PrivateKey &&
                            keystore.getCertificate(sName) != null)
                        {
                        sTlsKey = sName;
                        break;
                        }
                    }
                }
            catch (GeneralSecurityException ignore) {}

            if (sTlsKey == null)
                {
                return frame.raiseException("The Tls key name must be specified");
                }
            }

        ClassStructure  clzHandler = hHandler.getTemplate().getStructure();
        MethodStructure method     = clzHandler.findMethodDeep("handle", m -> m.getParamCount() == 4);
        assert method != null;

        FunctionHandle hFunction = xRTFunction.makeInternalHandle(frame, method).bindTarget(frame, hHandler);
        RequestHandler handler   = new RequestHandler(hHandler.f_context, hFunction);

        router.mapRoutes.put(hHostName.getStringValue(), new RouteInfo(handler, hKeystore, sTlsKey));
        return Op.R_NEXT;
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
     * Implementation of "start()" method.
     */
    private int invokeStart(Frame frame, HttpServerHandle hServer)
        {
        HttpServer  httpServer  = hServer.getHttpServer();
        HttpsServer httpsServer = hServer.getHttpsServer();

        if (httpServer == null)
            {
            return frame.raiseException(xException.illegalState(frame, "Server is not configured"));
            }

        if (httpServer.getExecutor() == null)
            {
            // this is a very first call; set up the thread pool
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
            hServer.f_context.f_container.getServiceContext().registerNotification();

            Router router = hServer.getRouter();
            httpServer .createContext("/", router);
            httpsServer.createContext("/", router);
            }
        return Op.R_NEXT;
        }

    /**
     * Implementation of "String? getClientHostName(RequestContext context)" method.
     */
    private int invokeGetClientHostName(Frame frame, HttpContextHandle hCtx, int iResult)
        {
        String sHost = getClientHostName(hCtx.f_exchange);

        return frame.assignValue(iResult, sHost == null
                ? xNullable.NULL
                : xString.makeHandle(sHost));
        }

    /**
     * Implementation of "Byte[] getClientAddressBytes(RequestContext context)" method.
     */
    private int invokeGetClientAddress(Frame frame, HttpContextHandle hCtx, int iResult)
        {
        InetSocketAddress addr = hCtx.f_exchange.getRemoteAddress();
        byte[]            ab   = addr.getAddress().getAddress();

        return frame.assignValue(iResult, xByteArray.makeByteArrayHandle(ab, Mutability.Constant));
        }

    /**
     * Implementation of "UInt16 getClientPort(RequestContext context)" method.
     */
    private int invokeGetClientPort(Frame frame, HttpContextHandle hCtx, int iResult)
        {
        int nPort = getClientPort(hCtx.f_exchange);

        return frame.assignValue(iResult, xUInt16.INSTANCE.makeJavaLong(nPort));
        }

    /**
     * Implementation of "Byte[] getServerAddressBytes(RequestContext context)" method.
     */
    private int invokeGetServerAddress(Frame frame, HttpContextHandle hCtx, int iResult)
        {
        InetSocketAddress addr = hCtx.f_exchange.getLocalAddress();
        byte[]            ab   = addr.getAddress().getAddress();

        return frame.assignValue(iResult, xByteArray.makeByteArrayHandle(ab, Mutability.Constant));
        }

    /**
     * Implementation of "UInt16 getServerPort(RequestContext context)" method.
     */
    private int invokeGetServerPort(Frame frame, HttpContextHandle hCtx, int iResult)
        {
        InetSocketAddress addr = hCtx.f_exchange.getLocalAddress();

        return frame.assignValue(iResult, xUInt16.INSTANCE.makeJavaLong(addr.getPort()));
        }

    /**
     * Implementation of "String getMethodString(RequestContext context)" method.
     */
    private int invokeGetMethod(Frame frame, HttpContextHandle hCtx, int iResult)
        {
        String sMethod = hCtx.f_exchange.getRequestMethod();

        return frame.assignValue(iResult, xString.makeHandle(sMethod));
        }

    /**
     * Implementation of "String getUriString(RequestContext context)" method.
     */
    private int invokeGetUri(Frame frame, HttpContextHandle hCtx, int iResult)
        {
        String sUri = hCtx.f_exchange.getRequestURI().toASCIIString();

        return frame.assignValue(iResult, xString.makeHandle(sUri));
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
     * Implementation of "conditional String[] getHeaderValuesForName(RequestContext context, String name)" method.
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
     *  "conditional RequestContext[] containsNestedBodies(RequestContext context)" method.
     */
    private int invokeContainsBodies(Frame frame, HttpContextHandle hCtx, int[] aiResult)
        {
        // TODO implement
        return frame.assignValue(aiResult[0], xBoolean.FALSE);
        }

    /**
     * Implementation of "close()" method.
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
                hServer.f_context.f_container.getServiceContext().unregisterNotification();
                }
            hServer.getRouter().mapRoutes.clear();
            }

        return Op.R_NEXT;
        }

    /**
     * Implementation of "send()" method.
     */
    private int invokeSend(Frame frame, ObjectHandle[] ahArg)
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
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
            }

        if (cbBody > 0)
            {
            try (OutputStream out = exchange.getResponseBody())
                {
                out.write(abBody);
                }
            catch (Throwable e)
                {
                return frame.raiseException(xException.ioException(frame, e.getMessage()));
                }
            }
        return Op.R_NEXT;
        }


    // ----- helper methods ------------------------------------------------------------------------

    protected static String getClientHostName(HttpExchange exchange)
        {
        String sHost = exchange.getRequestHeaders().getFirst("Host");
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

    protected static int getClientPort(HttpExchange exchange)
        {
        String sHost = exchange.getRequestHeaders().getFirst("Host");
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
        public RequestHandler(ServiceContext context, FunctionHandle hFunction)
            {
            f_context   = context;
            f_hFunction = hFunction;
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
            HttpContextHandle hContext = new HttpContextHandle(exchange);
            StringHandle      hURI     = xString.makeHandle(exchange.getRequestURI().toASCIIString());
            StringHandle      hMethod  = xString.makeHandle(exchange.getRequestMethod());
            BooleanHandle     hTls     = xBoolean.makeHandle(exchange instanceof HttpsExchange);

            return new ObjectHandle[]{hContext, hURI, hMethod, hTls};
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

        private final ServiceContext f_context;
        private final FunctionHandle f_hFunction;
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
                if (!listNames.isEmpty())
                    {
                    String    sHost = ((SNIHostName) listNames.get(0)).getAsciiName();
                    RouteInfo route = f_hServer.getRouter().mapRoutes.get(sHost);
                    if (route != null)
                        {
                        f_tloKeyStore.set(route.hKeyStore);
                        return route.sTlsKey;
                        }
                    }
                else
                    {
                    // TODO: REMOVE
                    System.err.println("*** Handshake from unspecified host");
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
        @Override
        public void handle(HttpExchange exchange)
                throws IOException
            {
            String    sHost = getClientHostName(exchange);
            RouteInfo route = mapRoutes.get(sHost);
            if (route == null)
                {
                exchange.sendResponseHeaders(444, -1); // HttpStatus.NoResponse
                }
            else
                {
                route.handler.handle(exchange);
                }
            }

        public final Map<String, RouteInfo> mapRoutes = new ConcurrentHashMap<>();
        }

    protected record RouteInfo(RequestHandler handler, KeyStoreHandle hKeyStore, String sTlsKey) {}


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

        protected void setHttpServer(HttpServer httpServer)
            {
            f_aoNative[1] = httpServer;
            }

        protected void setHttpsServer(HttpsServer httpsServer)
            {
            f_aoNative[2] = httpsServer;
            }

        @Override
        public String toString()
            {
            return "HttpServer" +
                    (getHttpServer() == null
                        ? ""
                        : "@" + getHttpServer().getAddress().getHostString());
            }

        /**
         * @return the request router
         */
        protected Router getRouter()
            {
            return (Router) f_aoNative[0];
            }

        /**
         * @return underlying {@link HttpServer}.
         */
        protected HttpServer getHttpServer()
            {
            return (HttpServer) f_aoNative[1];
            }

        /**
         * @return the underlying {@link HttpsServer}
         */
        protected HttpsServer getHttpsServer()
            {
            return (HttpsServer) f_aoNative[2];
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