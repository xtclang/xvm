module TestSimple
    {
    package net import net.xtclang.org;

    import net.Uri;

    @Inject Console console;

    void run()
        {
        String[][] tests =
            [
            ["http://localhost:8080", "https://localhost:8081"],
            ["http://acme.com/base/", "rel#frag"],
            ["http://acme.com/base/#frag", "/abs"],
            ["http://acme.com/base/sub/file?query=val", "../rel/otherfile#"],
            ["http://acme.com/base/sub/file#", "../rel/otherdir/?query=val"],
            ];

        for (String[] uris : tests)
            {
    //        assert:debug;
            Uri base  = new Uri(uris[0]);
            Uri apply = new Uri(uris[1]);
            console.print($"base={base}, apply={apply}, result={base.apply(apply)}");
            }
        }
    }
