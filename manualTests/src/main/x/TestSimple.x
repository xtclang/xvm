module TestSimple
    {
    @Inject Console console;
    @Inject Random rnd;
    @Inject Clock clock;

    package net   import net.xtclang.org;
    package web   import web.xtclang.org;
    package xenia import xenia.xtclang.org;

    import net.IPAddress;

    import web.codecs.Base64Format;
    import web.CookieConsent;

    import xenia.SessionCookie;

    void run()
        {
        SessionCookie cookie = new SessionCookie(sessionId    = 123,
                                                 cookieId     = Consent,
                                                 knownCookies = SessionCookie.CookieId.All,
                                                 consent      = All_1st.with(lastConsent=Date:2022-08-09),
                                                 expires      = Time:2022-09-10T11:12:13Z,
                                                 lastIp       = new IPAddress("1.2.3.4"),
                                                );

        String text = cookie.toString();
        console.println($"orig: {text}");

        SessionCookie cookie2 = new SessionCookie(text);
        String text2 = cookie.toString();

        console.println($"copy: {text2}");

        assert text == text2;
        assert cookie.sessionId    == cookie2.sessionId;
        assert cookie.cookieId     == cookie2.cookieId;
        assert cookie.knownCookies == cookie2.knownCookies;
        assert cookie.consent      == cookie2.consent;
        assert cookie.expires      == cookie2.expires;
        assert cookie.lastIp       == cookie2.lastIp;
        assert cookie.created      == cookie2.created;
        assert cookie.version      == cookie2.version;
        assert cookie.salt         == cookie2.salt;
        assert cookie.text         == cookie2.text;
        assert cookie == cookie2;

//        console.println($"none={new CookieConsent(CookieConsent.None.toString())}");
//        console.println($"All_1st={new CookieConsent(CookieConsent.All_1st.toString())}");
//        console.println($"All={new CookieConsent(CookieConsent.All.toString())}");
//
//        CookieConsent custom = new CookieConsent(necessary=True, blockThirdParty=True, lastConsent=clock.now);
//        console.println($"custom={new CookieConsent(custom.toString())}");
        }
    }