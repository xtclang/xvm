package org.xvm.runtime.template._native.proxy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;

import java.util.List;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.MapConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.text.xString;

import org.xvm.util.ListMap;

/**
 * Native implementation of a http request proxy.
 */
public class xRTHttpRequestProxy
    extends xConst
    {
    public static xRTHttpRequestProxy INSTANCE;

    public xRTHttpRequestProxy(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        super.initNative();

        markNativeProperty("body");
        markNativeProperty("headers");
        markNativeProperty("method");
        markNativeProperty("uri");

        getCanonicalType().invalidateTypeInfo();

        ClassTemplate   templateFile = f_templates.getTemplate("proxy.HttpRequestProxy");
        TypeComposition clzOSFile    = ensureClass(templateFile.getCanonicalType());

        s_clzRequestStruct = clzOSFile.ensureAccess(Constants.Access.STRUCT);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        RequestHandle hNode = (RequestHandle) hTarget;
        switch (sPropName)
            {
            case "body":
                {
                HttpExchange exchange = hNode.f_exchange;
                InputStream  body     = exchange.getRequestBody();

                if (body == null)
                    {
                    return frame.assignValue(iReturn, xNullable.NULL);
                    }
                try
                    {
                    byte[]       ab    = body.readAllBytes();
                    ObjectHandle hBody = xArray.makeByteArrayHandle(ab, Mutability.Constant);
                    return frame.assignValue(iReturn, hBody);
                    }
                catch (IOException e)
                    {
                    return frame.raiseException(xException.ioException(frame, e.getMessage()));
                    }
                }
            case "headers":
                {
                return getPropertyHeaders(frame, hNode, iReturn);
                }
            case "method":
                {
                HttpExchange exchange = hNode.f_exchange;
                String       sMethod  = exchange.getRequestMethod().toUpperCase();
                return frame.assignValue(iReturn, makePossiblyNullHandle(sMethod));
                }
            case "uri":
                {
                HttpExchange exchange = hNode.f_exchange;
                String       sScheme  = exchange.getRequestURI().toASCIIString();
                return frame.assignValue(iReturn, makePossiblyNullHandle(sScheme));
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    /**
     * Implements property: headers.get()
     */
    public int getPropertyHeaders(Frame frame, RequestHandle hNode, int iReturn)
        {
        Headers                            headers   = hNode.f_exchange.getRequestHeaders();
        ConstantPool                       poolCtx   = frame.poolContext();
        Map<StringConstant, ArrayConstant> mapResult = new ListMap<>();
        TypeConstant                       typeArray = poolCtx.ensureArrayType(poolCtx.typeString());

        for (Map.Entry<String, List<String>> entry : headers.entrySet())
            {
            String           sName   = entry.getKey();
            StringConstant[] ahValue = entry.getValue()
                                          .stream()
                                          .map(poolCtx::ensureStringConstant)
                                          .toArray(StringConstant[]::new);

            ArrayConstant constant = poolCtx.ensureArrayConstant(typeArray, ahValue);
            mapResult.put(poolCtx.ensureStringConstant(sName), constant);
            }

        TypeConstant typeMap     = poolCtx.ensureParameterizedTypeConstant(poolCtx.typeMap(),
                                        poolCtx.typeString(), typeArray);
        TypeConstant typeResult  = poolCtx.ensureImmutableTypeConstant(typeMap);
        MapConstant  constResult = poolCtx.ensureMapConstant(typeResult, mapResult);

        return frame.assignDeferredValue(iReturn, frame.getConstHandle(constResult));
        }

    private ObjectHandle makePossiblyNullHandle(String s)
        {
        return s == null
            ? xNullable.NULL
            : xString.makeHandle(s);
        }

    /**
     * Construct a new {@link RequestHandle} representing the specified request.
     *
     * @param frame      the current frame
     * @param exchange   the {@link HttpExchange} this request represents
     * @param iReturn    the register id to place the created handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    public ObjectHandle createHandle(Frame frame, HttpExchange exchange, int iReturn)
        {
        TypeComposition clzStruct = s_clzRequestStruct;
        return new RequestHandle(clzStruct, exchange);
        }

    // ----- ObjectHandle --------------------------------------------------------------------------

    public static class RequestHandle
            extends ObjectHandle.GenericHandle
        {
        protected final HttpExchange f_exchange;

        protected RequestHandle(TypeComposition clazz, HttpExchange exchange)
            {
            super(clazz);

            f_exchange = exchange;
            }

        public HttpExchange getHttpExchange()
            {
            return f_exchange;
            }
        }

    // ----- constants -----------------------------------------------------------------------------

    private static TypeComposition s_clzRequestStruct;
    }
