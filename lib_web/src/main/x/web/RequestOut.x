/**
 * A representation of an outgoing HTTP request.
 */
interface RequestOut
        extends Request
    {
    /**
     * Create a copy of this request TODO CP
     *
     * @param uri       (optional) new Uri (not all the Uri parts may be allowed)
     * @param protocol  (optional) new protocol
     * @param path      (optional) new path
     * @param accepts   (optional) new accepted media types
     */
    RequestOut with(Uri?        uri      = Null,
                    Protocol?   protocol = Null,
                    Path?       path     = Null,
                    AcceptList? accepts  = Null,
                    );

    /**
     * Add the specified cookie information to the message; if the cookie of the same name already
     * exists, then it is replaced with the new value.
     *
     * @param name   the cookie name to include in the request
     * @param value  the cookie value
     */
    void addCookie(String name, String value)
        {
        // TODO CP remove any existing entry for this cookie name (or replace its value)
        // TODO validation of cookie name, validation of value
        header.add(Header.COOKIE, $"{name}={value}");
        }
    }