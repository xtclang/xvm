module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;
    import ecstasy.collections.*;
    import collections.*;

    import ecstasy.net.IPAddress;

    void run()
        {
        String[] tests = ["0.0.0.0", "127.0.0.1", "255.255.255.255", "127.257", "16843009",
                          "5123123123", "1.2.3.4.", "1.2.3.4.5",
                          "::1", "0::0", "1::1", "1:2::7:8", "1:2::0", "1:2:3::",
                          "1:2:3:4:5:6:7:8", "1:2:3::5:6:7:8", "1:2:3:4::5:6:7:8",
                          "1:2:3:4:5:6:7:8:", "1:2:3:4:5:6:7:8:9", ];

        for (String test : tests)
            {
            try
                {
                console.println($"{test}={new IPAddress(test)}");
                }
            catch (Exception e)
                {
                console.println($"** {test}={e.text}");
                }
            }
        }
    }
