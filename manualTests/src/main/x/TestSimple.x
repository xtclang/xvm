module TestSimple
    {
    @Inject Console console;

    package web import web.xtclang.org;

    import web.security.Authenticator;

    void run(Int depth=0)
        {
        @Inject Authenticator? providedAuthenticator;
        console.print(providedAuthenticator);
        }
    }