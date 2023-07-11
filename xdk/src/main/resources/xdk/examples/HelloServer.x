/**
 * This is an extremely basic web server example.
 *
 * To compile this example, execute the following command line from within the directory containing
 * this file:
 *
 *     xtc HelloServer
 *
 * Then, to run this example:
 *
 *     xec HelloServer
 *
 * Then, to access the running server, open a browser and enter the following URL:
 *
 *     http://localhost:8080/
 */
@WebApp
module HelloServer {
    package crypto import crypto.xtclang.org;
    package web    import web.xtclang.org;
    package xenia  import xenia.xtclang.org;

    import web.*;

    void run() {
        import crypto.KeyStore;
        @Inject(opts=new KeyStore.Info(#./https.p12, "password")) KeyStore keystore;
        xenia.createServer(this, "localhost", "localhost", 8080, 8090, keystore);

        @Inject Console console;
        console.print($|To access this server, open a browser on this machine and enter this URL:
                       |
                       |     http://localhost:8080/
                       |
                       | Or:
                       |
                       |     https://localhost:8090/
                       |
                       |Use Ctrl-C to stop.
                     );
    }

    @WebService("/")
    service SiteRoot {
        @Get
        String sayHello() = "Hello, World!";
    }
}