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
import java.security.KeyStoreException;
import java.security.Principal;
import java.security.PrivateKey;

import java.security.cert.X509Certificate;

import java.util.List;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;

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
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.numbers.xUInt16;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.collections.arrays.xRTStringDelegate.StringArrayHandle;

import org.xvm.runtime.template._native.crypto.xRTKeyStore;
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
        markNativeMethod("configureImpl", null, VOID);
        markNativeMethod("start"       , null, VOID);
        markNativeMethod("send"        , null, VOID);
        markNativeMethod("close"       , null, VOID);

        markNativeMethod("getClientAddressBytes",  null, null);
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
            case "start":
                {
                HttpServerHandle hService = (HttpServerHandle) hTarget;
                return frame.f_context == hService.f_context
                        ? invokeStart(frame, hService, (ServiceHandle) hArg)
                        : xRTFunction.makeAsyncNativeHandle(method).
                                call1(frame, hService, new ObjectHandle[]{hArg}, iReturn);
                }

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
            case "configureImpl":
                return invokeConfigure(frame, (HttpServerHandle) hTarget,  ahArg);

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
     * Implementation of "void configureImpl(String bindAddr, UInt16 httpPort, UInt16 httpsPort,
     *                          KeyStore keystore, String? tlsKey)" method.
     */
    private int invokeConfigure(Frame frame, HttpServerHandle hServer, ObjectHandle[] ahArg)
        {
        if (hServer.getHttpServer() != null)
            {
            return frame.raiseException(xException.illegalState(frame, "Server is already configured"));
            }

        String         sBindAddr   = ((StringHandle)   ahArg[0]).getStringValue();
        int            nHttpPort   = (int) ((JavaLong) ahArg[1]).getValue();
        int            nHttpsPort  = (int) ((JavaLong) ahArg[2]).getValue();
        KeyStoreHandle hKeystore   = (KeyStoreHandle)  ahArg[3];
        String         sTlsKey     = ahArg[4] instanceof StringHandle hS ? hS.getStringValue() : null;

        try
            {
            HttpServer  httpServer  = createHttpServer (new InetSocketAddress(sBindAddr, nHttpPort));
            HttpsServer httpsServer = createHttpsServer(new InetSocketAddress(sBindAddr, nHttpsPort),
                                                            hKeystore, sTlsKey);
            hServer.configure(httpServer, httpsServer);
            return Op.R_NEXT;
            }
        catch (IOException | GeneralSecurityException e)
            {
            frame.f_context.f_container.terminate(hServer.f_context);
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
            }
        }

    private HttpServer createHttpServer(InetSocketAddress addr)
            throws IOException
        {
        return HttpServer.create(addr, 0);
        }

    private HttpsServer createHttpsServer(InetSocketAddress addr, KeyStoreHandle hKeystore,
                                          String sTlsKey)
            throws IOException, GeneralSecurityException
        {
        KeyManager[] aKeyManagers;
        if (sTlsKey == null)
            {
            if (xRTKeyStore.INSTANCE.findTlsKey(hKeystore) == null)
                {
                throw new KeyStoreException("Tls key is missing at the keystore");
                }
            aKeyManagers = new KeyManager[] {hKeystore.f_keyManager};
            }
        else
            {
            if (!hKeystore.f_keyStore.isKeyEntry(sTlsKey))
                {
                throw new IllegalArgumentException("Invalid alias: " + sTlsKey);
                }

            aKeyManagers = new KeyManager[] {new SimpleKeyManager(hKeystore.f_keyManager, sTlsKey, sTlsKey)};
            }

        TrustManager[] aTrustManagers = new TrustManager[] {hKeystore.f_trustManager};

        SSLContext ctxSSL = SSLContext.getInstance("TLS");
        ctxSSL.init(aKeyManagers, aTrustManagers, null);

        HttpsServer httpsServer = HttpsServer.create(addr, 0);
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
        return httpsServer;
        }

    /**
     * Implementation of "start(Handler handler)" method.
     */
    private int invokeStart(Frame frame, HttpServerHandle hServer, ServiceHandle hHandler)
        {
        HttpServer  httpServer  = hServer.getHttpServer();
        HttpsServer httpsServer = hServer.getHttpsServer();

        if (httpServer == null)
            {
            return frame.raiseException(xException.illegalState(frame, "Server is not configured"));
            }

        if (hServer.getRequestHandler() == null)
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
            }
        else
            {
            httpServer .removeContext("/");
            httpsServer.removeContext("/");
            }

        ClassStructure  clzHandler = hHandler.getTemplate().getStructure();
        MethodStructure method     = clzHandler.findMethodDeep("handle", m -> m.getParamCount() == 4);
        assert method != null;
        FunctionHandle  hFunction  = xRTFunction.makeInternalHandle(frame, method).bindTarget(frame, hHandler);

        RequestHandler handler = new RequestHandler(hHandler.f_context, hFunction);
        httpServer .createContext("/", handler);
        httpsServer.createContext("/", handler);

        hServer.setRequestHandler(handler);
        return Op.R_NEXT;
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
        InetSocketAddress addr = hCtx.f_exchange.getRemoteAddress();

        return frame.assignValue(iResult, xUInt16.INSTANCE.makeJavaLong(addr.getPort()));
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
        if (hServer != null)
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
            hServer.setRequestHandler(null);
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
        public SimpleKeyManager(X509KeyManager keyManager, String sClient, String sServer)
            {
            f_keyManager   = keyManager;
            f_sClientAlias = sClient;
            f_sServerAlias = sServer;
            }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers)
            {
            return new String[] {f_sClientAlias};
            }

        @Override
        public String chooseEngineClientAlias(String[] asKeyType, Principal[] issuers, SSLEngine engine)
            {
            return f_sClientAlias;
            }

        @Override
        public String chooseClientAlias(String[] asKeyType, Principal[] issuers, Socket socket)
            {
            return f_sClientAlias;
            }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers)
            {
            return new String[] {f_sServerAlias};
            }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine)
            {
            return f_sServerAlias;
            }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket)
            {
            return f_sServerAlias;
            }

        @Override
        public X509Certificate[] getCertificateChain(String sAlias)
            {
            return f_keyManager.getCertificateChain(sAlias);
            }

        @Override
        public PrivateKey getPrivateKey(String sAlias)
            {
            return f_keyManager.getPrivateKey(sAlias);
            }

        // ----- data fields -----------------------------------------------------------------------

        /**
         * The underlying manager.
         */
        private final X509KeyManager f_keyManager;

        /**
         * The alias to use for client calls.
         */
        private final String f_sClientAlias;

        /**
         * The alias to use for server calls.
         */
        private final String f_sServerAlias;
        }


    // ----- ObjectHandles -------------------------------------------------------------------------

    /**
     * A {@link ServiceHandle} for the HttpServer service.
     */
    protected static class HttpServerHandle
            extends ServiceHandle
        {
        protected HttpServerHandle(TypeComposition clazz, ServiceContext context)
            {
            super(clazz, context);
            }

        /**
         * The underlying native state needs to be kept in an array, so cloning the handle would
         * not splinter the state.
         */
        private final Object[] f_aoNative = new Object[3];

        protected void configure(HttpServer httpServer, HttpsServer httpsServer)
            {
            f_aoNative[0] = httpServer;
            f_aoNative[1] = httpsServer;
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
         * @return underlying {@link HttpServer}.
         */
        protected HttpServer getHttpServer()
            {
            return (HttpServer) f_aoNative[0];
            }

        /**
         * @return the underlying {@link HttpsServer}
         */
        protected HttpsServer getHttpsServer()
            {
            return (HttpsServer) f_aoNative[1];
            }

        /**
         * @return the http(s) request handler
         */
        protected RequestHandler getRequestHandler()
            {
            return (RequestHandler) f_aoNative[2];
            }

        /**
         * Store the http(s) request handler.
         */
        protected void setRequestHandler(RequestHandler handler)
            {
            f_aoNative[2] = handler;
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