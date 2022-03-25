/**
 * A set of the the most common protocols in use today. These are pre-defined only as a means to
 * avoid having to instantiate additional copies of the same common information.
 */
enum Protocols(String name, Version version, Boolean TLS, String fullText)
        extends Protocol(name, version, TLS, fullText)
    {
    HTTP1  ("HTTP"  , v:1  , False, "HTTP/1.0"),
    HTTP11 ("HTTP"  , v:1.1, False, "HTTP/1.1"),
    HTTPS11("HTTPS" , v:1.1, True , "HTTP/1.1"),
    HTTP2  ("HTTP"  , v:2  , False, "HTTP/2"  ),
    HTTPS2 ("HTTPS" , v:2  , True , "HTTP/2"  ),
    HTTP3  ("HTTP"  , v:3  , False, "HTTP/3"  ),
    HTTPS3 ("HTTPS" , v:3  , True , "HTTP/3"  ),
    WS13   ("WS"    , v:13 , False, "WS/13"   ),
    WSS13  ("WSS"   , v:13 , True , "WSS/13"  ),
    }

