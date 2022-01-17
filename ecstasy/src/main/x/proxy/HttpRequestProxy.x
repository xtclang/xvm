/**
 * TODO JK: explain please
 */
interface HttpRequestProxy
    {
    @RO Map<String, String[]> headers;

    @RO String method;

    @RO String uri;

    @RO Byte[]? body;
    }