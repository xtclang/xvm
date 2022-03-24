package org.xvm.runtime.template._native.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;

import java.net.InetSocketAddress;

import java.util.List;
import java.util.Map;

import java.util.concurrent.Executors;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.numbers.xIntLiteral.IntNHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.GenericArrayDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTStringDelegate.StringArrayHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * Native implementation of the http.Server service that wraps a native Java {@link HttpServer}.
 */
public class xRTServer
        extends xService
    {
    public static xRTServer INSTANCE;

    public xRTServer(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

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

        getCanonicalType().invalidateTypeInfo();
        }

    /**
     * Injection support method.
     */
    public ObjectHandle ensureServer(Frame frame, ObjectHandle hOpts)
        {
        long lPort = hOpts instanceof JavaLong   hInt ? hInt.getValue() :
                     hOpts instanceof IntNHandle hLit ? hLit.getValue().getLong()
                                                      : 80;
        try
            {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress((int) lPort), 0);

            ServiceContext context =
                    frame.f_context.f_container.createServiceContext("WebServer-" + lPort);
            ServiceHandle  hService =
                    new HttpServerHandle(getCanonicalClass(), context, httpServer);
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
                HttpServerHandle hServer = (HttpServerHandle) hTarget;
                return invokeAttachHandler(frame, hServer, (ServiceHandle) hArg);
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

    /**
     * Implementation of "attachHandler()" method.
     */
    private int invokeAttachHandler(Frame frame, HttpServerHandle hServer, ServiceHandle hHandler)
        {
        HttpServer httpServer = hServer.f_httpServer;

        if (hServer.m_httpHandler == null)
            {
            // this is a very first call; set up the thread pool
            httpServer.setExecutor(Executors.newFixedThreadPool(4));
            httpServer.start();

            // prevent the container from being terminated
            hServer.f_context.f_container.getServiceContext().registerNotification();
            }
        else
            {
            httpServer.removeContext("/");
            }

        ClassStructure  clzHandler = hHandler.getTemplate().getStructure();
        MethodStructure method     = clzHandler.findMethod("handle", 6);
        FunctionHandle  hFunction  = xRTFunction.makeHandle(method).bindTarget(frame, hHandler);

        RequestHandler handler = new RequestHandler(hServer.f_context, hFunction);
        httpServer.createContext("/", handler);

        hServer.m_httpHandler = handler;
        return Op.R_NEXT;
        }

    /**
     * Implementation of "send()" method.
     */
    private int invokeSend(Frame frame, ObjectHandle[] ahArg)
        {
        HttpExchange         exchange      = ((HttpContextHandle) ahArg[0]).f_exchange;
        long                 nStatus       = ((JavaLong) ahArg[1]).getValue();
        StringArrayHandle    hHeaderNames  = (StringArrayHandle) ((ArrayHandle) ahArg[2]).m_hDelegate;
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
                throws IOException
            {
            Headers        headers      = exchange.getRequestHeaders();
            StringHandle[] headerNames  = new StringHandle[headers.size()];
            ArrayHandle[]  headerValues = new ArrayHandle[headers.size()];
            int            nHeader      = 0;

            for (Map.Entry<String, List<String>> entry : headers.entrySet())
                {
                headerNames[nHeader] = xString.makeHandle(entry.getKey());

                StringHandle[] hValues = entry.getValue()
                    .stream()
                    .map(xString::makeHandle)
                    .toArray(StringHandle[]::new);
                headerValues[nHeader] = xArray.makeStringArrayHandle(hValues);
                nHeader++;
                }

            HttpContextHandle hContext      = new HttpContextHandle(exchange);
            ArrayHandle       hHeaderNames  = xArray.makeStringArrayHandle(headerNames);
            ArrayHandle       hHeaderValues = xArray.makeArrayHandle(ensureArrayOfArrayComposition(),
                                                headerValues.length, headerValues, Mutability.Constant);
            StringHandle      hURI          = xString.makeHandle(exchange.getRequestURI().toASCIIString());
            StringHandle      hMethod       = xString.makeHandle(exchange.getRequestMethod());
            byte[]            abBody        = exchange.getRequestBody().readAllBytes();
            ArrayHandle       hBody         = xArray.makeByteArrayHandle(abBody, Mutability.Constant);

            return new ObjectHandle[]{hContext, hURI, hMethod, hHeaderNames, hHeaderValues, hBody};
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

    private static TypeComposition ensureArrayOfArrayComposition()
        {
        TypeComposition clzAA = ARRAY_OF_STRING_ARRAY_CLZ;
        if (clzAA == null)
            {
            ConstantPool pool = INSTANCE.pool();

            clzAA = ARRAY_OF_STRING_ARRAY_CLZ = INSTANCE.f_templates.resolveClass(
                    pool.ensureArrayType(pool.ensureArrayType(pool.typeString())));
            }
        return clzAA;
        }


    // ----- ObjectHandles -------------------------------------------------------------------------

    /**
     * A {@link ServiceHandle} for the http server service.
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

    private static TypeComposition ARRAY_OF_STRING_ARRAY_CLZ;
    }