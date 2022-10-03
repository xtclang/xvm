@web.LoginRequired(High)
@web.WebApp
module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        Class clz = Test;

        assert clz.is(LoginRequired);
        assert clz.trust == "None";
        }

    mixin Endpoint
            into Method<WebService>;

    mixin LoginRequired(String trust)
            extends HttpsRequired   // this used to assert
        {}

    mixin HttpsRequired
            into Class<WebApp> | Class<WebService> | Endpoint;

    @LoginRequired("None")
    @WebService
    service Test
        {
        }

    mixin WebService
            into service;

    mixin WebApp
            into Module;
    }