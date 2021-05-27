package org.xvm.runtime.template._native.proxy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;

import java.net.InetSocketAddress;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xByteArray;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;


/**
 * Native implementation of a simple http server proxy.
 */
public class xRTWebServerProxy
        extends xService
    {
    public xRTWebServerProxy(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        markNativeMethod("start", new String[]{"proxy.WebServerProxy.Handler"}, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "start":
                {
                FunctionHandle hHandler       = (FunctionHandle) hArg;
                RequestHandler requestHandler = new RequestHandler(frame, hTarget, hHandler);

                frame.f_context.registerNotification();

                try
                    {
                    HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
                    server.createContext("/", requestHandler);
                    server.start();
                    }
                catch (IOException e)
                    {
                    throw new RuntimeException(e);
                    }

                return Op.R_NEXT;
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    protected static class RequestHandler
            implements HttpHandler
        {
        public RequestHandler(Frame frame, ObjectHandle hObject, FunctionHandle hHandler)
            {
            f_frame    = frame;
            f_hObject  = hObject;
            f_hHandler = hHandler;
            }

        @Override
        public void handle(HttpExchange exchange) throws IOException
            {
            FunctionHandle hFunction = new NativeFunctionHandle((_frame, _ahArg, _iReturn) ->
                {
                try
                    {
                    JavaLong        nStatus   = (JavaLong) _ahArg[0];
                    byte[]          abBody    = xByteArray.getBytes((ArrayHandle) _ahArg[1]);
                    ArrayHandle     haHeaders = (ArrayHandle) _ahArg[2];
                    ObjectHandle[]  ahHeader  = haHeaders.getTemplate().toArray(f_frame, haHeaders);
                    Headers  responseHeaders  = exchange.getResponseHeaders();

                    for (int i = 0, cHeaders = ahHeader.length; i < cHeaders; i++)
                        {
                        TupleHandle  hTuple   = (TupleHandle) ahHeader[i];
                        StringHandle hName    = (StringHandle) hTuple.m_ahValue[0];
                        ArrayHandle  haValues = (ArrayHandle) hTuple.m_ahValue[1];

                        ObjectHandle[] ahValue = haValues.getTemplate().toArray(f_frame, haValues);
                        for (int j = 0, cValues = ahValue.length; j < cValues; j++)
                            {
                            StringHandle hValue = (StringHandle) ahValue[j];
                            responseHeaders.add(hName.getStringValue(), hValue.getStringValue());
                            }
                        }
                    exchange.sendResponseHeaders((int) nStatus.getValue(), abBody.length);
                    try (OutputStream out = exchange.getResponseBody())
                        {
                        out.write(abBody);
                        }
                    return Op.R_NEXT;
                    }
                catch (ExceptionHandle.WrapperException e)
                    {
                    return f_frame.raiseException(e);
                    }
                catch (IOException e)
                    {
                    e.printStackTrace();
                    return f_frame.raiseException(e.getMessage());
                    }
                });

            ObjectHandle hRequest = xRTHttpRequestProxy.INSTANCE.createHandle(f_frame, exchange, 0);
            f_frame.f_context.callLater(f_hHandler, new ObjectHandle[]{hRequest, hFunction}, true);
            }

        final private Frame          f_frame; // TODO GG: this seems to be not necessary
        final private ObjectHandle   f_hObject;
        final private FunctionHandle f_hHandler;
        }
    }
