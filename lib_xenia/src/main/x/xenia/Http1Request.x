import ecstasy.collections.CaseInsensitive;

import net.UriTemplate;
import net.UriTemplate.UriParameters;

import web.AcceptList;
import web.Body;
import web.Header;
import web.HttpMessage;
import web.HttpMethod;
import web.MediaType;
import web.Protocol;
import web.Scheme;

import HttpServer.RequestInfo;


/**
 * An implementation of an HTTP/1 (i.e. 0.9, 1.0, 1.1) request, as received by a server, using the
 * raw request data provided by the `HttpServer.Handler` interface.
 */
const Http1Request(RequestInfo info, UriParameters matchResult)
        implements RequestIn
        implements Header
        implements Body {

    assert() {
        // TODO handle non-simple bodies e.g. multi-part, streaming
        assert !info.containsNestedBodies();
        if (Byte[] bytes := info.getBodyBytes()) {
            this.hasBody = True;
            this.bytes   = bytes;

            assert String[] contentTypes := info.getHeaderValuesForName(Header.ContentType);
            assert mediaType := MediaType.of(contentTypes[0]);
        } else {
            this.hasBody   = False;
            this.bytes     = [];
            this.mediaType = Text;  // whatever
        }
    }

    /**
     * Internal.
     */
    protected Boolean hasBody;


    // ----- HttpMessage interface -----------------------------------------------------------------

    @Override
    @RO Header header.get() {
        return this;
    }

    @Override
    Body? body {
        @Override
        Body? get() {
            return hasBody ? this : Null;
        }

        @Override
        void set(Body? body) {
            throw new ReadOnly();
        }
    }

    @Override
    Body ensureBody(MediaType mediaType, Boolean streaming=False) {
        assert Body body ?= this.body as "Body is absent";
        assert body.mediaType == mediaType as $"Body media type is {body.mediaType}";
        return body;
    }

    @Override
    immutable HttpMessage freeze(Boolean inPlace = False) {
        return this;
    }


    // ----- Request Interface ---------------------------------------------------------------------

    @Override
    HttpMethod method.get() {
        return info.method;
    }

    @Override
    @Lazy Uri uri.calc() {
        return info.uri;
    }

    @Override
    Scheme scheme.get() {
        return protocol.scheme;
    }

    @Override
    String authority.get() {
        return url.authority ?: assert;
    }

    @Override
    Protocol protocol.get() {
        return info.protocol;
    }

    @Override
    AcceptList accepts.get() {
        String accept = "";
        Loop: for (String add : valuesOf(Header.Accept)) {
            add = add.trim();
            if (add != "") {
                accept = accept == ""
                        ? add
                        : $"{accept},{add}";
            }
        }

        assert AcceptList list := AcceptList.of(accept);
        return list;
    }


    // ----- RequestIn Interface -------------------------------------------------------------------

    @Override
    @Lazy Uri url.calc() {
        Scheme  scheme = this.scheme;
        UInt16? port;

        if (scheme.tls) {
            port = info.route.httpsPort;
            if (port == 443) {
                port = Null;
            }
        } else {
            port = info.route.httpPort;
            if (port == 80) {
                port = Null;
            }
        }

        return uri.with(scheme = scheme.name,
                        host   = info.route.host.toString(),
                        port   = port
                       );
    }

    @Override
    SocketAddress client.get() = info.clientAddress;

    @Override
    SocketAddress server.get() = info.receivedAtAddress;

    typedef (String | List<String>) as QueryParameter;

    @Override
    @Lazy Map<String, QueryParameter> queryParams.calc() {
        if (String query ?= uri.query, query.size != 0) {
            Map<String, String>         rawParams   = query.splitMap();
            Map<String, QueryParameter> queryParams = new HashMap();

            for ((String key, String value) : rawParams) {
                queryParams.process(key, e -> {
                    if (e.exists) {
                        QueryParameter prevValue = e.value;
                        if (prevValue.is(String)) {
                            e.value = [prevValue, value];
                        } else {
                            prevValue += value;
                        }
                    } else {
                        e.value = value;
                    }
                });
            }
            return queryParams.makeImmutable();
        }
        return [];
    }

    @Override
    UriParameters matchResult;


    // ----- Header interface ----------------------------------------------------------------------

    @Override
    @RO Boolean isRequest.get() {
        return True;
    }

    @Override
    @RO Boolean modifiable.get() {
        return False;
    }

    @Override
    @Lazy String[] names.calc() = info.headerNames;

    @Override
    @Lazy List<Header.Entry> entries.calc() {
        Header.Entry[] entries = new Header.Entry[];
        for (String name : names) {
            assert String[] values := info.getHeaderValuesForName(name);
            for (String value : values) {
                entries += (name, value);
            }
        }
        return entries;
    }

    @Override
    Iterator<String> valuesOf(String name, Char? expandDelim=Null) {
        Iterator<String> iter = entries.iterator().filter(kv -> CaseInsensitive.areEqual(kv[0], name))
                                                  .map(kv -> kv[1]);

        if (expandDelim != Null) {
            iter = iter.flatMap(s -> s.split(expandDelim));
        }

        return iter.map(s -> s.trim());
    }

    @Override
    conditional String firstOf(String name, Char? expandDelim=Null) {
        if (String[] values := info.getHeaderValuesForName(name)) {
            String value = values.empty ? "" : values[0];
            if (expandDelim != Null) {
                values = value.split(expandDelim);
                value  = values[0].trim();
            }
            return True, value;
        }

        return False;
    }

    @Override
    conditional String lastOf(String name, Char? expandDelim=Null) {
        if (String[] values := info.getHeaderValuesForName(name)) {
            String value = values.empty ? "" : values[values.size-1];
            if (expandDelim != Null) {
                values = value.split(expandDelim);
                value  = values[values.size-1].trim();
            }
            return True, value;
        }

        return False;
    }

    @Override
    void removeAll(String name) {
        throw new ReadOnly();
    }


    // ----- Body interface ------------------------------------------------------------------------

    @Override
    MediaType mediaType;

    @Override
    Byte[] bytes;

    @Override
    Body from(Object content) {
        throw new ReadOnly();
    }
}