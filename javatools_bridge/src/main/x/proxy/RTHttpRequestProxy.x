import ecstasy.proxy.HttpRequestProxy;

const RTHttpRequestProxy
        implements HttpRequestProxy
    {
    @Override Map<String, String[]> headers.get() { TODO("native"); }

    @Override String method.get() { TODO("native"); }

    @Override String uri.get() { TODO("native"); }

    @Override Byte[]? body.get() { TODO("native"); }
    }