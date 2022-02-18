module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;
    import ecstasy.collections.*;
    import collections.*;

    import ecstasy.net.IPAddress;

    void run()
        {
        String[] tests =
            [
            "http://[1:2:3:4:5:6:7:8]",
            "http://[1.2.3.4.5.6.7.8]",
            "http://1.2.3.4",
            "http://www.myco.com/",
            "#tag",
            "?x=1#tag",
            "http://www.myco.com/index.html?x=1#tag",

            // bunch of random junk
            "https://www.example.com/boat.php",
            "http://example.com/appliance.aspx",
            "http://www.example.com/",
            "http://www.example.com/alarm/arch.htm",
            "https://www.example.org/addition/bead?airport=art&brother=birds",
            "https://www.example.com/bear#addition",
            "https://www.example.net/bat/beds",
            "https://www.example.com/",
            "http://example.org/arm.html",
            "http://www.example.com/arithmetic",
            "http://example.net/?airport=arithmetic&birth=branch",
            "https://example.com/?apparel=balance",
            "http://www.example.com/",
            "https://www.example.com/bite",
            ];

        console.println();
        console.println("-- URIs");
        for (String test : tests)
            {
            try
                {
                console.println($"{test} = {new URI(test)}");
                }
            catch (Exception e)
                {
                console.println($"** {test} = {e.text}");
                }
            }
        }
    }
