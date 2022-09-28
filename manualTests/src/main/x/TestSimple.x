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
        console.println($"none={CookieConsent.None}");
        console.println($"All_1st={CookieConsent.All_1st}");
        console.println($"All={CookieConsent.All}");

        CookieConsent custom = new CookieConsent(necessary=True, blockThirdParty=True, lastConsent=clock.now);
        console.println($"custom={custom}");
        }
    }