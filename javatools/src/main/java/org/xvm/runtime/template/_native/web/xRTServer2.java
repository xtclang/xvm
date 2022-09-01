package org.xvm.runtime.template._native.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;

import java.net.InetSocketAddress;

import java.util.List;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.GenericArrayDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTStringDelegate.StringArrayHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * Native implementation of the http.Server service that wraps a native Java {@link HttpServer}.
 */
public class xRTServer2
        extends xService
    {
    public static xRTServer2 INSTANCE;

    public xRTServer2(Container container, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("attachHandler", null, VOID);
        markNativeMethod("send"         , null, VOID);
        markNativeMethod("close"        , null, VOID);

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

        getCanonicalType().invalidateTypeInfo();
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
        if (!(hOpts instanceof StringHandle hAddress))
            {
            return new DeferredCallHandle(xException.illegalArgument(frame,
                    "Injection must specify a server address"));
            }

        String sAddress = hAddress.getStringValue();
        int    ofPort   = sAddress.indexOf(':');
        String sHost    = sAddress;
        int    nPort    = 8080;
        try
            {
            if (ofPort >= 0)
                {
                sHost  = sAddress.substring(0, ofPort);
                nPort  = Integer.valueOf(sAddress.substring(ofPort+1));
                }
            }
        catch (Exception e)
            {
            return new DeferredCallHandle(xException.illegalArgument(frame, e.getMessage()));
            }

        try
            {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(sHost, nPort), 0);

            Container       container = f_container;
            ServiceContext  context   = container.createServiceContext("HttpServer@" + sAddress);
            ServiceHandle   hService  = new HttpServerHandle(
                                            getCanonicalClass(container), context, httpServer);
            context.setService(hService);
            return hService;
            }
        catch (IOException e)
            {
            return new DeferredCallHandle(xException.ioException(frame, e.getMessage()));
            }
        }

    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "attachHandler":
                return invokeAttachHandler(frame, (HttpServerHandle) hTarget, (ServiceHandle) hArg);

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
                return invokeClose((HttpServerHandle) hTarget);
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "send":
                return invokeSend(frame, ahArg);
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
     * Implementation of "attachHandler()" method.
     */
    private int invokeAttachHandler(Frame frame, HttpServerHandle hServer, ServiceHandle hHandler)
        {
        HttpServer httpServer = hServer.f_httpServer;

        if (hServer.m_httpHandler == null)
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

            // TODO: replace the pool with dynamic
            httpServer.setExecutor(Executors.newFixedThreadPool(4, factory));
            httpServer.start();

            // prevent the container from being terminated
            hServer.f_context.f_container.getServiceContext().registerNotification();
            }
        else
            {
            httpServer.removeContext("/");
            }

        ClassStructure  clzHandler = hHandler.getTemplate().getStructure();
        MethodStructure method     = clzHandler.findMethodDeep("handle", m -> m.getParamCount() == 3);
        assert method != null;
        FunctionHandle  hFunction  = xRTFunction.makeHandle(frame, method).bindTarget(frame, hHandler);

        RequestHandler handler = new RequestHandler(hHandler.f_context, hFunction);
        httpServer.createContext("/", handler);

        hServer.m_httpHandler = handler;
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

        return frame.assignValue(iResult, xInt64.makeHandle(addr.getPort()));
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

        return frame.assignValue(iResult, xInt64.makeHandle(addr.getPort()));
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

        int            cValues = headers.size();
        StringHandle[] ahValue = new StringHandle[cValues];
        int            ix      = 0;
        for (String sName : headers.keySet())
            {
            ahValue[ix++] = xString.makeHandle(sName);
            }

        return frame.assignValue(iResult, xArray.makeStringArrayHandle(ahValue));
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

        int            cValues = listValues.size();
        StringHandle[] ahValue = new StringHandle[cValues];
        for (int i = 0; i < cValues; i++)
            {
            ahValue[i] = xString.makeHandle(listValues.get(i));
            }

        return frame.assignValues(aiResult, xBoolean.TRUE, xArray.makeStringArrayHandle(ahValue));
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
     * Implementation of "conditional RequestContext[] containsNestedBodies(RequestContext context)" method.
     */
    private int invokeContainsBodies(Frame frame, HttpContextHandle hCtx, int[] aiResult)
        {
        return frame.raiseException(xException.unsupportedOperation(frame, "Not implemented"));
        }

    /**
     * Implementation of "close()" method.
     */
    private int invokeClose(HttpServerHandle hServer)
        {
        HttpServer httpServer = hServer.f_httpServer;
        if (httpServer.getExecutor() == null)
            {
            // we need to compensate for a bug in com.sun.net.httpserver.HttpServer that doesn't
            // properly close the server socket that hasn't been established
            httpServer.start();
            httpServer.stop(0);
            }
        else
            {
            httpServer.removeContext("/");
            httpServer.stop(0);
            ((ExecutorService) httpServer.getExecutor()).shutdown();
            hServer.f_context.f_container.getServiceContext().unregisterNotification();
            }
        hServer.m_httpHandler = null;

        return Op.R_NEXT;
        }

    /**
     * Implementation of "send()" method.
     */
    private int invokeSend(Frame frame, ObjectHandle[] ahArg)
        {
        HttpExchange         exchange      = ((HttpContextHandle) ahArg[0]).f_exchange;
        long                 nStatus       = ((JavaLong) ahArg[1]).getValue();
        StringArrayHandle    hHeaderNames  = (StringArrayHandle)    ((ArrayHandle) ahArg[2]).m_hDelegate;
        GenericArrayDelegate hHeaderValues = (GenericArrayDelegate) ((ArrayHandle) ahArg[3]).m_hDelegate;
        ArrayHandle          hBody         = (ArrayHandle) ahArg[4];
        byte[]               abBody        = xByteArray.getBytes(hBody);
        int                  cbBody        = abBody.length;

        try
            {
            Headers headers = exchange.getResponseHeaders();

            for (long n = 0; n < hHeaderNames.m_cSize; n++)
                {
                String            sName   = hHeaderNames.get(n);
                StringArrayHandle hValues = (StringArrayHandle) ((ArrayHandle) hHeaderValues.get(n)).m_hDelegate;
                hValues.stream().forEach(hValue -> headers.add(sName, hValue));
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

            return new ObjectHandle[]{hContext, hURI, hMethod};
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


    // ----- ObjectHandles -------------------------------------------------------------------------

    /**
     * A {@link ServiceHandle} for the HttpServer service.
     */
    protected static class HttpServerHandle
            extends ServiceHandle
        {
        public HttpServerHandle(TypeComposition clazz, ServiceContext context, HttpServer server)
            {
            super(clazz, context);

            f_httpServer = server;
            }

        protected int getPort()
            {
            return f_httpServer.getAddress().getPort();
            }

        /**
         * The underlying {@link HttpServer}.
         */
        protected final HttpServer f_httpServer;

        /**
         * The handle to the http request handler.
         */
        protected RequestHandler m_httpHandler;
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