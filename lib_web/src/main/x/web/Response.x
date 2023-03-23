/**
 * A representation of an HTTP response.
 */
interface Response
        extends HttpMessage
    {
    /**
     * The reference to the corresponding `Request`.
     */
    @RO Request? request;

    /**
     * The status of this response.
     */
    @RO HttpStatus status;

    @Override
    Iterator<String> cookieNames()
        {
        return header.valuesOf(Header.SET_COOKIE, ';')
                     .map(kv -> kv.extract('=', 0, "???").trim());
        }

    /**
     * Obtain the value of the specified cookie, if it is included in the response.
     *
     * @return True iff the specified cookie name is present
     * @return (conditional) the value associated with the specified cookie
     */
    conditional Cookie getCookie(String name)
        {
        for (String value : header.valuesOf(Header.SET_COOKIE))
            {
            // TODO CP parse name, and if it matches, build the Cookie object
            }
        return False;
        }


    // ----- cookie support ------------------------------------------------------------------------

    /**
     * A cookie is a piece of information (along with some optional configuration parameters) that
     * a server can send as part of a `Response` to a user agent, and which that user agent will
     * subsequently send back to the server with each `Request`, subject to a number of rules.
     *
     * Generally, server applications should not use cookies directly, and should instead rely on
     * some combination of the session functionality and/or a database to manage information on
     * behalf of the user. However, there are specific use cases that require cookies, and these
     * include examples of Javascript code running on the user agent accessing information in the
     * cookie.
     *
     * @param name        the name of the cookie. Cookie names prefixed with `__Secure-` or
     *                    `__Host-` can be used only if they are set with the secure attribute from
     *                    a secure (HTTPS) origin. In addition, cookies with the `__Host-` prefix
     *                    must have a path of `/` (meaning any path at the host) and must not have
     *                    a Domain attribute.
     * @param value       the value associated with the cookie. This is the cookie's content.
     * @param expires     the expiration for the cookie. A cookie with an expiration is unironically
     *                    referred to as a "permanent cookie", and one without an expiration is
     *                    referred to as a "temporary cookie", also known as a "session cookie" (but
     *                    with the use of the term "session" unrelated to the [Session] concept).
     * @param path        the path that must exist in the requested URL for the user agent to send
     *                    the `Cookie` as part of the `Request`
     * @param domain      the host to which the cookie will be sent. If omitted, this attribute
     *                    defaults to the host of the current document URL, not including
     *                    subdomains. Contrary to earlier HTTP specifications, leading dots in
     *                    domain names (.example.com) are ignored. Multiple host/domain values are
     *                    not allowed, but if a domain is specified, then subdomains are always
     *                    included.
     * @param sameSite    controls whether or not a cookie is sent with cross-site requests,
     *                    providing some protection against cross-site request forgery attacks
     *                    (CSRF). While the specified HTTP default is [Lax], this implementation
     *                    defaults to [Strict].
     * @param exposeToJS  `True` indicates that the user agent should allow client side Javascript
     *                    to detect and access this cookie
     * @param requireTLS  `True` indicates that this cookie should not be sent to a user agent
     *                    unless the connection is protected by TLS
     */
    static const Cookie(String           name,
                        String           value,
                        (Time|Duration)? expires    = Null,
                        String?          path       = Null,
                        String?          domain     = Null,
                        SameSite         sameSite   = Strict,
                        Boolean          exposeToJS = False,
                        Boolean          requireTLS = True,
                       )
        {
        enum SameSite {Strict, Lax, None}

        // ----- constructors --------------------------------------------------------------------------

        assert()
            {
            // TODO validate properties
            }

        /**
         * Copy this Cookie to make a new Cookie, with only the specified changes.
         *
         * @param name        the name of the cookie
         * @param value       the value associated with the cookie
         * @param expires     the expiration for the cookie
         * @param path        the path that must exist in the requested URL for the user agent to
         *                    send the `Cookie` as part of a `Request`
         * @param domain      the host to which the cookie will be sent
         * @param sameSite    controls whether or not a cookie is sent with cross-site requests
         * @param exposeToJS  `True` indicates that the user agent should allow client side
         *                    Javascript to detect and access this cookie
         * @param requireTLS  `True` indicates that this cookie should not be sent to a user agent
         *                    unless the connection is protected by TLS
         *
         * @return the new Cookie
         */
        Cookie with(String?          name       = Null,
                    String?          value      = Null,
                    (Time|Duration)? expires    = Null,
                    String?          path       = Null,
                    String?          domain     = Null,
                    SameSite?        sameSite   = Null,
                    Boolean?         exposeToJS = Null,
                    Boolean?         requireTLS = Null,
                   )
            {
            return new Cookie(name       ?: this.name,
                              value      ?: this.value,
                              expires    ?: this.expires,
                              path       ?: this.path,
                              domain     ?: this.domain,
                              sameSite   ?: this.sameSite,
                              exposeToJS ?: this.exposeToJS,
                              requireTLS ?: this.requireTLS,
                             );
            }

        @Override
        String toString()
            {
            StringBuffer buf = new StringBuffer();

            name.appendTo(buf);
            buf.append('=');
            value.appendTo(buf);

            if (Time time := expires.is(Time))
                {
                "; Expires=".appendTo(buf);
                http.formatImfFixDate(time).appendTo(buf);
                }
            else if (Duration duration := expires.is(Duration))
                {
                "; Max-Age=".appendTo(buf);
                duration.seconds.appendTo(buf);
                }

            if (String path ?= path)
                {
                "; Path=".appendTo(buf);
                path.appendTo(buf);
                }

            if (String domain ?= domain)
                {
                "; Domain=".appendTo(buf);
                domain.appendTo(buf);
                }

            "; SameSite=".appendTo(buf);
            sameSite.appendTo(buf);

            if (!exposeToJS)
                {
                "; HttpOnly".appendTo(buf);
                }

            if (requireTLS)
                {
                "; Secure".appendTo(buf);
                }

            return buf.toString();
            }
        }
    }