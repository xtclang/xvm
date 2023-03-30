/**
 * A representation of an outgoing HTTP response.
 */
interface ResponseOut
        extends Response
    {
    /**
     * Add the provided cookie value to the response, associated with the specified name; if a
     * value for the cookie of the same name already exists on the response, then it will be
     * replaced with the value specified here.
     *
     * @param cookie  the cookie
     */
    void addCookie(Cookie cookie)
        {
        for (String value : header.valuesOf(Header.SetCookie))
            {
            // TODO parse name, and if it matches, delete the Cookie object
            }
        header.add(Header.SetCookie, cookie.toString());
        }
    }