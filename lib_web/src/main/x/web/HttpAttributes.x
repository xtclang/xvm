/**
 * A holder of HTTP message attributes.
 */
class HttpAttributes
    {
    construct()
        {
        attributes = new HashMap();
        }

    private Map<String, Object> attributes;

    /**
     * Add an attribute.
     * If an attribute is alreay mapped to the same key the new
     * attribute will overwrite the existing mapping.
     *
     * @param key       the key to map the new attribute to
     * @param attribute the attribute to add
     */
    void add(String key, Object attribute)
        {
        attributes.put(key, attribute);
        }

    /**
     * Remove any attribute mapped to the specified key.
     */
    void removeAttribute(String key)
        {
        attributes.remove(key);
        }

    /**
     * Returns the attribute mapped to the specified key.
     *
     * @return True iff there is an attribute mapped to the specified key
     * @return the value of the attribute mapped to the key
     */
    <T> conditional T getAttribute(String key)
        {
        if (Object attribute := attributes.get(key), attribute.is(T))
            {
            return True, attribute;
            }
        return False;
        }

    /**
     * The attribute prefix.
     */
    static String PREFIX = "ecstasy.http";

    /**
     * Attribute used to store the security Principal.
     */
    static String PRINCIPAL = PREFIX + ".AUTHENTICATION";

    /**
     * Attribute used to store any exception that may have occurred during request processing.
     */
    static String ERROR = PREFIX + ".error";

    /**
     * Attribute used to store the object that represents the Route.
     */
    static String ROUTE = PREFIX + ".route";

    /**
     * Attribute used to store the object that represents the Route match.
     */
    static String ROUTE_MATCH = PREFIX + ".route.match";

    /**
     * Attribute used to store the URI template defined by the route.
     */
    static String URI_TEMPLATE = PREFIX + ".route.template";

    /**
     * Attribute used to store the HTTP method name, if required within the response.
     */
    static String METHOD_NAME = PREFIX + ".method.name";

    /**
     * Attribute used to store the MediaTypeCodec. Used to override the registered codec per-request.
     */
    static String MEDIA_TYPE_CODEC = PREFIX + ".mediaType.codec";

    /**
     * Attribute used to store the MethodInvocationContext by declarative client.
     */
    static String INVOCATION_CONTEXT = PREFIX + ".invocationContext";

    /**
     * Attribute used to store the request body.
     */
    static String BODY = PREFIX + ".body";
    }