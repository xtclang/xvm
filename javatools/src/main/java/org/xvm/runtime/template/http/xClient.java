package org.xvm.runtime.template.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;

import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.time.Duration;

import java.util.List;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.GenericArrayDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTStringDelegate.StringArrayHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xByteArray;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;


/**
 * Native implementation of the http.WebServer service that wraps a
 * native Java {@link HttpServer}.
 */
public class xClient
        extends xConst
    {
    public static xClient INSTANCE;

    public xClient(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("construct", new String[]{"numbers.Int64", "http.Client.Redirect", "numbers.Int64"}, null);
        markNativeMethod("send", null, null);
        markNativeProperty("connectionTimeout");
        markNativeProperty("redirect");
        markNativeProperty("priority");

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        long            nTimeout        = ((JavaLong) ahVar[0]).getValue();
        EnumHandle      hRedirect       = (EnumHandle) ahVar[1];
        long            nPriority       = ((JavaLong) ahVar[2]).getValue();

        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.valueOf(hRedirect.getName().toUpperCase()));

        if (nTimeout > 0)
            {
            builder = builder.connectTimeout(Duration.ofMillis(nTimeout));
            }

        if (nPriority > 0)
            {
            builder = builder.priority((int) nPriority);
            }

        HttpClient   client  = builder.build();
        ClientHandle hClient = new ClientHandle(INSTANCE.getCanonicalClass(), client, nPriority, hRedirect);

        return frame.assignValue(iReturn, hClient);
        }

    public int invokeNativeNN(Frame frame, MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        if ("send".equals(method.getName()))
            {
            try
                {
                String              sURI    = ((StringHandle) ahArg[0]).getStringValue();
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(sURI));


                HttpRequest.BodyPublisher bodyPublisher;
                if (ahArg[4].getComposition() == null)
                    {
                    // No body
                    bodyPublisher = HttpRequest.BodyPublishers.noBody();
                    }
                else
                    {
                    bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(xByteArray.getBytes((ArrayHandle) ahArg[4]));
                    }

                if (ahArg[1].getComposition() == null)
                    {
                    builder = builder.method("GET", bodyPublisher);
                    // default method GET
                    }
                else
                    {
                    builder = builder.method(((StringHandle) ahArg[1]).getStringValue(), bodyPublisher);
                    }

                if (ahArg[2].getComposition() != null)
                    {
                    if (ahArg[3].getComposition() == null)
                        {
                        // No header values
                        return frame.raiseException(xException.ioException(frame, "Missing header values"));
                        }

                    StringArrayHandle    hHeaderNames  = (StringArrayHandle) ((ArrayHandle) ahArg[2]).m_hDelegate;
                    GenericArrayDelegate hHeaderValues = (GenericArrayDelegate) ((ArrayHandle) ahArg[3]).m_hDelegate;

                    if (hHeaderNames.m_cSize != hHeaderValues.m_cSize)
                        {
                        return frame.raiseException(xException.ioException(frame, "Header names and values arrays are different sizes"));
                        }

                    for (long n = 0; n < hHeaderNames.m_cSize; n++)
                        {
                        String            sName   = hHeaderNames.get(n);
                        StringArrayHandle hValues = (StringArrayHandle) ((ArrayHandle) hHeaderValues.get(n)).m_hDelegate;
                        for (int i = 0; i < hValues.m_cSize; i++)
                            {
                            builder.header(sName, hValues.get(i));
                            }
                        }
                    }

                ClientHandle              hClient      = (ClientHandle) hTarget;
                HttpResponse<byte[]>      response     = hClient.f_client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                JavaLong                  hStatus      = xInt64.makeHandle(response.statusCode());
                byte[]                    ab           = response.body();
                ArrayHandle               hBody        = xArray.makeByteArrayHandle(ab, xArray.Mutability.Constant);
                Map<String, List<String>> headers      = response.headers().map();
                int                       cHeader      = headers.size();
                StringHandle[]            headerNames  = new StringHandle[cHeader];
                ArrayHandle[]             headerValues = new ArrayHandle[cHeader];
                int                       nIndex       = 0;

                for (Map.Entry<String, List<String>> entry : headers.entrySet())
                    {
                    headerNames[nIndex] = xString.makeHandle(entry.getKey());
                    xString.StringHandle[] hValues = entry.getValue()
                        .stream()
                        .map(xString::makeHandle)
                        .toArray(xString.StringHandle[]::new);
                    headerValues[nIndex] = xArray.makeStringArrayHandle(hValues);
                    nIndex++;
                    }

                ConstantPool    pool          = frame.poolContext();
                TypeConstant    typeArray     = pool.ensureArrayType(pool.ensureArrayType(pool.typeString()));
                TypeComposition type          = frame.f_context.f_templates.resolveClass(typeArray);
                ArrayHandle     hHeaderNames  = xArray.makeStringArrayHandle(headerNames);
                ArrayHandle     hHeaderValues = xArray.makeArrayHandle(type, headerValues.length, headerValues, xArray.Mutability.Constant);

                return frame.assignValues(aiReturn, hStatus, hHeaderNames, hHeaderValues, hBody);
                }
            catch (Throwable t)
                {
                return frame.raiseException(xException.ioException(frame, t.getMessage()));
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ClientHandle hServer = (ClientHandle) hTarget;
        switch (sPropName)
            {
            case "connectionTimeout":
                return frame.assignValue(iReturn, xInt64.makeHandle(hServer.getConnectionTimeout()));
            case "redirect":
                return frame.assignValue(iReturn, hServer.getRedirectPolicy());
            case "priority":
                return frame.assignValue(iReturn, xInt64.makeHandle(hServer.getPriority()));
            }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    // ----- WebServerHandle ------------------------------------------------

    /**
     * A {@link ObjectHandle} for an HTTP client service.
     */
    protected static class ClientHandle
                extends ObjectHandle
        {
        public ClientHandle(TypeComposition clazz, HttpClient client,
                            long nPriority, EnumHandle hRedirect)
            {
            super(clazz);
            f_client    = client;
            f_hRedirect = hRedirect;
            f_nPriority = nPriority;
            }

        protected long getConnectionTimeout()
            {
            return f_client.connectTimeout().map(Duration::toMillis).orElse(0L);
            }

        protected long getPriority()
            {
            return f_nPriority;
            }

        protected EnumHandle getRedirectPolicy()
            {
            return f_hRedirect;
            }

        /**
         * The wrapped {@link HttpClient}.
         */
        private final HttpClient f_client;

        private final EnumHandle f_hRedirect;

        private final long f_nPriority;
        }

    // ----- RequestHandler -------------------------------------------------

    /**
     * The {@link HttpHandler} that handles all request from the {@link HttpServer}
     * and calls the natural Handler instance's handle method.
     */
    protected static class RequestHandler
            implements HttpHandler
        {
        public RequestHandler(ServiceContext context, FunctionHandle hFunction)
            {
            f_context    = context;
            f_hFunction = hFunction;
            }

        @Override
        public void handle(HttpExchange exchange) throws IOException
            {
            ConstantPool poolOld = ConstantPool.getCurrentPool();
            try
                {
                ConstantPool.setCurrentPool(f_context.f_pool);

                // create the request handle
                ObjectHandle[] ahRequest = createRequestParameters(exchange);

                // call the Handler handle method
                f_context.postRequest(null, f_hFunction, ahRequest, 1)
                    .handle((response, err) ->
                    {
                    // process the response (or error) from calling the Handler handle method.
                    if (err == null)
                        {
                        try
                            {
                            TupleHandle          hResponse     = (TupleHandle) response;
                            long                 nStatus       = ((JavaLong) hResponse.m_ahValue[0]).getValue();
                            StringArrayHandle    hHeaderNames  = (StringArrayHandle) ((ArrayHandle) hResponse.m_ahValue[1]).m_hDelegate;
                            GenericArrayDelegate hHeaderValues = (GenericArrayDelegate) ((ArrayHandle) hResponse.m_ahValue[2]).m_hDelegate;
                            ArrayHandle          hBody         = (ArrayHandle) hResponse.m_ahValue[3];
                            byte[]               abBody        = xByteArray.getBytes(hBody);
                            Headers              headers       = exchange.getResponseHeaders();

                            for (long n = 0; n < hHeaderNames.m_cSize; n++)
                                {
                                String            sName   = hHeaderNames.get(n);
                                StringArrayHandle hValues = (StringArrayHandle) ((ArrayHandle) hHeaderValues.get(n)).m_hDelegate;
                                hValues.stream().forEach(sValue -> headers.add(sName, sValue));
                                }

                            exchange.sendResponseHeaders((int) nStatus, abBody.length);
                            try (OutputStream out = exchange.getResponseBody())
                                {
                                out.write(abBody);
                                }
                            }
                        catch (Throwable t)
                            {
                            sendError(exchange, t);
                            }
                        }
                    else
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
            finally
                {
                ConstantPool.setCurrentPool(poolOld);
                }
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

        private ObjectHandle[] createRequestParameters(HttpExchange exchange) throws IOException
            {
            Headers        headers      = exchange.getRequestHeaders();
            StringHandle[] headerNames  = new StringHandle[headers.size()];
            ArrayHandle[]  headerValues = new ArrayHandle[headers.size()];
            int            n            = 0;

            for (Map.Entry<String, List<String>> entry : headers.entrySet())
                {
                headerNames[n] = xString.makeHandle(entry.getKey());
                StringHandle[] hValues = entry.getValue()
                    .stream()
                    .map(xString::makeHandle)
                    .toArray(StringHandle[]::new);
                headerValues[n] = xArray.makeStringArrayHandle(hValues);
                n++;
                }

            ConstantPool pool = f_context.f_pool;
            TypeComposition type = f_context.f_templates.resolveClass(pool.ensureArrayType(pool.ensureArrayType(pool.typeString())));

            ArrayHandle  hHeaderNames  = xArray.makeStringArrayHandle(headerNames);
            ArrayHandle  hHeaderValues = xArray.makeArrayHandle(type, headerValues.length, headerValues, xArray.Mutability.Constant);
            StringHandle hURI          = xString.makeHandle(exchange.getRequestURI().toASCIIString());
            StringHandle hMethod       = xString.makeHandle(exchange.getRequestMethod());
            byte[]       abBody        = exchange.getRequestBody().readAllBytes();
            ArrayHandle  hBody         = xArray.makeByteArrayHandle(abBody, xArray.Mutability.Constant);

            return new ObjectHandle[]{hURI, hMethod, hHeaderNames, hHeaderValues, hBody};
            }

        private final ServiceContext f_context;

        private final FunctionHandle f_hFunction;
        }
    }
