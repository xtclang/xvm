package org.xvm.runtime.template._native.web;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import java.security.cert.X509Certificate;

import java.time.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.OwnedResource;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.collections.arrays.ByteBasedDelegate;
import org.xvm.runtime.template._native.collections.arrays.ByteBasedDelegate.ByteArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTStringDelegate.StringArrayHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;

/**
 * Native implementation of the RTConnector.x service.
 */
public class xRTConnector
        extends xService {
    public static xRTConnector INSTANCE;

    public xRTConnector(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);

        if (fInstance) {
            INSTANCE = this;
            s_sAgent = "Mozilla/5.0 (compatible; Ecstasy/"
                       + structure.getFileStructure().getModule().getVersionString()
                       + ')' ;
        }
    }

    @Override
    public void initNative() {
        markNativeMethod("getDefaultHeaders", null, null);
        markNativeMethod("sendRequest", null, null);

        invalidateTypeInfo();
    }

    // ----- native implementations ----------------------------------------------------------------

    @Override
    public TypeConstant getCanonicalType() {
        TypeConstant type = m_typeCanonical;
        if (type == null) {
            ConstantPool  pool = pool();
            ClassConstant idClient = pool.ensureClassConstant(
                    pool.ensureModuleConstant("web.xtclang.org"), "Client");
            m_typeCanonical = type = pool.ensureTerminalTypeConstant(
                    pool.ensureClassConstant(idClient, "Connector"));
        }
        return type;
    }

    /**
     * Injection support method.
     */
    public ObjectHandle ensureConnector(Frame frame, ObjectHandle hOpts) {
        ServiceContext context = f_container.createServiceContext("Connector");
        try {
            SSLContext    sslContext    = createSSLContext();

            ConnectorHandle hConnector = new ConnectorHandle(getCanonicalClass(f_container),
                                            context, sslContext);
            context.setService(hConnector);
            return hConnector;
        } catch (GeneralSecurityException e) {
            return xException.makeObscure(frame, e.getMessage());
        }
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        switch (method.getName()) {
        case "getDefaultHeaders":
            return invokeGetDefaultHeaders(frame, aiReturn);

        case "sendRequest": {
            ConnectorHandle hConnector = (ConnectorHandle) hTarget;
            return frame.f_context == hConnector.f_context
                    ? invokeSendRequest(frame, hConnector, (StringHandle) ahArg[0],
                        (StringHandle) ahArg[1], (ArrayHandle) ahArg[2],
                        (ArrayHandle) ahArg[3], (ArrayHandle) ahArg[4], aiReturn)
                    : xRTFunction.makeAsyncNativeHandle(method).
                        callN(frame, hConnector, ahArg, aiReturn);
        }
        }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }

    /**
     * Implementation of
     *  "(String[] defaultHeaderNames, String[] defaultHeaderValues) getDefaultHeaders()".
     */
    private int invokeGetDefaultHeaders(Frame frame, int[] aiReturn) {
        // REVIEW: should we allow them to specify default headers in the injection definition?

        ArrayHandle hNames  = m_hDefaultNames;
        ArrayHandle hValues = m_hDefaultValues;
        if (hNames == null) {
            m_hDefaultNames  = hNames  = xString.makeArrayHandle(new String[] {"User-Agent"});
            m_hDefaultValues = hValues = xString.makeArrayHandle(new String[] {s_sAgent});
        }

        return frame.assignValues(aiReturn, hNames, hValues);
    }

    /**
     * Implementation of
     *  "(Int statusCode, String[] responseHeaderNames, String[] responseHeaderValues, Byte[] responseBytes)
     *      sendRequest(String uri, String[] headerNames, String[] headerValues, Byte[] bytes)".
     */
    private int invokeSendRequest(Frame frame, ConnectorHandle hConn, StringHandle hMethod,
                                  StringHandle hUrl, ArrayHandle hHeaderNames, ArrayHandle hHeaderValues,
                                  ArrayHandle hBytes, int[] aiReturn) {
        StringArrayHandle haNames  = (StringArrayHandle) hHeaderNames.m_hDelegate;
        StringArrayHandle haValues = (StringArrayHandle) hHeaderValues.m_hDelegate;
        ByteArrayHandle   haBytes  = (ByteArrayHandle)   hBytes.m_hDelegate;

        try {
            long ldtTimeout     = frame.f_fiber.getTimeoutStamp();
            long cTimeoutMillis = ldtTimeout > 0
                    ? Math.max(1, ldtTimeout - frame.f_context.f_container.currentTimeMillis())
                    : 0L;

            HttpRequest.Builder builderRequest =
                    HttpRequest.newBuilder(new URI(hUrl.getStringValue()));

            for (int i = 0, c = (int) haNames.m_cSize; i < c; i++) {
                builderRequest.header(haNames.get(i), haValues.get(i));
            }

            if (cTimeoutMillis > 0) {
                builderRequest.timeout(Duration.ofMillis(cTimeoutMillis));
            }

            byte[] abData = haBytes.m_cSize > 0
                    ? ((ByteBasedDelegate) haBytes.getTemplate()).
                            getBytes(haBytes, 0, haBytes.m_cSize, false)
                    : null;

            builderRequest.method(hMethod.getStringValue(),
                    abData == null ? BodyPublishers.noBody() : BodyPublishers.ofByteArray(abData));

            HttpClient  client  = hConn.clients(frame).selectClient(cTimeoutMillis);
            HttpRequest request = builderRequest.build();

            Callable<HttpResponse<byte[]>> task = () ->
                    client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            CompletableFuture<HttpResponse<byte[]>> cfSend = frame.scheduleIO(task);

            Frame.Continuation continuation = frameCaller -> {
                try {
                    return processResponse(frameCaller, cfSend.get(), aiReturn);
                } catch (Throwable e) {
                    return frameCaller.raiseException(
                        xException.ioException(frameCaller, e.getMessage()));
                }
            };

            return frame.waitForIO(cfSend, continuation);
        } catch (Exception e) {
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
        }
    }

    private int processResponse(Frame frame, HttpResponse<byte[]> response, int[] aiReturn) {
        try {
            int                       nResponseCode      = response.statusCode();
            Map<String, List<String>> mapResponseHeaders = response.headers().map();

            int          cResponseHeaders   = mapResponseHeaders.size();
            List<String> listResponseNames  = new ArrayList<>(cResponseHeaders);
            List<String> listResponseValues = new ArrayList<>(cResponseHeaders);

            Iterator<Map.Entry<String, List<String>>> it = mapResponseHeaders.entrySet().iterator();
            for (int i = 0; i < cResponseHeaders; i++) {
                Map.Entry<String, List<String>> entry = it.next();

                String sName = entry.getKey();
                if (sName == null) {
                    // this appears to be the HttpStatus entry
                    continue;
                }

                List<String> listValue = entry.getValue();
                for (String sValue : listValue) {
                    assert sValue != null;

                    listResponseNames.add(sName);
                    listResponseValues.add(sValue);
                }
            }

            String[] asResponseNames  = listResponseNames.toArray(Utils.NO_NAMES);
            String[] asResponseValues = listResponseValues.toArray(Utils.NO_NAMES);

            byte[] abResponse = response.body();

            ObjectHandle hResponseBytes = abResponse == null || abResponse.length == 0
                    ? xArray.ensureEmptyByteArray()
                    : xArray.makeByteArrayHandle(abResponse, Mutability.Constant);

            return frame.assignValues(aiReturn,
                    xInt64.makeHandle(nResponseCode),
                    xString.makeArrayHandle(asResponseNames),
                    xString.makeArrayHandle(asResponseValues),
                    hResponseBytes
                    );
        } catch (Exception e) {
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
        }
    }

    private static CookieHandler createCookieHandler() {
        return new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    }

    private static SSLContext createSSLContext()
            throws GeneralSecurityException {
        // allow self-signed certificates
        TrustManager[] aTrustMgr = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            }
        };

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, aTrustMgr, new SecureRandom());
        return context;
    }

    // ----- ObjectHandles -------------------------------------------------------------------------

    /**
     * A {@link ServiceHandle} for the RTConnector service.
     */
    protected static class ConnectorHandle
            extends ServiceHandle {
        protected ConnectorHandle(TypeComposition clazz, ServiceContext context,
                                  SSLContext sslContext) {
            super(clazz, context);
            this.sslContext = sslContext;
        }

        synchronized HttpClientPool clients(Frame frame) {
            Container owner = frame.getResourceContainer();
            OwnedResource<HttpClientPool> resource = clients.get(owner);
            if (resource == null) {
                resource = frame.acquireResource(
                        () -> new HttpClientPool(createCookieHandler(), sslContext),
                        pool -> OwnedResource.closeOnWorker(pool).whenComplete((_, _) -> {
                            synchronized (this) {
                                clients.remove(owner);
                            }
                        }));
                clients.put(owner, resource);
            }
            return resource.get();
        }

        @Override
        public String toString() {
            return "Connector";
        }

        private final SSLContext sslContext;
        private final Map<Container, OwnedResource<HttpClientPool>> clients = new HashMap<>();
    }

    // ----- data fields ---------------------------------------------------------------------------

    /**
     * Cached agent string.
     */
    private static String s_sAgent;

    /**
     * Cached canonical type.
     */
    private TypeConstant m_typeCanonical;

    /**
     * Cached default headers.
     */
    private ArrayHandle m_hDefaultNames;
    private ArrayHandle m_hDefaultValues;
}
