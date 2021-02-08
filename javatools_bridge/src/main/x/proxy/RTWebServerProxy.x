import ecstasy.proxy.WebServerProxy;

service RTWebServerProxy
        implements WebServerProxy
    {
    @Override
    void start(Handler handler);
    }