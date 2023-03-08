import codecs.Utf8Codec;
import codecs.Base64Format;

import crypto.Signer;

import ecstasy.collections.CaseInsensitive;

import Realm.Hash;
import Realm.UserId;

import responses.SimpleResponse;


/**
 * An implementation of the Authenticator interface for
 * [The 'Digest' HTTP Authentication Scheme](https://datatracker.ietf.org/doc/html/rfc7616).
 */
service DigestAuthenticator(Realm realm)
        implements Authenticator
    {
    assert()
        {
        Signer[] hashers = realm.hashers;
        assert !hashers.empty as $|The "{realm.name}" realm must specify at least one hashing\
                                  | algorithm
                                 ;
        for (Signer hasher : hashers)
            {
            switch (String name = hasher.algorithm.name)
                {
                case "MD5":
                case "SHA-256":
                case "SHA-512-256":
                    break;

                default:
                    assert as $|The "{realm.name}" realm specifies an unsupported hash algorithm:\
                               | {name}
                              ;
                }
            }
        }

    /**
     * The Realm that contains the user/password information
     */
    public/private Realm realm;


    // ----- Authenticator interface ---------------------------------------------------------------

    @Override
    Boolean|ResponseOut authenticate(RequestIn request, Session session, Endpoint endpoint)
        {
        // TLS is a pre-requisite for authentication in the Xenia design
        assert request.scheme.tls;

        Boolean stale = False;

        // first, check to see if the incoming request includes the necessary authentication
        // information, which will be in one or more "Authorization" header entries
        NextAuthAttempt: for (String auth : request.header.valuesOf("Authorization"))
            {
            auth = auth.trim();
            if (CaseInsensitive.stringStartsWith(auth, "Digest "))
                {
                if ((UserId userId,
                     Hash   responseHash,
                     Signer hasher,
                     String opaque,
                     String nonce,
                     String uri,
                     String cnonce,
                     String ncText,
                     Int    nc          ) := parseDigest(auth.substring(7)))
                    {
                    // verify that the server nonce is still acceptable; the client nonce and its
                    // count isn't checked here, since the communication is over TLS, and any
                    // noteworthy changes to the session or the connection will automatically make
                    // the server nonce stale
                    switch (lookupNonce(session, nonce))
                        {
                        case Unknown:
                            continue NextAuthAttempt;

                        case Stale:
                            stale = True;
                            continue NextAuthAttempt;

                        case Valid:
                            break;
                        }

                    Hash[]  hashes  = realm.hashesFor(userId, hasher);
                    String? badUser = Null;
                    for (Hash pwdHash : hashes)
                        {
                        String user;
                        if (userId.is(String))
                            {
                            user      = userId;
                            badUser ?:= user;
                            }
                        // obtain the plain text user name (which we need to reproduce the hash) by
                        // "validating" the password hash that we just got from the realm when we
                        // looked up the hashed user id; in other words,  this is not "validating"
                        // anything; it's just looking up a plain text user name
                        else if (!(user := realm.validateHash(userId, pwdHash, hasher)))
                            {
                            // somehow, when we went to look up the user name for the password hash
                            // that the realm just gave us, the user hash/password hash combination
                            // disappeared; in theory, someone could have just changed the password
                            // for that user or something similar, so just pretend that we didn't
                            // even know about that user hash/password hash combo
                            continue;
                            }

                        // create what a response digest would look like, using the hashed password
                        // and other information that we parsed from the digest auth:
                        //
                        //    response = KD ( H(A1), unq(nonce)
                        //                           ":" nc
                        //                           ":" unq(cnonce)
                        //                           ":" unq(qop)
                        //                           ":" H(A2)
                        //                  )
                        //    A1       = H( unq(username) ":" unq(realm) ":" passwd )
                        //                   ":" unq(nonce-prime) ":" unq(cnonce-prime)
                        //    A2       = Method ":" request-uri
                        //
                        // where:
                        //
                        //    H(data) = <algorithm>(data)
                        //    KD(secret, data) = H(concat(secret, ":", data))
                        //
                        // and the "hash" value provided by the realm is the first part of A1:
                        //
                        //    H( unq(username) ":" unq(realm) ":" passwd )
                        Hash hashA1   = toHash($"{toString(pwdHash)}:{nonce}:{cnonce}", hasher);
                        Hash hashA2   = toHash($"{request.method.name}:{uri}"         , hasher);
                        Hash expected = toHash($|{toString(hashA1)}:{nonce}:{ncText}\
                                                |:{cnonce}:auth:{toString(hashA2)}
                                                                                      , hasher);
                        if (responseHash == expected)
                            {
                            session.authenticate(user);
                            return True;
                            }
                        }

                    if (session.authenticationFailed(badUser))
                        {
                        return False;
                        }
                    }
                else
                    {
                    return new SimpleResponse(BadRequest).freeze(inPlace=True);
                    }
                }
            }

        // to cause the client to request the user for a name and password, we need to return an
        // "Unauthorized" error code with a header that directs the client to use Digest auth
        ResponseOut response = new SimpleResponse(Unauthorized);
        String nonce  = createNonce(session);
        for (Signer hasher : realm.hashers)
            {
            response.header.put("WWW-Authenticate", $|Digest realm="{realm.name}",\
                                                     |qop="auth",\
                                                     |algorithm={hasher.algorithm.name}-sess,\
                                                     |nonce="{nonce}",\
                                                     |opaque="BeKindToOthers",\
                                                     |charset=UTF-8\
                                                     |{stale ? ",stale=true" : ""}
                               );
            }
        return response.freeze(inPlace=True);
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * A key to store nonce data on the session. The data are stored in a Map whose key is the
     * String nonce, and whose value is the time at which the nonce was created.
     */
    protected static String ActiveNonces = "DigestAuthenticator.nonces";

    /**
     * Nonces are stored in a table keyed by the nonce string, with a corresponding value being the
     * point in time that the nonce was created.
     */
    protected typedef ListMap<String, Time> as NonceTable;

    /**
     * Create and register an authentication nonce on the current session.
     *
     * @param session  the current session
     *
     * @return the new nonce
     */
    protected String createNonce(Session session)
        {
        return session.attributes.process(ActiveNonces, entry ->
            {
            @Inject Random rnd;
            String nonce = Base64Format.Instance.encode(rnd.fill(new Byte[9]));

            NonceTable nonces;
            if (entry.exists, nonces := entry.value.is(NonceTable))
                {
                while (nonces.size >= 20)
                    {
                    nonces = nonces.remove(nonces.keys.iterator().take());
                    }
                }
            else
                {
                nonces = new NonceTable(20);
                }

            @Inject Clock clock;
            entry.value = nonces.put(nonce, clock.now);

            return nonce;
            });
        }

    /**
     * The current status of a previously created nonce:
     *
     * * `Unknown` -- the nonce is not recognized (i.e. it is not remembered)
     * * `Stale` -- the nonce is recognized, but cannot be used because session events have occurred
     *   in the period of time since the nonce was handed out for use
     * * `Valid` -- the nonce is recognized, and is valid for use
     */
    protected enum NonceStatus {Unknown, Stale, Valid}

    /**
     * Look up a previously created nonce and check its status.
     *
     * @param session  the current session
     * @param nonce    the previously created nonce
     *
     * @return the nonce status
     */
    protected NonceStatus lookupNonce(Session session, String nonce)
        {
        return session.attributes.process(ActiveNonces, entry ->
            {
            if (entry.exists, NonceTable nonces := entry.value.is(NonceTable),
                    Time nonceCreated := nonces.get(nonce))
                {
                return session.anyEventsSince(nonceCreated)
                        ? Stale
                        : Valid;
                }
            return Unknown;
            });
        }

    /**
     * Parse the text that follows "Digest " in the "Authorization" header.
     *
     * Examples from the
     * ['Digest' HTTP Authentication Scheme](https://datatracker.ietf.org/doc/html/rfc7616):
     *
     *     Authorization: Digest username="Mufasa",
     *         realm="http-auth@example.org",
     *         uri="/dir/index.html",
     *         algorithm=MD5,
     *         nonce="7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v",
     *         nc=00000001,
     *         cnonce="f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ",
     *         qop=auth,
     *         response="8ca523f5e9506fed4657c9700eebdbec",
     *         opaque="FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS"
     *
     *     Authorization: Digest username="Mufasa",
     *         realm="http-auth@example.org",
     *         uri="/dir/index.html",
     *         algorithm=SHA-256,
     *         nonce="7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v",
     *         nc=00000001,
     *         cnonce="f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ",
     *         qop=auth,
     *         response="753927fa0e85d155564e2e272a28d1802ca10daf449
     *            6794697cf8db5856cb6c1",
     *         opaque="FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS"
     *
     *     Authorization: Digest
     *         username="488869477bf257147b804c45308cd62ac4e25eb717
     *            b12b298c79e62dcea254ec",
     *         realm="api@example.org",
     *         uri="/doe.json",
     *         algorithm=SHA-512-256,
     *         nonce="5TsQWLVdgBdmrQ0XsxbDODV+57QdFR34I9HAbC/RVvkK",
     *         nc=00000001,
     *         cnonce="NTg6RKcb9boFIAS3KrFK9BGeh+iDa/sm6jUMp2wds69v",
     *         qop=auth,
     *         response="ae66e67d6b427bd3f120414a82e4acff38e8ecd9101d
     *            6c861229025f607a79dd",
     *         opaque="HRPCssKJSGjCrkzDg8OhwpzCiGPChXYjwrI2QmXDnsOS",
     *         userhash=true
     *
     *     Authorization: Digest
     *         username*=UTF-8''J%C3%A4s%C3%B8n%20Doe,
     *         realm="api@example.org",
     *         uri="/doe.json",
     *         algorithm=SHA-512-256,
     *         nonce="5TsQWLVdgBdmrQ0XsxbDODV+57QdFR34I9HAbC/RVvkK",
     *         nc=00000001,
     *         cnonce="NTg6RKcb9boFIAS3KrFK9BGeh+iDa/sm6jUMp2wds69v",
     *         qop=auth,
     *         response="ae66e67d6b427bd3f120414a82e4acff38e8ecd9101d
     *            6c861229025f607a79dd",
     *         opaque="HRPCssKJSGjCrkzDg8OhwpzCiGPChXYjwrI2QmXDnsOS",
     *         userhash=false
     *
     * @param text  the text that follows "Digest " in the "Authorization" header
     *
     * @return `True` iff the parse was successful; `False` indicates a `BadRequest` error
     * @return (conditional) userId - the user name in plain text, or the user hash
     * @return (conditional) responseHash - the response digest that includes password proof
     * @return (conditional) hasher - the [hasher](Signer) to use
     * @return (conditional) opaque - the opaque string previously sent by this authenticator
     * @return (conditional) nonce - the nonce string previous sent by this authenticator
     * @return (conditional) uri - the URI being accessed by the client
     * @return (conditional) cnonce - the nonce created by the client
     * @return (conditional) ncText - the `nc` value before it is parsed
     * @return (conditional) nc - the number of times, including this time, that the client nonce
     *         has been sent in a response
     */
    conditional (UserId userId,
                 Hash   responseHash,
                 Signer hasher,
                 String opaque,
                 String nonce,
                 String uri,
                 String cnonce,
                 String ncText,
                 Int    nc          ) parseDigest(String text)
        {
        val props = text.trim().splitMap();

        // realm name is optional, but it should match the name previously provided
        if (String realmName := props.get("realm"))
            {
            if (realmName := unquote(realmName), realmName == realm.name)
                {
                // realm name matches
                }
            else
                {
                return False;
                }
            }

        // qop is required, and only one qop is supported ("auth")
        if (String qop := props.get("qop"), qop == "auth" || qop == "\"auth\"")
            {
            // qop matches
            }
        else
            {
            return False;
            }

        // the things that need to be parsed and returned
        UserId userId;
        Hash   responseHash;
        Signer hasher;
        String opaque;
        String nonce;
        String uri;
        String cnonce;
        Int    nc = 0;

        // "username", "username*", "userhash": the user name -- possibly hashed, or possibly
        // encoded differently in a similarly named key with a star on the end, because why TF
        // not? -- is required
        Boolean userHashed = False;
        if (String userhash := props.get("userhash")) // optional
            {
            if (userhash == "true")
                {
                userHashed = True;
                }
            else if (userhash != "false")
                {
                return False;
                }
            }

        if (String username := props.get("username"))
            {
            // the presence of both "username" and "username*" is illegal
            if (props.contains("username*"))
                {
                return False;
                }

            if (!(userId := unquote(username)))
                {
                return False;
                }
            }
        else if (username := props.get("username*"))
            {
            // user hashing is not compatible with the use of the "username*" MIME parameter
            if (userHashed)
                {
                return False;
                }

            if (!(userId := decodeUtf8MimeHeader(username)))
                {
                return False;
                }
            }
        else
            {
            return False;
            }

        // a helper function to grab required header properties
        static conditional String require(Map<String, String> props, String name, Boolean? quoted)
            {
            if (String val := props.get(name))
                {
                return quoted?
                        ? unquote(val)
                        : (True, val);

                val := unquote(val);
                return True, val;
                }
            return False;
            }

        // a few more required pieces of information from the header properties
        String algorithm;
        String response;
        String ncHex;

        if (algorithm := require(props, "algorithm", Null ),
            opaque    := require(props, "opaque"   , True ),
            nonce     := require(props, "nonce"    , True ),
            uri       := require(props, "uri"      , True ),
            cnonce    := require(props, "cnonce"   , True ),
            ncHex     := require(props, "nc"       , False),
            response  := require(props, "response" , True ))
            {
            // all required props found
            }
        else
            {
            return False;
            }

        // figure out which hashing algorithm (Signer) to use
        static String Suffix = "-sess";
        FindHasher: if (algorithm.endsWith(Suffix))
            {
            algorithm = algorithm[0 ..< algorithm.size-Suffix.size];
            for (hasher : realm.hashers)
                {
                if (hasher.algorithm.name == algorithm)
                    {
                    break FindHasher;
                    }
                }
            return False;
            }
        else
            {
            return False;
            }

        // the nc value must be 8 hex chars (because why TF not?); they get turned into an Int
        if (ncHex.size != 8)
            {
            return False;
            }
        for (Char ch : ncHex)
            {
            if (Nibble n := ch.isNibble())
                {
                nc = nc << 4 | n;
                }
            else
                {
                return False;
                }
            }


        // response (which contains the password information) is required
        // the response is a hex string
        if (response.size & 1 == 1)
            {
            // weird, but the spec of course doesn't have an opinion on whether the size should be
            // evenly divisible by 2, so it's probably worth right justifying it
            response = $"0{response}";
            }
        Byte[] hash = new Byte[](response.size / 2);
        for (Int offset = 0, Int length = response.size; offset < length; offset += 2)
            {
            if (Nibble n0 := response[offset].isNibble(), Nibble n1 := response[offset+1].isNibble())
                {
                hash += n0.toByte() << 4 | n1.toByte();
                }
            else
                {
                return False;
                }
            }
        responseHash = hash.freeze(inPlace=True);

        return True, userId, responseHash, hasher, opaque, nonce, uri, cnonce, ncHex, nc;
        }

    /**
     * Remove the required quotes from a String, and return the contents
     *
     * @param s  a String that must be quoted with double quotes
     *
     * @return `True` iff the passed String started and ended with double quotes
     * @return (conditional) the contents of the passed quoted String
     */
    static conditional String unquote(String s)
        {
        if (s.size >= 2 && s.startsWith('"') && s.endsWith('"'))
            {
            return True, s[0 >..< s.size-1];
            }

        return False;
        }

    /**
     * Decode a MIME header encoded using UTF-8.
     *
     * The format is defined by [the standard](https://datatracker.ietf.org/doc/html/rfc5987) known
     * as "Character Set and Language Encoding for Hypertext Transfer Protocol (HTTP) Header Field
     * Parameters"
     *
     * @param s  a String in the format defined by
     *           [RFC 5987](https://datatracker.ietf.org/doc/html/rfc5987)
     *
     * @return `True` iff the String contents were successfull decoded
     * @return (conditional) the decoded String contents
     */
    static conditional String decodeUtf8MimeHeader(String text)
        {
        // helper function to turn regular chars into ASCII bytes
        static conditional Byte isAttrChar(Char ch)
            {
            return switch (ch)
                {
                case 'A'..'Z', 'a'..'z', '0'..'9':
                case '!', '#', '$', '&', '+', '-', '.':
                case '^', '_', '`', '|', '~':
                    (True, ch.toByte());

                default: False;
                };
            }

        // helper function to turn "percent encoded" sequences into ASCII bytes
        static conditional (Byte, Int) decodePctEncoded(String s, Int offset)
            {
            Int length = s.size;
            if (offset + 2 > length)
                {
                return False;
                }

            if (Nibble n0 := s[offset++].isNibble(), Nibble n1 := s[offset++].isNibble())
                {
                return True, n0.toByte() << 4 | n1.toByte(), offset;
                }

            return False;
            }

        static String Prefix = "UTF-8'";
        if (!text.startsWith(Prefix))
            {
            return False;
            }

        Int offset;
        if (offset := text.indexOf('\'', Prefix.size))
            {
            ++offset;
            }
        else
            {
            return False;
            }

        Int    length = text.size;
        Byte[] bytes  = new Byte[](length-offset);
        while (offset < length)
            {
            Char ch = text[offset++];
            if (Byte b := isAttrChar(ch))
                {
                bytes += b;
                }
            else if (ch == '%', (b, offset) := decodePctEncoded(text, offset))
                {
                bytes += b;
                }
            else
                {
                return False;
                }
            }

        try
            {
            return True, bytes.unpackUtf8();
            }
        catch (Exception e) {}

        return False;
        }

    /**
     * Hash to hash-string conversion. Hashes are often converted to strings of lowercase hexits,
     * without the leading "0x", so that they can be concatenated with other strings, so that those
     * strings can be hashed, etc.
     *
     * @param hash  the hash
     *
     * @return the hash string
     */
    static String toString(Hash hash)
        {
        StringBuffer buf = new StringBuffer(hash.size * 2);
        return hash.appendTo(buf, pre="").toString().toLowercase();
        }

    /**
     * String to hash calculation.
     *
     * @param s       the String to hash
     * @param hasher  the [hasher](Signer) to use
     *
     * @return the hash
     */
    static Hash toHash(String s, Signer hasher)
        {
        return hasher.sign(s.utf8()).bytes;
        }
    }