package org.xvm.runtime.template.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;

import java.util.concurrent.atomic.AtomicLong;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xService;

import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.GenericArrayDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTStringDelegate.StringArrayHandle;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xByteArray;

/**
 * Native implementation of the http.Response service, which wraps a
 * native {@link HttpExchange}.
 */
public class xResponse
        extends xService
    {
    public static xResponse INSTANCE;

    public xResponse(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("send",null, null);

        getCanonicalType().invalidateTypeInfo();

        ClassTemplate templateChannel = f_templates.getTemplate("http.Response");

        s_clzRequest = ensureClass(templateChannel.getCanonicalType());
        }

    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "send":
                send(hTarget, ahArg);
                return Op.R_NEXT;
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    public ServiceHandle createHandle(ServiceContext context, HttpExchange exchange)
        {
        String         sId            = "Response-" + s_cResponse.getAndIncrement();
        ServiceContext contextRequest = context.f_container.createServiceContext(sId);
        ResponseHandle hResponse      = new ResponseHandle(s_clzRequest, contextRequest, exchange);
        contextRequest.setService(hResponse);
        return hResponse;
        }

    // ----- helper methods -------------------------------------------------

    private void send(ObjectHandle hTarget, ObjectHandle[] ahArg)
        {
        if (hTarget instanceof ResponseHandle)
            {
            sendResponse((ResponseHandle) hTarget, ahArg);
            }
        }

    private void sendResponse(ResponseHandle hResponse, ObjectHandle[] ahArg)
        {
        long                 nStatus       = ((JavaLong) ahArg[0]).getValue();
        StringArrayHandle    hHeaderNames  = (StringArrayHandle) ((ArrayHandle) ahArg[1]).m_hDelegate;
        GenericArrayDelegate hHeaderValues = (GenericArrayDelegate) ((ArrayHandle) ahArg[2]).m_hDelegate;
        ArrayHandle          hBody         = (ArrayHandle) ahArg[3];
        byte[]               abBody        = xByteArray.getBytes(hBody);
        int                  cbBody        = abBody.length;

        HttpExchange exchange = hResponse.f_exchange;
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
        catch (IOException t)
            {
            sendError(exchange, t);
            return;
            }

        if (cbBody > 0)
            {
            try (OutputStream out = exchange.getResponseBody())
                {
                out.write(abBody);
                }
            catch (Throwable ignore) {}
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

    // ----- RequestHandle --------------------------------------------------

    /**
     * A {@link ServiceHandle} for an HTTP request service.
     */
    protected static class ResponseHandle
                extends ServiceHandle
        {
        public ResponseHandle(TypeComposition clazz, ServiceContext context, HttpExchange exchange)
            {
            super(clazz, context);
            f_exchange = exchange;
            }

        /**
         * The wrapped {@link HttpExchange}.
         */
        private final HttpExchange f_exchange;
        }

    // ----- data members ---------------------------------------------------

    private static TypeComposition s_clzRequest;

    private static final AtomicLong s_cResponse = new AtomicLong();
    }
