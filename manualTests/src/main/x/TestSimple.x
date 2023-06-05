module TestSimple
    {
    @Inject Console console;

    package net import net.xtclang.org;

    import net.Uri;

    void run() {
        Uri uri1 = new Uri("http://user:password@examples.xqiz.it:8090/path");
        console.print(uri1);
        assert uri1.user == "user:password" && uri1.port == 8090; // used to assert


        Uri uri2 = new Uri("http://user:password@examples.xqiz.it/path");
        console.print(uri2);
        assert uri2.user == "user:password" && uri2.port == Null; // used to assert
    }
}