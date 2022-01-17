package org.xvm.runtime.template.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.io.IOException;

import java.net.InetSocketAddress;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

/**
 * Native implementation of the http.Server service that wraps a
 * native Java {@link HttpServer}.
 */
public class xServer
        extends xService
    {
    public static xServer INSTANCE;

    public xServer(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("construct", new String[]{"numbers.Int64", "http.Server.Handler"}, null);
        markNativeMethod("start", VOID, VOID);
        markNativeMethod("stop", VOID, VOID);
        markNativeMethod("isRunning", VOID, BOOLEAN);
        markNativeProperty("port");
        markNativeProperty("handler");

        getCanonicalType().invalidateTypeInfo();

        ConstantPool    pool                 = pool();
        TypeConstant    typeString           = pool.typeString();
        TypeConstant    typeStringArray      = pool.ensureArrayType(pool.typeString());
        TypeConstant    typeStringArrayArray = pool.ensureArrayType(typeStringArray);
        TypeConstant    typeByteArray        = pool.typeByteArray();
        TypeConstant    typeResponse         = xResponse.INSTANCE.getCanonicalType();

        m_aTypeHandleArgs = new TypeConstant[]{typeString, typeString, typeStringArray,
            typeStringArrayArray, typeByteArray, typeResponse};
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        try
            {
            long            nPort         = ((JavaLong) ahVar[0]).getValue();
            ServiceContext  context       = frame.f_context.f_container.createServiceContext("WebServer-" + nPort);
            ServiceHandle   hHandler      = (ServiceHandle) ahVar[1];
            ClassStructure  structHandler = hHandler.getComposition().getTemplate().getStructure();
            MethodStructure method        = structHandler.findMethod("handle", m_aTypeHandleArgs.length,  m_aTypeHandleArgs);
            FunctionHandle  hFunction     = xRTFunction.makeHandle(method).bindTarget(frame, hHandler);
//            WebServerHandle hServer       = createJavaWebServer(context, (int) nPort, hFunction, hHandler);
            WebServerHandle hServer       = createNettyWebServer(context, (int) nPort, hFunction, hHandler);

            context.setService(hServer);

            return frame.assignValue(iReturn, hServer);
            }
        catch (IOException e)
            {
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
            }
        }

    private WebServerHandle createJavaWebServer(ServiceContext  context, int nPort, FunctionHandle  hFunction, ServiceHandle hHandler) throws IOException
        {
        RequestHandler  handler = new RequestHandler(context, hFunction);
        HttpServer      server = HttpServer.create(new InetSocketAddress((int) nPort), 0);

        server.createContext("/", handler);
        server.setExecutor(Executors.newFixedThreadPool(4));

        return new JavaWebServerHandle(INSTANCE.getCanonicalClass(), context, server, hHandler);
        }

    private WebServerHandle createNettyWebServer(ServiceContext  context, int nPort, FunctionHandle  hFunction, ServiceHandle hHandler) throws IOException
        {
        NettyServer server = new NettyServer(nPort, context, hFunction);
        return new NettyServerHandle(INSTANCE.getCanonicalClass(), context, server, hHandler);
        }

    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        WebServerHandle hServer = (WebServerHandle) hTarget;
        switch (method.getName())
            {
            case "start":
                if (!hServer.isRunning())
                    {
                    hServer.start();
                    }
                return Op.R_NEXT;

            case "stop":
                if (hServer.isRunning())
                    {
                    hServer.stop();
                    }
                return Op.R_NEXT;

            case "isRunning":
                return frame.assignValue(iReturn, xBoolean.makeHandle(hServer.isRunning()));
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        WebServerHandle hServer = (WebServerHandle) hTarget;
        switch (sPropName)
            {
            case "port":
                return frame.assignValue(iReturn, xInt64.makeHandle(hServer.getPort()));
            case "handler":
                return frame.assignValue(iReturn, hServer.getHandler());
            }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    // ----- JavaWebServerHandle --------------------------------------------

    /**
     * A {@link ServiceHandle} for a web server service.
     */
    protected static abstract class WebServerHandle
            extends ServiceHandle
        {
        public WebServerHandle(TypeComposition clazz, ServiceContext context)
            {
            super(clazz, context);
            }

        protected abstract int getPort();

        protected abstract void start();

        protected abstract void stop();

        protected abstract boolean isRunning();

        protected abstract ServiceHandle getHandler();
        }

    // ----- JavaWebServerHandle --------------------------------------------

    /**
     * A {@link ServiceHandle} for a web server service.
     */
    protected static class JavaWebServerHandle
                extends WebServerHandle
        {
        public JavaWebServerHandle(TypeComposition clazz, ServiceContext context, HttpServer server, ServiceHandle hHandler)
            {
            super(clazz, context);
            f_server   = server;
            f_hHandler = hHandler;
            }

        @Override
        protected void start()
            {
            f_context.registerNotification();
            f_server.start();
            f_fRunning = true;
            }

        @Override
        protected void stop()
            {
            f_server.stop(1);
            f_context.unregisterNotification();
            f_fRunning = false;
            }

        @Override
        protected int getPort()
            {
            return f_server.getAddress().getPort();
            }

        @Override
        protected ServiceHandle getHandler()
            {
            return f_hHandler;
            }

        @Override
        protected boolean isRunning()
            {
            return f_fRunning;
            }

        /**
         * The wrapped {@link HttpServer}.
         */
        private final HttpServer f_server;

        /**
         * The handle to the request handler.
         */
        private final ServiceHandle f_hHandler;

        /**
         * Whether the server is running.
         */
        private volatile boolean f_fRunning;
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
            f_context   = context;
            f_hFunction = hFunction;
            }

        @Override
        public void handle(HttpExchange exchange) throws IOException
            {
            ConstantPool poolOld = ConstantPool.getCurrentPool();
            try
                {
                ConstantPool.setCurrentPool(f_context.f_pool);

                // call the Handler handle method
                ObjectHandle[] hArgs = createArguments(exchange);
                f_context.postRequest(null, f_hFunction, hArgs, 0)
                    .handle((response, err) ->
                    {
                    // process the response (or error) from calling the Handler handle method.
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
            finally
                {
                ConstantPool.setCurrentPool(poolOld);
                }
            }

        private ObjectHandle[] createArguments(HttpExchange exchange) throws IOException
            {
            Headers                headers      = exchange.getRequestHeaders();
            xString.StringHandle[] headerNames  = new xString.StringHandle[headers.size()];
            xArray.ArrayHandle[]   headerValues = new xArray.ArrayHandle[headers.size()];
            int                    nHeader      = 0;

            for (Map.Entry<String, List<String>> entry : headers.entrySet())
                {
                headerNames[nHeader] = xString.makeHandle(entry.getKey());
                xString.StringHandle[] hValues = entry.getValue()
                    .stream()
                    .map(xString::makeHandle)
                    .toArray(xString.StringHandle[]::new);
                headerValues[nHeader] = xArray.makeStringArrayHandle(hValues);
                nHeader++;
                }

            ConstantPool    pool = f_context.f_pool;
            TypeComposition type = f_context.f_templates.resolveClass(pool.ensureArrayType(pool.ensureArrayType(pool.typeString())));

            xArray.ArrayHandle   hHeaderNames  = xArray.makeStringArrayHandle(headerNames);
            xArray.ArrayHandle   hHeaderValues = xArray.makeArrayHandle(type, headerValues.length, headerValues, xArray.Mutability.Constant);
            xString.StringHandle hURI          = xString.makeHandle(exchange.getRequestURI().toASCIIString());
            xString.StringHandle hMethod       = xString.makeHandle(exchange.getRequestMethod());
            byte[]               abBody        = exchange.getRequestBody().readAllBytes();
            xArray.ArrayHandle   hBody         = xArray.makeByteArrayHandle(abBody, xArray.Mutability.Constant);
            ServiceHandle        hResponse     = xResponse.INSTANCE.createHandle(f_context, exchange);

            return new ObjectHandle[]{hURI, hMethod, hHeaderNames, hHeaderValues, hBody, hResponse};
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

    // ----- NettyServerHandle ----------------------------------------------

    /**
     * A {@link ServiceHandle} for a web server service.
     */
    protected static class NettyServerHandle
                extends WebServerHandle
        {
        public NettyServerHandle(TypeComposition clazz, ServiceContext context, NettyServer server, ServiceHandle hHandler)
            {
            super(clazz, context);
            f_server   = server;
            f_hHandler = hHandler;
            }

        @Override
        protected void start()
            {
            f_context.registerNotification();
            f_server.start();
            f_fRunning = true;
            }

        @Override
        protected void stop()
            {
            f_server.stop();
            f_context.unregisterNotification();
            f_fRunning = false;
            }

        @Override
        protected int getPort()
            {
            return f_server.getPort();
            }

        @Override
        public ServiceHandle getHandler()
            {
            return f_hHandler;
            }

        @Override
        protected boolean isRunning()
            {
            return f_fRunning;
            }

        // ----- data members -----------------------------------------------

        /**
         * The wrapped {@link NettyServer}.
         */
        private final NettyServer f_server;

        /**
         * The handle to the request handler.
         */
        private final ServiceHandle f_hHandler;

        /**
         * Whether the server is running.
         */
        private volatile boolean f_fRunning;
        }

    // ----- NettyServer ----------------------------------------------------

    private static class NettyServer
        {
        public NettyServer(int nPort, ServiceContext context, FunctionHandle hFunction)
            {
            f_nPort     = nPort;
            f_context   = context;
            f_hFunction = hFunction;
            }

        public int getPort()
            {
            return f_nPort;
            }

        public void start()
            {
            // Create the multi-threaded event loops for the server
            EventLoopGroup bossGroup = new NioEventLoopGroup();
            EventLoopGroup workerGroup = new NioEventLoopGroup();

            try
                {
                // A helper class that simplifies server configuration
                ServerBootstrap httpBootstrap = new ServerBootstrap();

                // Configure the server
                httpBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ServerInitializer(f_context, f_hFunction)) // <-- Our handler created here
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

                // Bind and start to accept incoming connections.
                ChannelFuture httpChannel = httpBootstrap.bind(f_nPort).sync();

                ChannelFuture closeFuture = httpChannel.channel().closeFuture();
                m_channelFuture = closeFuture.addListener(future ->
                                                    {
                                                    bossGroup.shutdownGracefully();
                                                    workerGroup.shutdownGracefully();
                                                    });
                }
            catch (InterruptedException e)
                {
                e.printStackTrace();
                }
            }

        public boolean isRunning()
            {
            return m_channelFuture != null && !m_channelFuture.isDone();
            }

        public void stop()
            {
            try
                {
                m_channelFuture.channel().close().sync();
                }
            catch (InterruptedException e)
                {
                e.printStackTrace();
                }
            }

        private final int f_nPort;

        private final ServiceContext f_context;

        private final FunctionHandle f_hFunction;

        private ChannelFuture m_channelFuture;
        }

    private static class ServerInitializer extends ChannelInitializer<Channel>
        {
        public ServerInitializer(ServiceContext context, FunctionHandle hFunction)
            {
            f_context   = context;
            f_hFunction = hFunction;
            }

        @Override
        protected void initChannel(Channel ch)
            {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
            pipeline.addLast(new ServerHandler(f_context, f_hFunction));
            }

        private final ServiceContext f_context;

        private final FunctionHandle f_hFunction;
        }

    private static class ServerHandler extends SimpleChannelInboundHandler<FullHttpRequest>
        {
        public ServerHandler(ServiceContext context, FunctionHandle hFunction)
            {
            f_context   = context;
            f_hFunction = hFunction;
            }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg)
            {
            ConstantPool poolOld = ConstantPool.getCurrentPool();
            try
                {
System.err.println("Request Start: " + msg.uri());
long start = System.currentTimeMillis();
                ConstantPool.setCurrentPool(f_context.f_pool);

                // call the Handler handle method
                ObjectHandle[] hArgs = createArguments(ctx, msg);
                f_context.postRequest(null, f_hFunction, hArgs, 0)
                    .handle((response, err) ->
                    {
long end = System.currentTimeMillis();
long dif = end - start;
System.err.println("Request End: " + msg.uri() + " in " + dif + " ms");
                    // process the response (or error) from calling the Handler handle method.
                    if (err != null)
                        {
                        sendError(ctx, err);
                        }
                    return null;
                    });
                }
            catch (Throwable t)
                {
                sendError(ctx, t);
                }
            finally
                {
                ConstantPool.setCurrentPool(poolOld);
                }
            }

        private ObjectHandle[] createArguments(ChannelHandlerContext ctx, FullHttpRequest request) throws IOException
            {
            HttpHeaders            headers      = request.headers();
            xString.StringHandle[] headerNames  = new xString.StringHandle[headers.size()];
            xArray.ArrayHandle[]   headerValues = new xArray.ArrayHandle[headers.size()];
            int                    nHeader      = 0;

            for (String sName : headers.names())
                {
                headerNames[nHeader] = xString.makeHandle(sName);
                xString.StringHandle[] hValues = headers.getAll(sName)
                    .stream()
                    .map(xString::makeHandle)
                    .toArray(xString.StringHandle[]::new);
                headerValues[nHeader] = xArray.makeStringArrayHandle(hValues);
                nHeader++;
                }

            ConstantPool    pool = f_context.f_pool;
            TypeComposition type = f_context.f_templates.resolveClass(pool.ensureArrayType(pool.ensureArrayType(pool.typeString())));

            xArray.ArrayHandle   hHeaderNames  = xArray.makeStringArrayHandle(headerNames);
            xArray.ArrayHandle   hHeaderValues = xArray.makeArrayHandle(type, headerValues.length, headerValues, xArray.Mutability.Constant);
            xString.StringHandle hURI          = xString.makeHandle(request.uri());
            xString.StringHandle hMethod       = xString.makeHandle(request.method().name());
            ByteBuf              byteBuf       = request.content();
            byte[]               abBody        = new byte[byteBuf.capacity()];

            byteBuf.getBytes(0, abBody);

            xArray.ArrayHandle   hBody         = xArray.makeByteArrayHandle(abBody, xArray.Mutability.Constant);
            ServiceHandle        hResponse     = xResponse.INSTANCE.createHandle(f_context, ctx, request);

            return new ObjectHandle[]{hURI, hMethod, hHeaderNames, hHeaderValues, hBody, hResponse};
            }

        private void sendError(ChannelHandlerContext ctx, Throwable t)
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

        // ----- data members -----------------------------------------------

        private final ServiceContext f_context;

        private final FunctionHandle f_hFunction;
        }

    // ----- data members ---------------------------------------------------

    private TypeConstant[] m_aTypeHandleArgs;
    }
