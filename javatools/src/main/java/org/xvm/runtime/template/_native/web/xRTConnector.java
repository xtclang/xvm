package org.xvm.runtime.template._native.web;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import java.security.cert.X509Certificate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
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
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
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

import org.xvm.util.ListMap;
import org.xvm.util.TransientThreadLocal;


/**
 * Native implementation of the RTConnector.x service.
 */
public class xRTConnector
        extends xService
    {
    public static xRTConnector INSTANCE;

    public xRTConnector(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            s_sAgent = "Mozilla/5.0 (compatible; Ecstasy/"
                       + structure.getFileStructure().getModule().getVersionString()
                       + ')' ;
            }
        }

    private static SSLSocketFactory createSocketFactory()
            throws GeneralSecurityException
        {
        TrustManager[] aTrustMgr = new TrustManager[]
            {
            new X509TrustManager()
                {
                @Override
                public X509Certificate[] getAcceptedIssuers()
                    {
                    return null;
                    }
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                    {
                    }
                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                    {
                    }
                }
            };

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, aTrustMgr, new SecureRandom());
        return context.getSocketFactory();
        }

    private static synchronized void ensureGlobalHandler()
        {
        if (s_handlerGlobal == null)
            {
            // set up the global handler (horrible design in Java, but nothing we can do)
            CookieHandler.setDefault(s_handlerGlobal = new GlobalCookieHandler());
            }
        }

    @Override
    public void initNative()
        {
        markNativeMethod("getDefaultHeaders", null, null);
        markNativeMethod("sendRequest", null, null);

        invalidateTypeInfo();
        }


    // ----- native implementations ----------------------------------------------------------------

    @Override
    public TypeConstant getCanonicalType()
        {
        TypeConstant type = m_typeCanonical;
        if (type == null)
            {
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
    public ObjectHandle ensureConnector(Frame frame, ObjectHandle hOpts)
        {
        ServiceContext  context    = f_container.createServiceContext("Connector");
        ConnectorHandle hConnector = new ConnectorHandle(getCanonicalClass(f_container), context);

        try
            {
            CookieHandler    handler  = createCookieHandler();
            SSLSocketFactory factory  = createSocketFactory();
            HostnameVerifier verifier = (sHostName, session) -> true;

            hConnector.configure(handler, factory, verifier);

            context.setService(hConnector);
            return hConnector;
            }
        catch (GeneralSecurityException e)
            {
            f_container.terminate(context);
            return new DeferredCallHandle(xException.makeHandle(frame, e.getMessage()));
            }
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "getDefaultHeaders":
                return invokeGetDefaultHeaders(frame, aiReturn);

            case "sendRequest":
                return invokeSendRequest(frame, (ConnectorHandle) hTarget, (StringHandle) ahArg[0],
                    (StringHandle) ahArg[1], (ArrayHandle) ahArg[2], (ArrayHandle) ahArg[3],
                    (ArrayHandle) ahArg[4], aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    /**
     * Implementation of
     *  "(String[] defaultHeaderNames, String[] defaultHeaderValues) getDefaultHeaders()".
     */
    private int invokeGetDefaultHeaders(Frame frame, int[] aiReturn)
        {
        // REVIEW: should we allow them to specify default headers in the injection definition?

        ArrayHandle hNames  = xString.makeArrayHandle(new String[] {"User-Agent"});
        ArrayHandle hValues = xString.makeArrayHandle(new String[] {s_sAgent});

        return frame.assignValues(aiReturn, hNames, hValues);
        }

    /**
     * Implementation of
     *  "(Int statusCode, String[] responseHeaderNames, String[] responseHeaderValues, Byte[] responseBytes)
     *      sendRequest(String uri, String[] headerNames, String[] headerValues, Byte[] bytes)".
     */
    private int invokeSendRequest(Frame frame, ConnectorHandle hConn, StringHandle hMethod,
                                  StringHandle hUrl, ArrayHandle hHeaderNames, ArrayHandle hHeaderValues,
                                  ArrayHandle hBytes, int[] aiReturn)
        {
        StringArrayHandle haNames  = (StringArrayHandle) hHeaderNames.m_hDelegate;
        StringArrayHandle haValues = (StringArrayHandle) hHeaderValues.m_hDelegate;
        ByteArrayHandle   haBytes  = (ByteArrayHandle)   hBytes.m_hDelegate;

        int                 cHeaders   = (int) haNames.m_cSize;
        Map<String, String> mapHeaders = new ListMap<>(cHeaders);
        for (int i = 0; i < cHeaders; i++)
            {
            mapHeaders.put(haNames.get(i), haValues.get(i));
            }

        try
            {
            URL               url  = new URI(hUrl.getStringValue()).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod(hMethod.getStringValue());
            conn.setInstanceFollowRedirects(false);

            if (conn instanceof HttpsURLConnection connTls)
                {
                connTls.setHostnameVerifier(hConn.m_verifier);
                connTls.setSSLSocketFactory(hConn.m_sslFactory);
                }

            for (Map.Entry<String, String> entry : mapHeaders.entrySet())
                {
                conn.addRequestProperty(entry.getKey(), entry.getValue());
                }

            String sMethod = hMethod.getStringValue();
            byte[] abData;
            if ("PUT".equals(sMethod) || "POST".equals(sMethod))
                {
                abData = ((ByteBasedDelegate) haBytes.getTemplate()).
                                            getBytes(haBytes, 0, haBytes.m_cSize, false);
                }
            else
                {
                abData = null;
                }

            long ldtTimeout = frame.f_fiber.getTimeoutStamp();
            if (ldtTimeout > 0)
                {
                long cTimeoutMillis = Math.max(1, ldtTimeout - System.currentTimeMillis());
                if (cTimeoutMillis > Integer.MAX_VALUE)
                    {
                    cTimeoutMillis = Integer.MAX_VALUE;
                    }
                conn.setConnectTimeout((int) cTimeoutMillis);
                conn.setReadTimeout((int) cTimeoutMillis);
                }

            Callable<Integer> task = () ->
                {
                try (var ignore = s_handlerGlobal.withHandler(hConn.m_cookieHandler))
                    {
                    if (abData != null)
                        {
                        conn.setDoOutput(true);
                        conn.setDoInput(true);

                        OutputStream os = conn.getOutputStream();
                        os.write(abData);
                        os.flush();
                        }
                    return conn.getResponseCode();
                    }
                };

            CompletableFuture<Integer> cfSend = frame.f_context.f_container.scheduleIO(task);

            Frame.Continuation continuation = frameCaller ->
                {
                try
                    {
                    return processResponse(frameCaller, conn, cfSend.get(), aiReturn);
                    }
                catch (Throwable e)
                    {
                    return frameCaller.raiseException(
                        xException.ioException(frameCaller, e.getMessage()));
                    }
                };

            return frame.waitForIO(cfSend, continuation);
            }
        catch (Exception e)
            {
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
            }
        }

    private int processResponse(Frame frame, HttpURLConnection conn, int nResponseCode, int[] aiReturn)
        {
        int cMaxSize = 8*1024*1024; // TODO: how to configure?
        try
            {
            Map<String, List<String>> mapResponseHeaders = conn.getHeaderFields();

            int          cResponseHeaders   = mapResponseHeaders.size();
            List<String> listResponseNames  = new ArrayList<>(cResponseHeaders);
            List<String> listResponseValues = new ArrayList<>(cResponseHeaders);
            int          nContentLength     = 0;

            Iterator<Map.Entry<String, List<String>>> it = mapResponseHeaders.entrySet().iterator();
            for (int i = 0; i < cResponseHeaders; i++)
                {
                Map.Entry<String, List<String>> entry = it.next();

                String sName = entry.getKey();
                if (sName == null)
                    {
                    // this appears to be the HttpStatus entry
                    continue;
                    }

                List<String> listValue = entry.getValue();
                for (int j = 0, c = listValue.size(); j < c; j++)
                    {
                    String sValue = listValue.get(j);
                    assert sValue != null;

                    listResponseNames.add(sName);
                    listResponseValues.add(sValue);

                    if ("Content-Length".equalsIgnoreCase(sName))
                        {
                        nContentLength = Integer.parseInt(sValue);
                        }
                    }
                }

            String[] asResponseNames  = listResponseNames.toArray(Utils.NO_NAMES);
            String[] asResponseValues = listResponseValues.toArray(Utils.NO_NAMES);

            byte[] abResponse = null;
            if (nContentLength > 0)
                {
                if (nContentLength > cMaxSize)
                    {
                    nResponseCode  = 206; // "Partial Content"
                    nContentLength = cMaxSize;
                    }
                InputStream in = nResponseCode >= 400
                        ? conn.getErrorStream()
                        : conn.getInputStream();
                byte[] ab = new byte[nContentLength];

                int ofStart = 0;
                int cRemain = nContentLength;
                while (true)
                    {
                    int cbRead = in.read(ab, ofStart, cRemain);
                    if (cbRead == -1 || cbRead == cRemain)
                        {
                        break;
                        }
                    ofStart += cbRead;
                    cRemain -= cbRead;
                    }
                abResponse = ab;
                }

            ObjectHandle hResponseBytes = abResponse == null
                    ? xArray.ensureEmptyByteArray()
                    : xArray.makeByteArrayHandle(abResponse, Mutability.Constant);

            return frame.assignValues(aiReturn,
                    xInt64.makeHandle(nResponseCode),
                    xString.makeArrayHandle(asResponseNames),
                    xString.makeArrayHandle(asResponseValues),
                    hResponseBytes
                    );
            }
        catch (Exception e)
            {
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
            }
        }


    // ----- ObjectHandles -------------------------------------------------------------------------

    private CookieHandler createCookieHandler()
        {
        ensureGlobalHandler();
        return new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        }

    /**
     * Global CookieHandler.
     */
    protected static class GlobalCookieHandler
            extends CookieHandler
        {
        private final TransientThreadLocal<CookieHandler> f_tloHandler = new TransientThreadLocal<>();

        public AutoCloseable withHandler(CookieHandler handler)
            {
            return f_tloHandler.push(handler);
            }

        @Override
        public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders)
                throws IOException
            {
            return f_tloHandler.get().get(uri, requestHeaders);
            }

        @Override
        public void put(URI uri, Map<String, List<String>> responseHeaders)
                throws IOException
            {
            f_tloHandler.get().put(uri, responseHeaders);
            }
        }

    /**
     * A {@link ServiceHandle} for the RTConnector service.
     */
    protected static class ConnectorHandle
            extends ServiceHandle
        {
        /**
         * The {@link CookieHandler} used by this Connector.
         */
        protected CookieHandler m_cookieHandler;
        /**
         * The {@link SSLSocketFactory} used for HTTPS.
         */
        protected SSLSocketFactory m_sslFactory;
        /**
         * The {@link HostnameVerifier}.
         */
        protected HostnameVerifier m_verifier;

        protected ConnectorHandle(TypeComposition clazz, ServiceContext context)
            {
            super(clazz, context);
            }

        protected void configure(CookieHandler cookieHandler, SSLSocketFactory sslFactory,
                                 HostnameVerifier hostVerifier)
            {
            m_cookieHandler = cookieHandler;
            m_sslFactory    = sslFactory;
            m_verifier      = hostVerifier;
            }

        @Override
        public String toString()
            {
            return "Connector";
            }
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * Global handler.
     */
    private static GlobalCookieHandler s_handlerGlobal;
    /**
     * Cached agent string.
     */
    private static String s_sAgent;
    /**
     * Cached canonical type.
     */
    private TypeConstant m_typeCanonical;
    }