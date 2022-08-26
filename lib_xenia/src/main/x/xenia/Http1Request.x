import ecstasy.collections.CaseInsensitive;

import net.IPAddress;
import net.SocketAddress;
import net.URI;

import web.AcceptList;
import web.Body;
import web.Header;
import web.HttpMessage;
import web.HttpMethod;
import web.MediaType;
import web.Protocol;
import web.Scheme;
import web.routing.UriTemplate;
import web.routing.UriTemplate.UriParameters;


/**
 * An implementation of an HTTP/1 (i.e. 0.9, 1.0, 1.1) request, as received by a server, using the
 * raw request data provided by the `HttpServer.Handler` interface.
 */
const Http1Request(RequestInfo info, UriParameters matchResult)
        implements Request
        implements Header
        implements Body
    {
    assert()
        {
        // TODO handle non-simple bodies e.g. multi-part, streaming
        assert !info.containsNestedBodies();
        if (Byte[] bytes := info.getBodyBytes())
            {
            this.hasBody = True;
            this.bytes   = bytes;

            assert String[] contentTypes := info.getHeaderValuesForName("ContentType");
            assert mediaType := MediaType.of(contentTypes[0]);
            }
        else
            {
            this.hasBody   = False;
            this.bytes     = [];
            this.mediaType = Text;  // whatever
            }
        }

    /**
     * Internal.
     */
    protected Boolean hasBody;


    // ----- RequestInfo interface -----------------------------------------------------------------

    static interface RequestInfo
        {
        /**
         * Obtain the IP address that the request was sent from.
         *
         * @return the client IP address, in either v4 or v6 form
         */
        IPAddress getClientAddress();

        /**
         * Obtain the port number on the client that the request was sent from
         *
         * @return the client port number
         */
        UInt16 getClientPort();

        /**
         * Obtain the IP address that the request was received on.
         *
         * @return the server IP address, in either v4 or v6 form
         */
        IPAddress getServerAddress();

        /**
         * Obtain the port number on the server that the request was received on
         *
         * @return the server port number
         */
        UInt16 getServerPort();

        /**
         * Obtain the HTTP method name (such as "GET" or "PUT") that is indicated by the request.
         *
         * @return the HTTP method name
         */
        HttpMethod getMethod();

        /**
         * Obtain the HTTP URI that is indicated by the request.
         *
         * @return the URI from the request
         */
        URI getUri();

        /**
         * Obtain the HTTP protocol name (such as "HTTP/1.1") that is indicated by the request. In HTTP
         * 1.1, this is part of the "request line", which is the first line of text in the request.
         *
         * @return the HTTP protocol name
         */
        String getProtocolString();

        /**
         * Obtain the number of header name/value pairs
         *
         * @return the the number of header name/value pairs
         */
        Int getHeaderCount();

        /**
         * Obtain the specified header name and value.
         *
         * @param index  a value between `0` (inclusive) and `getHeaderCount` (exclusive)
         *
         * @return the name of the specified header
         * @return the value of the specified header
         */
        (String name, String value) getHeader(Int index);

        /**
         * Obtain all of the values for the specified header name.
         *
         * @param name  the case-insensitive header name
         *
         * @return True if there is at least one header for the specified name
         * @return (conditional) an array of one or more values associated with the specified name
         */
        conditional String[] getHeaderValuesForName(String name);

        /**
         * Obtain all of the bytes in the request body.
         *
         * @return True if there is a body
         * @return (conditional) an array of `Byte` representing the body content
         */
        conditional Byte[] getBodyBytes();

        /**
         * Determine if the body contains nested information (e.g. mult-part) with its own headers,
         * etc.
         *
         * @return True if there is one or more nested bodies
         * @return (conditional) an array of `RequestInfo` objects, each representing one nested
         *         body
         */
        conditional RequestInfo[] containsNestedBodies();
        }


    // ----- HttpMessage interface -----------------------------------------------------------------

    @Override
    @RO Header header.get()
        {
        return this;
        }

    @Override
    Body? body
        {
        @Override
        Body? get()
            {
            return hasBody ? this : Null;
            }

        @Override
        void set(Body? body)
            {
            throw new ReadOnly();
            }
        }

    @Override
    Body ensureBody(MediaType mediaType, Boolean streaming=False)
        {
        assert Body body ?= this.body as "Body is absent";
        assert body.mediaType == mediaType as $"Body media type is {body.mediaType}";
        return body;
        }

    @Override
    immutable HttpMessage freeze(Boolean inPlace = False)
        {
        return this;
        }


    // ----- Request interface ---------------------------------------------------------------------

    @Override
    HttpMethod method
        {
        @Override
        HttpMethod get()
            {
            return info.getMethod();
            }

        @Override
        void set(HttpMethod method)
            {
            throw new ReadOnly();
            }
        }

    @Override
    Scheme scheme
        {
        @Override
        Scheme get()
            {
            return Scheme.byName.getOrNull(uri.scheme?)? : assert;
            }

        @Override
        void set(Scheme scheme)
            {
            throw new ReadOnly();
            }
        }

    @Override
    String authority
        {
        @Override
        String get()
            {
            return uri.authority ?: assert;
            }

        @Override
        void set(String authority)
            {
            throw new ReadOnly();
            }
        }

    @Override
    String path
        {
        @Override
        String get()
            {
            return uri.path?.toString() : assert;
            }

        @Override
        void set(String path)
            {
            throw new ReadOnly();
            }
        }

    @Override
    String requestLine.get()
        {
        // REVIEW
        return $"{method} {path} {protocol}";
        }

    @Override
    @Lazy URI uri.calc()
        {
        return info.getUri();
        }

    @Override
    @RO SocketAddress? client.get()
        {
        return (info.getClientAddress(), info.getClientPort());
        }

    @Override
    @RO SocketAddress? server.get()
        {
        return (info.getServerAddress(), info.getServerPort());
        }

    @Override
    Protocol? protocol.get()
        {
        String name = info.getProtocolString();
        return Protocol.byName.getOrCompute(name, () -> throw new IllegalState($"unknown protocol: {name}"));
        }

    @Override
    @Lazy Map<String, String|List<String>> queryParams.calc()
        {
        TODO CP
        }

    @Override
    UriParameters matchResult;

    @Override
    AcceptList accepts.get()
        {
        String accept = "";
        Loop: for (String add : valuesOf("Accept"))
            {
            add = add.trim();
            if (add != "")
                {
                accept = accept == ""
                        ? add
                        : $"{accept},{add}";
                }
            }

        assert AcceptList list := AcceptList.of(accept);
        return list;
        }

    @Override
    void setCookie(String name, String value)
        {
        throw new ReadOnly();
        }


    // ----- Header interface ----------------------------------------------------------------------

    @Override
    @RO Boolean isRequest.get()
        {
        return True;
        }

    @Override
    @RO Boolean modifiable.get()
        {
        return False;
        }

    @Override
    @Lazy List<String> names.calc()
        {
        return new String[info.getHeaderCount()](i->info.getHeader(i)).freeze(inPlace=True);
        }

    @Override
    @Lazy List<Entry> entries.calc()
        {
        return new List<Entry>()
            {
            @Override
            Int size.get()
                {
                return names.size;
                }

            @Override
            @Op("[]") Entry getElement(Int index)
                {
                assert:bounds 0 <= index < size;
                return info.getHeader(index);
                }
            };
        }

    @Override
    Iterator<String> valuesOf(String name, Char? expandDelim=Null)
        {
        import ecstasy.collections.CaseInsensitive; // TODO GG comment this line out and re-compile :-o
        Iterator<String> iter = entries.iterator().filter(e -> CaseInsensitive.areEqual(e[0], name))
                                                  .map(e -> e[1]);

        if (expandDelim != Null)
            {
            iter = iter.flatMap(s -> s.split(expandDelim).iterator());
            }

        return iter.map(s -> s.trim());
        }

    @Override
    conditional String firstOf(String name, Char? expandDelim=Null)
        {
        if (String[] values := info.getHeaderValuesForName(name))
            {
            String value = values.empty ? "" : values[0];
            if (expandDelim != Null)
                {
                values = value.split(expandDelim);
                value  = values[0].trim();
                }
            return True, value;
            }

        return False;
        }

    @Override
    conditional String lastOf(String name, Char? expandDelim=Null)
        {
        if (String[] values := info.getHeaderValuesForName(name))
            {
            String value = values.empty ? "" : values[values.size-1];
            if (expandDelim != Null)
                {
                values = value.split(expandDelim);
                value  = values[values.size-1].trim();
                }
            return True, value;
            }

        return False;
        }

    @Override
    void removeAll(String name)
        {
        throw new ReadOnly();
        }


    // ----- Body interface ------------------------------------------------------------------------

    @Override
    MediaType mediaType;

    @Override
    Byte[] bytes;

    @Override
    Body from(Object content)
        {
        throw new ReadOnly();
        }
    }