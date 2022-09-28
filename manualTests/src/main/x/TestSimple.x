module TestSimple
    {
    @Inject Console console;
    @Inject Random rnd;
    @Inject Clock clock;

    package web import web.xtclang.org;
    import web.codecs.Base64Format;
    import web.CookieConsent;

    void run()
        {
        Int i = 0;
        assert:arg i > 0 as "not positive";
        }
    }