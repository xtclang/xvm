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
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xByteArray;
import org.xvm.runtime.template.collections.xTuple;
import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.xService;

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
                xRTFunction.FunctionHandle hHandler   = (xRTFunction.FunctionHandle) hArg;

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
        public RequestHandler(Frame frame, ObjectHandle hObject, xRTFunction.FunctionHandle hHandler)
            {
            f_frame    = frame;
            f_hObject  = hObject;
            f_hHandler = hHandler;
            }

        @Override
        public void handle(HttpExchange exchange) throws IOException
            {
            FunctionHandle hFunction = new xRTFunction.NativeFunctionHandle((_frame, _ahArg, _iReturn) ->
                            {
                            try
                                {
                                ObjectHandle.JavaLong      nStatus  = (ObjectHandle.JavaLong) _ahArg[0];
                                xByteArray.ByteArrayHandle abBody   = (xByteArray.ByteArrayHandle) _ahArg[1];
                                xArray.GenericArrayHandle  ahHeader = (xArray.GenericArrayHandle) _ahArg[2];

                                Headers responseHeaders = exchange.getResponseHeaders();
                                for (int i = 0; i < ahHeader.m_cSize; i++)
                                    {
                                    xTuple.TupleHandle        hTuple  = (xTuple.TupleHandle) ahHeader.m_ahValue[i];
                                    xString.StringHandle      hName   = (xString.StringHandle) hTuple.m_ahValue[0];
                                    xArray.GenericArrayHandle hValues = (xArray.GenericArrayHandle) hTuple.m_ahValue[1];
                                    for (int j = 0; j < hValues.m_cSize; j++)
                                        {
                                        xString.StringHandle hValue = (xString.StringHandle) hValues.m_ahValue[j];
                                        responseHeaders.add(hName.getStringValue(), hValue.getStringValue());
                                        }
                                    }
                                exchange.sendResponseHeaders((int) nStatus.getValue(), abBody.m_cSize);
                                try (OutputStream out = exchange.getResponseBody())
                                    {
                                    if (abBody.m_cSize > 0)
                                        {
                                        out.write(abBody.m_abValue, 0, abBody.m_cSize);
                                        }
                                    }
                                return Op.R_NEXT;
                                }
                            catch (IOException e)
                                {
                                e.printStackTrace();
                                return Op.R_EXCEPTION;
                                }
                            });

            ObjectHandle hRequest = xRTHttpRequestProxy.INSTANCE.createHandle(f_frame, exchange, 0);
            f_frame.f_context.callLater(f_hHandler, new ObjectHandle[]{hRequest, hFunction}, true);
            }

        final private Frame f_frame;
        final private ObjectHandle f_hObject;
        final private xRTFunction.FunctionHandle f_hHandler;
        }
    }
