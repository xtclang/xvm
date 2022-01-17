package org.xvm.runtime.template.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
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

    public ServiceHandle createHandle(ServiceContext context, ChannelHandlerContext ctx, FullHttpRequest request)
        {
        String             sId            = "Response-" + s_cResponse.getAndIncrement();
        ServiceContext     contextRequest = context.f_container.createServiceContext(sId);
        NettyResponseHandle hResponse     = new NettyResponseHandle(s_clzRequest, contextRequest, ctx, request);
        contextRequest.setService(hResponse);
        return hResponse;
        }

    // ----- helper methods -------------------------------------------------

    private void send(ObjectHandle hTarget, ObjectHandle[] ahArg)
        {
        if (hTarget instanceof ResponseHandle)
            {
            sendJava((ResponseHandle) hTarget, ahArg);
            }
        if (hTarget instanceof NettyResponseHandle)
            {
            sendNetty((NettyResponseHandle) hTarget, ahArg);
            }
        }

    private void sendNetty(NettyResponseHandle hResponse, ObjectHandle[] ahArg)
        {
        ChannelHandlerContext context = hResponse.f_ctx;
        try
            {
            long                 now           = System.currentTimeMillis();
            long                 dif           = now - hResponse.f_timestamp;
System.err.println("Response Sent " + hResponse.f_request.uri() + " in " + dif + " ms");
            long                 nStatus       = ((JavaLong) ahArg[0]).getValue();
            StringArrayHandle    hHeaderNames  = (StringArrayHandle) ((ArrayHandle) ahArg[1]).m_hDelegate;
            GenericArrayDelegate hHeaderValues = (GenericArrayDelegate) ((ArrayHandle) ahArg[2]).m_hDelegate;
            ArrayHandle          hBody         = (ArrayHandle) ahArg[3];
            byte[]               abBody        = xByteArray.getBytes(hBody);
            ByteBuf              content       = Unpooled.wrappedBuffer(abBody);
            FullHttpResponse     response      = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf((int) nStatus), content);
            HttpHeaders          headers       = response.headers();

            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

            for (long n = 0; n < hHeaderNames.m_cSize; n++)
                {
                String            sName   = hHeaderNames.get(n);
                StringArrayHandle hValues = (StringArrayHandle) ((ArrayHandle) hHeaderValues.get(n)).m_hDelegate;
                hValues.stream().forEach(hValue -> headers.add(sName, hValue));
                }

            context.write(response);
            context.flush();
            }
        catch (Throwable t)
            {
            sendNettyError(context, t);
            }
        }

    private void sendJava(ResponseHandle hResponse, ObjectHandle[] ahArg)
        {
        HttpExchange exchange = hResponse.f_exchange;
        try
            {
            long                 nStatus       = ((JavaLong) ahArg[0]).getValue();
            StringArrayHandle    hHeaderNames  = (StringArrayHandle) ((ArrayHandle) ahArg[1]).m_hDelegate;
            GenericArrayDelegate hHeaderValues = (GenericArrayDelegate) ((ArrayHandle) ahArg[2]).m_hDelegate;
            ArrayHandle          hBody         = (ArrayHandle) ahArg[3];
            byte[]               abBody        = xByteArray.getBytes(hBody);
            Headers              headers       = exchange.getResponseHeaders();

            for (long n = 0; n < hHeaderNames.m_cSize; n++)
                {
                String            sName   = hHeaderNames.get(n);
                StringArrayHandle hValues = (StringArrayHandle) ((ArrayHandle) hHeaderValues.get(n)).m_hDelegate;
                hValues.stream().forEach(hValue -> headers.add(sName, hValue));
                }

            exchange.sendResponseHeaders((int) nStatus, abBody.length);
            try (OutputStream out = exchange.getResponseBody())
                {
                out.write(abBody);
                }
            }
        catch (Throwable t)
            {
            sendJavaError(exchange, t);
            }
        }

    private void sendJavaError(HttpExchange exchange, Throwable t)
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

    private void sendNettyError(ChannelHandlerContext ctx, Throwable t)
        {
        t.printStackTrace();
        try
            {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            ctx.write(response);
            ctx.flush();
            }
        catch (Throwable thrown)
            {
            thrown.printStackTrace();
            }
        }

    // ----- RequestHandle --------------------------------------------------

    /**
     * A {@link ServiceHandle} for a http request service.
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

    // ----- NettyRequestHandle ---------------------------------------------

    /**
     * A {@link ServiceHandle} for a http request service.
     */
    protected static class NettyResponseHandle
                extends ServiceHandle
        {
        public NettyResponseHandle(TypeComposition clazz, ServiceContext context, ChannelHandlerContext ctx, FullHttpRequest request)
            {
            super(clazz, context);
            f_ctx     = ctx;
            f_request = request;
            f_timestamp = System.currentTimeMillis();
            }

        /**
         * The wrapped {@link ChannelHandlerContext}.
         */
        private final ChannelHandlerContext f_ctx;

        /**
         * The wrapped {@link FullHttpRequest}.
         */
        private final FullHttpRequest f_request;

        private long f_timestamp;
        }

    private static TypeComposition s_clzRequest;

    private static final AtomicLong s_cResponse = new AtomicLong();
    }
