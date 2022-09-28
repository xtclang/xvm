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
        console.println($"none={new CookieConsent(CookieConsent.None.toString())}");
        console.println($"All_1st={new CookieConsent(CookieConsent.All_1st.toString())}");
        console.println($"All={new CookieConsent(CookieConsent.All.toString())}");

        CookieConsent custom = new CookieConsent(necessary=True, blockThirdParty=True, lastConsent=clock.now);
        console.println($"custom={new CookieConsent(custom.toString())}");
        }
    }