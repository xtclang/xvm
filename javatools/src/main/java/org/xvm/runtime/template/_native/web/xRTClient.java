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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

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

import org.xvm.runtime.template.numbers.xInt;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.collections.arrays.ByteBasedDelegate;
import org.xvm.runtime.template._native.collections.arrays.ByteBasedDelegate.ByteArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTStringDelegate.StringArrayHandle;

import org.xvm.util.ListMap;
import org.xvm.util.TransientThreadLocal;


/**
 * Native implementation of the RTClient.x service.
 */
public class xRTClient
        extends xService
    {
    public static xRTClient INSTANCE;
    /**
     * Global handler.
     */
    private static GlobalCookieHandler s_handlerGlobal;
    /**
     * Cached canonical type.
     */
    private TypeConstant m_typeCanonical;

    public xRTClient(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
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
            ConstantPool pool = pool();
            m_typeCanonical = type = pool.ensureTerminalTypeConstant(
                pool.ensureClassConstant(pool.ensureModuleConstant("web.xtclang.org"), "Client"));

            }
        return type;
        }

    /**
     * Injection support method.
     */
    public ObjectHandle ensureClient(Frame frame, ObjectHandle hOpts)
        {
        ServiceContext context = f_container.createServiceContext("Client");
        ClientHandle   hClient = new ClientHandle(getCanonicalClass(f_container), context);

        try
            {
            CookieHandler    handler  = createCookieHandler();
            SSLSocketFactory factory  = createSocketFactory();
            HostnameVerifier verifier = (sHostName, session) -> true;

            hClient.configure(handler, factory, verifier);

            context.setService(hClient);
            return hClient;
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
                return invokeSendRequest(frame, (ClientHandle) hTarget, (StringHandle) ahArg[0],
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

        String sAgent = "Ecstasy WebClient for " +
                frame.f_fiber.getCallingContainer().getModule().getName();

        ArrayHandle hNames  = xString.makeArrayHandle(new String[] {"User-Agent"});
        ArrayHandle hValues = xString.makeArrayHandle(new String[] {sAgent});

        return frame.assignValues(aiReturn, hNames, hValues);
        }

    /**
     * Implementation of
     *  "(Int statusCode, String[] responseHeaderNames, String[] responseHeaderValues, Byte[] responseBytes)
     *      sendRequest(String uri, String[] headerNames, String[] headerValues, Byte[] bytes)".
     */
    private int invokeSendRequest(Frame frame, ClientHandle hClient, StringHandle hMethod,
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

        String sMethod = hMethod.getStringValue();

        int cTimeoutMillis = 5_000_000;   // TODO: how to configure?
        int cMaxSize       = 8*1024*1024;

        try (var ignore = s_handlerGlobal.withHandler(hClient.m_cookieHandler))
            {
            URL               url  = new URL(hUrl.getStringValue());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod(hMethod.getStringValue());
            conn.setConnectTimeout(cTimeoutMillis);
            conn.setReadTimeout(cTimeoutMillis);
            conn.setInstanceFollowRedirects(false);

            if (conn instanceof HttpsURLConnection connTls)
                {
                connTls.setHostnameVerifier(hClient.m_verifier);
                connTls.setSSLSocketFactory(hClient.m_sslFactory);
                }

            for (Map.Entry<String, String> entry : mapHeaders.entrySet())
                {
                conn.addRequestProperty(entry.getKey(), entry.getValue());
                }

            if (sMethod.equals("PUT") || sMethod.equals("POST"))
                {
                byte[] abData = ((ByteBasedDelegate) haBytes.getTemplate()).
                                            getBytes(haBytes, 0, haBytes.m_cSize, false);

                conn.setDoOutput(true);
                conn.setDoInput(true);

                OutputStream os = conn.getOutputStream();
                os.write(abData);
                os.flush();
                }

            // process the response
            int nResponseStatus = conn.getResponseCode();

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

                    if (sName.equalsIgnoreCase("Content-Length"))
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
                    nResponseStatus = 206; // "Partial Content"
                    nContentLength  = cMaxSize;
                    }
                InputStream in = conn.getInputStream();
                byte[]      ab = new byte[nContentLength];

                in.read(ab, 0, nContentLength);
                abResponse = ab;
                }

            ObjectHandle hResponseBytes = abResponse == null
                    ? xArray.ensureEmptyByteArray()
                    : xArray.makeByteArrayHandle(abResponse, Mutability.Constant);

            return frame.assignValues(aiReturn,
                    xInt.makeHandle(nResponseStatus),
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


    // ----- data fields and constants -------------------------------------------------------------

    /**
     * Global CookieHandler.
     */
    static class GlobalCookieHandler
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
     * A {@link ServiceHandle} for the RTClient service.
     */
    protected static class ClientHandle
            extends ServiceHandle
        {
        /**
         * The {@link CookieHandler} used by this Client.
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

        protected ClientHandle(TypeComposition clazz, ServiceContext context)
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
            return "Client";
            }
        }
    }