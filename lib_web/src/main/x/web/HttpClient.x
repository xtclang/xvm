import net.Uri;

import crypto.Algorithms;
import crypto.Signer;

import codecs.Base64Format;
import codecs.Codec;
import codecs.Registry;
import codecs.Utf8Codec;

import ecstasy.collections.CaseInsensitive;

import Header.Entry;


/**
 * An implementation of the `Client` API.
 */
const HttpClient
        implements Client {

    construct(Registry? registry = Null) {
        this.registry = registry ?: new Registry();
    }

    /**
     * The connector.
     */
    @Inject Client.Connector connector;

    @Override
    Registry registry;

    @Override
    @Lazy Header defaultHeaders.calc() {
        (String[] defaultHeaderNames, String[] defaultHeaderValues) =
            connector.getDefaultHeaders();

        Header header = new ResponseHeader(defaultHeaderNames, defaultHeaderValues);
        return &header.maskAs(Header);
    }

    @Override
    ResponseIn send(RequestOut request, PasswordCallback? callback = Null) {
        List<Entry> entries = request.header.entries;

        Int      headerCount  = entries.size;
        String[] headerNames  = new Array(headerCount);
        String[] headerValues = new Array(headerCount);
        for (Int i : 0 ..< headerCount) {
            Entry entry = entries[i];

            headerNames  += entry[0];
            headerValues += entry[1];
        }

        String  method    = request.method.name;
        Uri     uri       = request.uri;
        Byte[]  bytes     = request.body?.bytes : [];

        // TODO: allow these to be configurable
        Boolean autoRedirect = True;
        Int     retryLimit   = 8;

        for (Int retryCount = 0; True; retryCount++) {
            (Int      statusCode,
             String[] responseHeaderNames,
             String[] responseHeaderValues,
             Byte[]   responseBytes) =
                connector.sendRequest(method, uri.toString(), headerNames, headerValues, bytes);

            if (autoRedirect && 300 <= statusCode < 400 && retryCount < retryLimit,
                    Int index := responseHeaderNames.indexOf("Location")) {
                Uri redirect = new Uri(responseHeaderValues[index]);
                uri = uri.apply(redirect);
                continue;
            }

            HttpStatus status = HttpStatus.of(statusCode);
            Authorize:
            if (status == Unauthorized && retryCount < retryLimit,
                Int challengeIx := responseHeaderNames.indexOf(
                        CaseInsensitive.areEqual(_, "WWW-Authenticate"))) {

                // RFC 9110 allows for multiple challenges - see section 11.6.1 WWW-Authenticate
                // https://datatracker.ietf.org/doc/html/rfc9110#name-authenticating-users-to-ori
                // For now, however, we assume a single challenge, which is either "Basic" or
                // "Digest"
                String challenge = responseHeaderValues[challengeIx];

                enum Auth {Basic, Digest}

                Auth authMethod;
                Int  realmIndex;
                if (challenge.startsWith("Basic ")) {
                    authMethod = Basic;
                    realmIndex = 6; // "Basic ".size;
                } else if (challenge.startsWith("Digest ")) {
                    authMethod = Digest;
                    realmIndex = 7; // "Digest ".size;
                } else {
                    break Authorize;
                }

                Map<String, String> props = challenge.substring(realmIndex).splitMap();
                String              realm;
                if (!(realm := props.get("realm"))) {
                    break Authorize;
                }

                realm := realm.unquote();

                String name;
                String password;
                if (String userInfo ?= uri.user, Int delim := userInfo.indexOf(':')) {
                    name     = userInfo[0 ..< delim];
                    password = userInfo[delim >..< userInfo.size];
                } else if (callback != Null) {
                    (name, password) = callback(realm);
                } else {
                    break Authorize;
                }

                String authorization;
                switch (authMethod) {
                case Basic:
                    authorization = authorizeBasic(name, password);
                    break;

                case Digest:
                    if (authorization :=
                            authorizeDigest(request, realm, name, password, retryCount, props)) {
                        break;
                    } else {
                        break Authorize;
                    }
                }

                headerNames  += "Authorization";
                headerValues += authorization;
                continue; // resend the request with the "Authorization" header
            }

            Header     responseHeader = new ResponseHeader(responseHeaderNames, responseHeaderValues);
            ResponseIn response       = new Response(registry, status, &responseHeader.maskAs(Header), responseBytes);
            return &response.maskAs(ResponseIn);
        }
    }

    /**
     * Process the WWW-Authenticate field and generate the corresponding Authorization field
     * according to the RFC 7617
     * [The 'Basic' HTTP Authentication Scheme](https://datatracker.ietf.org/doc/html/rfc7617).
     */
    private static String authorizeBasic(String name, String password)
        {
        return $"Basic {Base64Format.Instance.encode(Utf8Codec.encode($"{name}:{password}"))}";
        }

    /**
     * Process the WWW-Authenticate field and generate the corresponding Authorization field
     * according to the RFC 7616
     * [The 'Digest' HTTP Authentication Scheme](https://datatracker.ietf.org/doc/html/rfc7616).
     */
    private static conditional String authorizeDigest(RequestOut request, String realm, String name,
            String password, Int retryCount, Map<String, String> props)
        {
        // create cnonce and extract necessary properties (see DigestAuthenticator.parseDigest)
        import security.DigestAuthenticator;
        import security.Realm.Hash;

        import DigestAuthenticator.require;
        import DigestAuthenticator.toHash;

        static String toString(Hash hash) {
            // toString() is an overloaded name; cannot be imported "as-is"
            return DigestAuthenticator.toString(hash);
        }

        if (String algorithm := require(props, "algorithm", Null),
            String opaque    := require(props, "opaque"   , True),
            String nonce     := require(props, "nonce"    , True)) {

            @Inject Algorithms algorithms;

            static String Suffix = "-sess";

            Signer hasher;
            if (algorithm.endsWith(Suffix),
                hasher := algorithms.hasherFor(algorithm[0 ..< algorithm.size-Suffix.size])) {} else {
                return False;
            }

            @Inject Random rnd;
            String cnonce = Base64Format.Instance.encode(rnd.bytes(9));

            String qop = "auth";
            qop := require(props, "qop", True);
            if (qop != "auth") {
                return False; // TODO implement auth-int
            }

            String ncText = retryCount.toString().rightJustify(8, fill='0');

            // create a response digest
            //
            //    response = KD ( H(A1), unq(nonce)
            //                           ":" nc
            //                           ":" unq(cnonce)
            //                           ":" unq(qop)
            //                           ":" H(A2)
            //                  )
            //    A1       = H( unq(username) ":" unq(realm) ":" passwd )
            //                   ":" unq(nonce-prime) ":" unq(cnonce-prime)
            //    A2       = if (qop == "aut_int")
            //                  Method ":" request-uri ":" H(entity-body)
            //               else // (qop == "auth" or unspecified)
            //                  Method ":" request-uri
            // where:
            //
            //    H(data) = <algorithm>(data)
            //    KD(secret, data) = H(concat(secret, ":", data))
            //
            // and the "hash" value provided by the realm is the first part of A1:
            //
            //    H( unq(username) ":" unq(realm) ":" passwd )
            Hash pwdHash  = toHash($"{name}:{realm}:{password}"           , hasher);
            Hash hashA1   = toHash($"{toString(pwdHash)}:{nonce}:{cnonce}", hasher);
            Hash hashA2   = toHash($"{request.method.name}:{request.uri}" , hasher);
            Hash response = toHash($|{toString(hashA1)}:{nonce}:{ncText}\
                                    |:{cnonce}:{qop}:{toString(hashA2)}
                                                                          , hasher);
            return True, $|Digest username="{name}", realm="{realm}", uri="{request.uri}", \
                          |algorithm={algorithm}, nonce="{nonce}", nc={ncText}, cnonce="{cnonce}", \
                          |qop={qop}, response="{toString(response)}", opaque="{opaque}"
                          ;
        }
        return False;
    }


    // ----- helper classes ------------------------------------------------------------------------

    static const ResponseHeader
            implements Header {
        construct(String[] headerNames, String[] headerValues) {
            Int headerCount = headerNames.size;

            assert headerValues.size == headerCount;

            this.entries = new Entry[headerCount](i -> (headerNames[i], headerValues[i])).freeze(True);
        }

        @Override
        @RO Boolean isRequest.get() = False;

        @Override
        List<Entry> entries;

        @Override
        immutable ResponseHeader freeze(Boolean inPlace = False) {
            return this;
        }

        @Override
        Int estimateStringLength() {
            Int length = 0;
            for (Int i : 0 ..< entries.size) {
                Entry entry = entries[i];
                length += entry[0].size + entry[1].size + 2;
            }
            return length;
        }

        @Override
        Appender<Char> appendTo(Appender<Char> buf) {
            for (Int i : 0 ..< entries.size) {
                Entry entry = entries[i];

                entry[0].appendTo(buf).add('=');
                entry[1].appendTo(buf).add('\n');
            }
            return buf;
        }
    }

    static const Response(Registry registry, HttpStatus status, Header header, Byte[] bytes)
            implements ResponseIn
            implements Body {
        // ----- ResponseIn interface --------------------------------------------------------------

        @Override
        <Result> conditional Result to(Type<Result> type) {
            if (status == OK) {
                if (Codec<Result> codec := registry.findCodec(mediaType, Result)) {
                    try {
                        Result result = codec.decode(bytes);
                        return True, result;
                    } catch (Exception ignore) {}
                }
            }
            return False;
        }

        @Override
        @RO Body body.get() = this;

        @Override
        Body ensureBody(MediaType mediaType, Boolean streaming=False) {
            throw new ReadOnly();
        }

        // ----- Body interface --------------------------------------------------------------------

        @Override
        MediaType mediaType.get() {
            if (String    mediaTypeName := header.firstOf("Content-Type"),
                MediaType mediaType     := MediaType.of(mediaTypeName)) {
                return mediaType;
            }
            throw new IllegalState("MediaType is not supplied");
        }

        @Override
        Body from(Object content) {
            throw new Unsupported();
        }

        @Override
        String toString() {
            return bytes.size > 0 && (mediaType == Json || mediaType == Text)
                ? $"{status}: {body.bytes.unpackUtf8()}"
                : status.toString();
        }
    }
}