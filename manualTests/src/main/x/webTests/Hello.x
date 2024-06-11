/**
 * You can run this module with or without port forwarding.
 *
 * Then start the server by the command:
 *
 *    xec build/Hello.xtc [routeName:httpPort/httpsPort] [bindName:bindHttpPort/bindHttpsPort]
 *
 * This is an internal test for development and not an "easy to use" example. Defaults assume port
 * forwarding (80 -> 8080, 443 -> 8090).
 */
module Hello
        incorporates WebApp {
    package json  import json.xtclang.org;
    package net   import net.xtclang.org;
    package web   import web.xtclang.org;
    package xenia import xenia.xtclang.org;

    import net.IPAddress;

    import json.*;

    import web.*;
    import web.http.*;
    import web.responses.*;
    import web.security.*;

    import xenia.CookieBroker;
    import xenia.Http1Request;
    import xenia.SessionManager;

    package msg import Messages;
    import msg.Greeting;

    void run(String[] args=["localhost", "localhost:8080/8090"]) {
        @Inject Console console;

        String      routeString = args.size > 0 ? args[0] : "localhost";
        String      bindString  = args.size > 1 ? args[1] : "localhost:8080/8090";
        IPAddress[] proxies     = args.size > 2 ? args[2]
                .split(',', True, True).map(s->new IPAddress(s)).toArray(Constant) : [];

        // optional third parameter specifies the IP address of the trusted reverse proxy
        xenia.HttpServer.ProxyCheck isTrustedProxy = args.size > 2
                ? (ip -> proxies.contains(ip))
                : xenia.HttpServer.NoTrustedProxies;

        HostInfo hostOf(String addressString) {
            if (Int portOffset := addressString.indexOf(":")) {
                String portsString = addressString.substring(portOffset+1);
                addressString = addressString[0 ..< portOffset];

                assert Int slashOffset := portsString.indexOf("/") as "Ports are missing";

                UInt16 httpPort  = new UInt16(portsString[0 ..< slashOffset]);
                UInt16 httpsPort = new UInt16(portsString.substring(slashOffset+1));
                return new HostInfo(addressString, httpPort, httpsPort);
            }
            return new HostInfo(addressString);
        }

        HostInfo route   = hostOf(routeString);
        HostInfo binding = hostOf(bindString);

        @Inject Directory curDir;
        Directory dataDir = curDir.dirFor("data");

        xenia.createServer(this, route=route, binding=binding,
                extras=[
                    ExtraFiles   = () -> new ExtraFiles(dataDir),
                    CookieBroker = () -> cookieBroker,
                    ],
                isTrustedProxy=isTrustedProxy);

        String portSuffix = route.httpPort == 80 ? "" : $":{route.httpPort}";
        String uri        = $"http://{route.host}{portSuffix}";

        console.print($|Hello server is bound to {binding}
                       |
                       |Use the curl command to test, for example:
                       |
                       |  curl -L -b cookies.txt -i -w '\\n' -X GET {uri}
                       |
                       | To activate the debugger:
                       |
                       |  curl -L -b cookies.txt -i -w '\\n' -X GET {uri}/e/debug
                       |
                       |Use Ctrl-C to stop.
                     );
    }

    private @Lazy CookieBroker cookieBroker.calc() = new CookieBroker(this);

    // ----- WebApp duck-type methods --------------------------------------------------------------

    Authenticator createAuthenticator() =
        new DigestAuthenticator(new FixedRealm("Hello", "admin", "addaya"));
// TODO: test ChainAuthenticator
//        new BasicAuthenticator(new FixedRealm("Hello", "admin", "addaya"));

    sessions.Broker createSessionBroker() = cookieBroker;


    // ----- Web services --------------------------------------------------------------------------

    /**
     * This service allows accessing files in the "data" directory.
     */
    @WebService("/data")
    service ExtraFiles
            incorporates StaticContent {
        construct(Directory extra){
            construct StaticContent(path, extra);
        }
    }

    package inner {
        @WebService("/")
        service Simple {
            SimpleData simpleData.get() {
                return session?.as(SimpleData) : assert;
            }

            @Get
            ResponseOut home() {
                return new HtmlResponse(File:/resources/hello/index.html);
            }

            @Get("hello")
            Greeting greeting() {
                return ("Hi", 1);
            }

            @HttpsRequired(autoRedirect=True)
            @Get("s")
            ResponseOut secure() {
                return home();
            }

            @Get("user")
            @Produces(Text)
            String getUser(Session session) {
                return session.principal?.name : "";
            }

            @LoginRequired
            @Get("l")
            String logMeIn(Session session) {
                return $"user={session.principal?.name : "<anonymous>"}";
            }

            @Get("d")
            ResponseOut logMeOut() {
                session?.deauthenticate();
                return home();
            }

            @Get("c")
            Int count(SimpleData sessionData) {
                return sessionData.counter++;
            }

            @Post("upload")
            String upload(RequestIn request) {
                if (Body body ?= request.body) {
                    FormDataFile[] fileData = http.extractFileData(body);
                    if (!fileData.empty) {
                        return fileData.map(fd ->
                            $"{fd.name}; {fd.mediaType}; {fd.contents.size} bytes").toString();
                    }
                }
                return "<No data>";
            }

            @Default @Get
            @Produces(Text)
            String askWhat() {
                return "what?";
            }

            static mixin SimpleData
                    into Session {
                Int counter;
            }
        }

        @WebService("/e")
        service Echo {
            @Get("{path}")
            String[] getEcho(String path) {
                assert:debug path != "debug";

                assert RequestIn request ?= this.request,
                                 request := &request.revealAs((protected Http1Request));

                Session? session = this.session;
                return [
                        $"url={request.url}",
                        $"uri={request.uri}",
                        $"scheme={request.scheme}",
                        $"originator={request.originator}",
                        $"client={request.client}",
                        $"server={request.server}",
                        $"route={request.info.routeTrace}",
                        $"authority={request.authority}",
                        $"path={request.path}",
                        $"protocol={request.protocol}",
                        $"accepts={request.accepts}",
                        $"query={request.queryParams}",
                        $"user={session?.principal?.name : "<anonymous>"}",
                       ];
            }
        }

        @WebService("/settings")
        service Settings {
            @LoginRequired
            @Get("allow-cookies")
            ResponseOut turnOnPersistentCookies(Session session) {
                Boolean       oldExclusiveAgent = session.exclusiveAgent;
                CookieConsent oldCookieConsent  = session.cookieConsent;

                session.exclusiveAgent = True;
                session.cookieConsent  = oldCookieConsent.with(necessary   = True,
                                                               lastConsent = xenia.clock.now.date
                                                              );

                return new HtmlResponse($|Session cookies enabled=\
                                         |{session.exclusiveAgent}\
                                         | (was {oldExclusiveAgent});\
                                         | consent={session.cookieConsent}\
                                         | (was {oldCookieConsent})
                                         |<br><a href="/">home</a>
                                       );
            }

            @HttpsRequired
            @Get("disallow-cookies")
            ResponseOut turnOffPersistentCookies(Session session) {
                Boolean       oldExclusiveAgent = session.exclusiveAgent;
                CookieConsent oldCookieConsent  = session.cookieConsent;

                session.exclusiveAgent = False;
                session.cookieConsent  = new CookieConsent(lastConsent=xenia.clock.now.date);

                return new HtmlResponse($|Session cookies enabled=\
                                         |{session.exclusiveAgent}\
                                         | (was {oldExclusiveAgent});\
                                         | consent={session.cookieConsent}\
                                         | (was {oldCookieConsent})
                                         |<br><a href="/">home</a>
                                       );
            }
        }

        @StaticContent("/static", /resources/hello)
        service Content {}
    }
}