import libweb.Client;

/**
 * The native Client.Connector implementation.
 */
service RTConnector
        implements Client.Connector {

    @Override
    (String[] defaultHeaderNames, String[] defaultHeaderValues) getDefaultHeaders() = TODO("native");

    @Override
    (Int statusCode, String[] responseHeaderNames, String[] responseHeaderValues, Byte[] responseBytes)
        sendRequest(String method, String uri,
                    String[] headerNames, String[] headerValues, Byte[] bytes) = TODO("native");
}