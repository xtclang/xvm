/**
 * To run this module, create a self-signed certificate using the following command
 * (assuming "xvm/manualTests" is the current directory):
 *
 *    keytool -genkeypair -alias hello_tls -keyalg RSA -keysize 2048 -validity 365\
 *            -dname "cn=xqiz.it, ou=hello"\
 *            -keystore src/main/x/webTests/resources/hello/https.p12 -storetype PKCS12 -storepass password
 *
 * Create a symmetric key used to encrypt the cookies:
 *
 *    keytool -genseckey -alias hello_cookies -keyalg AES -keysize 256\
 *            -keystore src/main/x/webTests/resources/hello/https.p12 -storetype PKCS12 -storepass password
 *
 * Then start the server by the command:
 *
 *    xec build/Hello.xtc password
 */
module Hello
        incorporates WebApp {
    package crypto import crypto.xtclang.org;
    package web    import web.xtclang.org;
    package xenia  import xenia.xtclang.org;
    package msg    import Messages;

    import crypto.KeyStore;
    import crypto.KeyStore.Info;

    import web.*;
    import web.responses.*;
    import web.security.*;

    import msg.Greeting;

    void run(String[] args=["password"]) {
        @Inject Console console;
        @Inject Directory curDir;

        File   store = /resources/hello/https.p12;
        String password;
        if (args.size == 0) {
            password = console.readLine("Enter password:", suppressEcho=True);
        } else {
            password = args[0];
        }

        @Inject(opts=new Info(store.contents, password)) KeyStore keystore;

        String hostName  = "localhost";
        UInt16 httpPort  = 8080;
        UInt16 httpsPort = 8090;
        xenia.createServer(this, hostName, httpPort, httpsPort, keystore);

        console.print($|Use the curl command to test, for example:
                       |
                       |  curl -L -b cookies.txt -i -w '\\n' -X GET http://{hostName}:{httpPort}/
                       |
                       | To activate the debugger:
                       |
                       |  curl -L -b cookies.txt -i -w '\\n' -X GET http://{hostName}:{httpPort}/e/debug
                       |
                       |Use Ctrl-C to stop.
                     );
    }

    // TODO GG this cannot be protected or private?
    Authenticator createAuthenticator() {
        return new DigestAuthenticator(new FixedRealm("Hello", ["admin"="addaya"]));
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

            @HttpsRequired
            @Get("s")
            String secure() {
                return "secure";
            }

            @Get("user")
            @Produces(Text)
            String getUser(Session session) {
                return session.userId ?: "";
            }

            @LoginRequired
            @Get("l")
            ResponseOut logMeIn(Session session) {
                return home();
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
            String[] echo(String path) {
                assert:debug path != "debug";

                assert RequestIn request ?= this.request;

                Session? session = this.session;
                return [
                        $"url={request.url}",
                        $"uri={request.uri}",
                        $"scheme={request.scheme}",
                        $"client={request.client}",
                        $"server={request.server}",
                        $"authority={request.authority}",
                        $"path={request.path}",
                        $"protocol={request.protocol}",
                        $"accepts={request.accepts}",
                        $"query={request.queryParams}",
                        $"user={session?.userId? : "<anonymous>"}",
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