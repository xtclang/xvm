/**
 * To run this module, create a self-signed certificate using the following command
 * (assuming "xvm/manualTests" is the current directory):
 *
 *    keytool -genkeypair -keyalg RSA -alias hello -keystore data/hello/https.p12\
 *            -storetype PKCS12 -storepass password -validity 365 -keysize 2048 -dname cn=xqiz.it
 *
 * Then start the server by the command:
 *
 *    xec build/Hello.xtc password
 */
module Hello
        incorporates WebApp
    {
    package crypto import crypto.xtclang.org;
    package web    import web.xtclang.org;
    package xenia  import xenia.xtclang.org;

    // TODO: package authdb import webauthdb.xtclang.org;

    import crypto.KeyStore;
    import crypto.KeyStore.Info;

    import web.*;
    import web.security.*;

    void run(String[] args=["password"])
        {
        @Inject Console console;
        @Inject Directory curDir;

        File   store = curDir.fileFor("data/hello/https.p12");
        String password;
        if (args.size == 0)
            {
            console.print("Enter password:", suppressNewline=True);
            password = console.readLine(suppressEcho=True);
            }
        else
            {
            password = args[0];
            }

        @Inject(opts=new Info(store.contents, password)) KeyStore keystore;

        String hostName  = "localhost";
        UInt16 httpPort  = 8080;
        UInt16 httpsPort = 8090;
        xenia.createServer(this, hostName, keystore, httpPort, httpsPort);

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

    @Override
    protected Authenticator createAuthenticator()
        {
        return new BasicAuthenticator(new FixedRealm("Hello", ["admin"="addaya"]));
        }

    package inner
        {
        @WebService("/")
        service Simple
            {
            SimpleData simpleData.get()
                {
                return session?.as(SimpleData) : assert;
                }

            @Get
            String hello()
                {
                return "hello";
                }

            @HttpsRequired
            @Get("s")
            String secure()
                {
                return "secure";
                }

            @LoginRequired
            @Get("l")
            String logMeIn(Session session)
                {
                return $"logged in as {session.userId}";
                }

            @Get("c")
            Int count(SimpleData sessionData)
                {
                return sessionData.counter++;
                }

            @Default @Get
            @Produces(Text)
            String askWhat()
                {
                return "what?";
                }

            static mixin SimpleData
                    into Session
                {
                Int counter;
                }
            }

        @WebService("/e")
        service Echo
            {
            @Get("{path}")
            String[] echo(String path)
                {
                assert:debug path != "debug";

                Map<String, String|List<String>> query = request?.queryParams : assert;
                return [path, query.empty ? "" : $"{query}"];
                }
            }

        @StaticContent("/static", /resources/hello)
        service Content
            {
            }
        }
    }