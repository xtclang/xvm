module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;
    import ecstasy.collections.*;
    import collections.*;

    import ecstasy.net.IPAddress;

    void run()
        {
        // conditional vs. Boolean should work the same way with := assignment
        (Boolean, String, String) twoStrings(Boolean retval)
            {
            return retval, "Hello", "World";
            }

        String s1 = "not";
        String s2 = "set";

//        (s1, s2) := twoStrings(False) ;
        s1 := twoStrings(False);
        console.println($"before: {s1} {s2}");
//        (s1, s2) := twoStrings(True);
        s1 := twoStrings(True);
        console.println($"after: {s1} {s2}");

//        String[] tests = ["0.0.0.0", "127.0.0.1", "255.255.255.255", "127.257", "16843009",
//                          "5123123123", "1.2.3.4", "1.2..4", "1.2.3.", "1.2.3.4.", "1.2.3.4.5",
//                          "::1", "0::0", "1::1", "1:2::7:8", "1:2::0", "1:2:3::",
//                          "1:2:3:4:5:6:7:8", "1:2:3::5:6:7:8", "1:2:3:4::5:6:7:8",
//                          "1:2:3:4:5:6:7:8:", "1:2:3:4:5:6:7:8:9", ];
//
//        console.println();
//        console.println("-- IPs");
//        for (String test : tests)
//            {
//            try
//                {
//                console.println($"{test}={new IPAddress(test)}");
//                }
//            catch (Exception e)
//                {
//                console.println($"** {test}={e.text}");
//                }
//            }
//
//        tests =
//            [
//            "http://www.myco.com/index.html?x=1#tag",
//            ];
//
//
//        console.println();
//        console.println("-- URIs");
//        for (String test : tests)
//            {
//            try
//                {
//                console.println($"{test}={new URI(test)}");
//                }
//            catch (Exception e)
//                {
//                console.println($"** {test}={e.text}");
//                }
//            }
        }
    }
