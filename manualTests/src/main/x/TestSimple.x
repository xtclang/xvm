module TestSimple {
    @Inject Console console;

    package net import net.xtclang.org;

    import net.Uri;
    import net.Url;

    void run() {
        testUri("host.xqiz.it");
        console.print();
        testUrl("host.xqiz.it");
    }

    void testUri(String name) {
        Uri uri = new Uri(name);
        console.print($"{uri.authority=} {uri.path=}");

        Uri urlHttps = uri.with(scheme="https");
        Uri urlHttp  = uri.with(scheme="http");
        console.print($"{urlHttps=} {urlHttp=}");
    }

    void testUrl(String name) {
        Url url = new Url(name);
        console.print($"{url.authority=} {url.path=}");

        Uri uriHttps = url.with(scheme="https");
        Uri uriHttp  = url.with(scheme="http");
        console.print($"{uriHttps=} {uriHttp=}");
    }
}
