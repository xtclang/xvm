/**
 * OAuth2 test.
 *
 *     xec -L . oauth.xtc test.xqiz.it 192.168.1.40:8080/8090 192.168.1.30
 */
module oauth
        incorporates WebApp {

    package net   import net.xtclang.org;
    package web   import web.xtclang.org;
    package xenia import xenia.xtclang.org;

    import net.IPAddress;

    import web.*;
    import web.http.*;
    import web.responses.*;
    import web.security.*;

    import xenia.Http1Request;
    import xenia.HttpServer.ProxyCheck;
    import xenia.HttpServer.NoTrustedProxies;

    void run(String[] args=[]) {
        @Inject Console console;

        // first parameter specifies the name of the website that this app is hosted on
        String      routeString = args.size > 0 ? args[0] : "test.xqiz.it";
        // second parameter specifies the IP address/ports to bind to
        String      bindString  = args.size > 1 ? args[1] : "0.0.0.0:8080/8090";
        // optional third parameter specifies the IP address of the trusted reverse proxy
        IPAddress[] proxies     = args.size > 2 ? args[2]
                .split(',', True, True).map(s->new IPAddress(s)).toArray(Constant) : [];

        HostInfo   route          = new HostInfo(routeString);
        HostInfo   binding        = new HostInfo(bindString);
        ProxyCheck isTrustedProxy = proxies.empty ? NoTrustedProxies : ip -> proxies.contains(ip);
        xenia.createServer(this, route=route, binding=binding, isTrustedProxy=isTrustedProxy);

        String portSuffix = route.httpPort == 80 ? "" : $":{route.httpPort}";
        String uri        = $"http://{route.host}{portSuffix}";

        console.print($|The {this:module} app is listening on: {binding}
                       |
                       |Access the application at: {route}
                       |
                       |Use Ctrl-C to stop.
                     );
    }

    Authenticator createAuthenticator() {
        return new DigestAuthenticator(new FixedRealm("Hello", ["admin"="password"]));
    }

    @HttpsRequired
    @Produces(Html)
    @WebService("/")
    service Simple {
        @Default @Get
        String home(Session session, RequestIn request) {
            return $|<!DOCTYPE html>
                    |<html lang="en">
                    |<head>
                    |<style type="text/css" media="screen">
                    |table, th, td \{
                    |  border: 1px solid black;
                    |}
                    |</style>
                    |</head>
                    |
                    |<body>
                    |<h1>{this:module}</h1>
                    |
                    |<ul>
                    |<li><a href="/login">login with Github</a></li>
                    |<li><a href="/logout">logout</a></li>
                    |<li><a href="/debug">debug</a></li>
                    |</ul>
                    |
                    |<table style="font-family:'Courier New'">
                    |<tr><td>user</td><td>{session.userId? : "<anonymous>"}</td></tr>
                    |<tr><td>tls</td><td>{request.as(Http1Request).info.tls}</td></tr>
                    |{{for (val t : request.cookies()) {$.append($"<tr><td>cookie {t[0]}</td><td>{t[1]}</td></tr>");}}}
                    |<tr><td>originator</td><td>{request.originator}</td></tr>
                    |<tr><td>client</td><td>{request.client}</td></tr>
                    |<tr><td>server</td><td>{request.server}</td></tr>
                    |<tr><td>method</td><td>{request.method}</td></tr>
                    |<tr><td>uri</td><td>{request.uri}</td></tr>
                    |<tr><td>scheme</td><td>{request.scheme}</td></tr>
                    |<tr><td>route</td><td>{request.as(Http1Request).info.routeTrace}</td></tr>
                    |<tr><td>authority</td><td>{request.authority}</td></tr>
                    |<tr><td>path</td><td>{request.path}</td></tr>
                    |<tr><td>protocol</td><td>{request.protocol}</td></tr>
                    |<tr><td>accepts</td><td>{request.accepts}</td></tr>
                    |<tr><td>query</td><td>{request.queryParams}</td></tr>
                    |</table>
                    |
                    |</body>
                    |</html>
                   ;
        }

        @LoginRequired
        @Get("login")
        String login(Session session, RequestIn request) {
            return home(session, request);
        }

        @Get("logout")
        String logout(Session session, RequestIn request) {
            return home(session, request);
        }

        @Get("debug")
        String debug(Session session, RequestIn request) {
            assert:debug;
            return home(session, request);
        }
    }
}