import convert.formats.Base64Format;

import crypto.Signer;

import ecstasy.collections.CaseInsensitive;

import sec.Credential;
import sec.Entity;
import sec.NonceManager;
import sec.Principal;

import DigestCredential.Hash;
import DigestCredential.md5;
import DigestCredential.sha256;
import DigestCredential.sha512_256;
import DigestCredential.UserId;


/**
 * An implementation of the Authenticator interface for
 * [The 'Digest' HTTP Authentication Scheme](https://datatracker.ietf.org/doc/html/rfc7616).
 */
@Concurrent
service DigestAuthenticator
        implements Authenticator {

    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a `DigestAuthenticator`.
     *
     * @param realm               the [Realm] that this `DigestAuthenticator` uses to verify
     *                            [Principal]s and [Credential]s
     * @param disallowAlgorithms  (optional) an array of algorithm names to explicitly disallow the
     *                            use of for digest authentication; the contents, if specified, can
     *                            be some combination of "MD5", "SHA-256", and "SHA-512-256"
     */
    construct(Realm realm, String[] disallowAlgorithms = []) {
        Signer[] hashers = [md5, sha256, sha512_256];
        if (!disallowAlgorithms.empty) {
            for (String name : disallowAlgorithms) {
                hashers = hashers - switch (name.toUppercase()) {
                    case "MD5": md5;
                    case "SHA-256": sha256;
                    case "SHA-512-256": sha512_256;
                    default: assert as $"Unknown/unsupported hash algorithm: {name.quoted()}";
                };
            }
            assert !hashers.empty;
        }

        this.realm   = realm;
        this.hashers = hashers;
        this.nonces  = new NonceManager(Duration:5m);
    }

    @Override
    construct(DigestAuthenticator that) {
        Realm thatRealm = that.realm;
        this.realm   = thatRealm.is(Duplicable) ? thatRealm.duplicate() : thatRealm;
        this.hashers = that.hashers;
        this.nonces  = that.nonces;
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The Realm that contains the user/password information.
     */
    @Override
    public/protected Realm realm;

    /**
     * The [Signer]s (hash algorithms) used by this `DigestAuthenticator`.
     */
    Signer[] hashers;

    /**
     * A `NonceManager` whose job it is to generate and ensure at most a single use of each nonce.
     */
    protected/private NonceManager nonces;

    // ----- Authenticator interface ---------------------------------------------------------------

    @Override
    DigestAttempt[] findAndRevokeSecrets(RequestIn request) {
        // scan for and cancel all nonces
        for (String auth : request.header.valuesOf("Authorization")) {
            auth = auth.trim();
            if (CaseInsensitive.stringStartsWith(auth, "Digest "),
                    (String? realmName, _, _, _, _, _, String nonceText) := parseDigest(auth.substring(7)),
                    realmName == Null || realmName == realm.name,
                    Int nonce := Int.parse(nonceText, 16)) {
                // validate the nonce to kill it (it's only valid once!)
                nonces.validate(nonce);
            }
        }

        // no plain text secrets
        return [];
    }

    @Override
    DigestAttempt[] authenticate(RequestIn request) {
        static DigestAttempt Corrupt = new DigestAttempt(Null, Failed);

        // TLS is a pre-requisite for authentication
        assert request.scheme.tls;

        private String[] challenges(RequestIn request, Boolean stale) {
            String   nonce  = toString(nonces.generate());
            String[] result = new String[];

            // the Safari browser does not implement the spec correctly; if you give it an option to
            // use anything other than MD5, it cannot proceed with any of the challenge options
            Signer[] hashers = (request.userAgent?.indexOf("Safari") : False)
                    ? [md5]
                    : this.hashers;

            for (Signer hasher : hashers) {
                result += $|Digest realm="{realm.name}",\
                           |qop="auth",\
                           |algorithm={hasher.algorithm.name}-sess,\
                           |nonce="{nonce}",\
                           |opaque="BeKindToOthers",\
                           |charset=UTF-8\
                           |{stale ? ",stale=true" : ""}
                          ;
            }
            return result.freeze(inPlace=True);
        }

        // first, check to see if the incoming request includes the necessary authentication
        // information, which will be in one or more "Authorization" header entries
        DigestAttempt[] attempts = [];
        Boolean         stale    = False;
        Boolean         passed   = False;
        NextAuthAttempt: for (String auth : request.header.valuesOf("Authorization")) {
            auth = auth.trim();
            if (CaseInsensitive.stringStartsWith(auth, "Digest ")) {
                if ((   String?  realmName,
                        String[] qop,
                        UserId   userId,
                        Hash     responseHash,
                        Signer   hasher,
                        String   opaque,
                        String   nonce,
                        String   uri,
                        String   cnonce,
                        String   ncText,
                        Int      nc,
                        ) := parseDigest(auth.substring(7))) {

                    // realm is optional, but if it is present, it must match
                    if (!qop.contains("auth") || realmName? != realm.name) {
                        continue NextAuthAttempt;
                    }

                    // verify that the server nonce is still acceptable; the client nonce and its
                    // count isn't checked here, since the communication is over TLS
                    Int nonceValue;
                    if (!(nonceValue := Int.parse(nonce, 16))) {
                        attempts += Corrupt;
                        continue NextAuthAttempt;
                    }
                    if (!nonces.validate(nonceValue)) {
                        stale = True;
                        continue NextAuthAttempt;
                    }

                    // the userId is either a name or a hash; use it as a Principal "locator"
                    String locator = userId.is(String) ? userId.quoted() : userId.toString(pre="");
                    if (Principal principal := realm.findPrincipal(DigestCredential.Scheme, locator)) {
                        Authenticator.Status status = principal.calcStatus(realm) == Active ? Success : NotActive;

                        // validate the credential
                        DigestCredential? failure = Null;
                        for (Credential credential : principal.credentials) {
                            // for credentials that match the name/user-hash, there are three
                            // outcomes: (1) revoked, (2) bad password hash, (3) all good!
                            if (credential.scheme == DigestCredential.Scheme
                                    && credential.is(DigestCredential)
                                    && credential.isUser(userId)
                                    && credential.active) {

                                // create what a response digest would look like, using the hashed
                                // password and other information that we parsed from the digest
                                // auth:
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

                                Hash pwdHash;
                                if (!(pwdHash := credential.findPasswordHash(hasher))) {
                                    failure ?:= credential;
                                    continue;
                                }
                                Hash hashA1   = toHash($"{toString(pwdHash)}:{nonce}:{cnonce}", hasher);
                                Hash hashA2   = toHash($"{request.method.name}:{uri}"         , hasher);
                                Hash expected = toHash($|{toString(hashA1)}:{nonce}:{ncText}\
                                                        |:{cnonce}:auth:{toString(hashA2)}
                                                                                              , hasher);
                                if (responseHash == expected) {
                                    attempts += new DigestAttempt(principal, status, Null, credential);
                                    passed    = True;
                                    continue NextAuthAttempt;
                                } else {
                                    failure ?:= credential;
                                }
                            }
                        }

                        // none of the credentials matched
                        attempts += new DigestAttempt(principal, Failed, challenges(request, stale), failure);
                    } else {
                        // no such user
                        attempts += new DigestAttempt(locator, Failed, challenges(request, stale));
                    }
                } else {
                    attempts += Corrupt;
                }
            }
        }

        if (attempts.empty) {
            // to cause the client to request the user for a name and password, we need to return an
            // "Unauthorized" error code with a header that directs the client to use Digest auth
            attempts = [new DigestAttempt(Null, NoData, challenges(request, stale))];
        }

        return attempts;
    }

    // ----- internal ------------------------------------------------------------------------------

    static const DigestAttempt(Claim? claim, Status status, AuthResponse? response = Null,
                              DigestCredential? credential = Null)
            extends Attempt(claim, status, response);

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
     * @return (conditional) realmName - the realm name, iff specified
     * @return (conditional) qop - a list of quality-of-protection options e.g. "auth", "auth-int"
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
    conditional (String?  realmName,
                 String[] qop,
                 UserId   userId,
                 Hash     responseHash,
                 Signer   hasher,
                 String   opaque,
                 String   nonce,
                 String   uri,
                 String   cnonce,
                 String   ncText,
                 Int      nc,
                ) parseDigest(String text) {

        val props = text.trim().splitMap(valueQuote=ch->ch=='\"');

        // realm name is optional, but if present, it must match the name previously provided
        String? realmName = Null;
        if (realmName := props.get("realm")) {
            if (realmName == realm.name) {
                // realm name matches
            } else {
                return False;
            }
        }

        // qop is required
        String[] qop;
        if (String qopList := props.get("qop")) {
            qop = qopList.split(',', trim=True);
        } else {
            return False;
        }

        // "username", "username*", "userhash": the user name -- possibly hashed, or possibly
        // encoded differently in a similarly named key with a star on the end, because why TF
        // not? -- is required
        Boolean userHashed = False;
        if (String userhash := props.get("userhash")) { // optional
            if (userhash == "true") {
                userHashed = True;
            } else if (userhash != "false") {
                return False;
            }
        }

        UserId userId;
        if (String username := props.get("username")) {
            // the presence of both "username" and "username*" is illegal
            if (props.contains("username*")) {
                return False;
            }

            userId = username;
        } else if (String username := props.get("username*")) {
            // user hashing is not compatible with the use of the "username*" MIME parameter
            if (userHashed) {
                return False;
            }

            if (!(userId := decodeUtf8MimeHeader(username))) {
                return False;
            }
        } else {
            return False;
        }

        String algorithm;
        String opaque;
        String nonce;
        String uri;
        String cnonce;
        String ncHex;
        String response;
        if (algorithm := props.get("algorithm"),
            opaque    := props.get("opaque"),
            nonce     := props.get("nonce"),
            uri       := props.get("uri"),
            cnonce    := props.get("cnonce"),
            ncHex     := props.get("nc"),
            response  := props.get("response")) {
            // all required props found
        } else {
            return False;
        }

        // figure out which hashing algorithm (Signer) to use
        static String Suffix = "-sess";
        Signer hasher;
        FindHasher: if (algorithm.endsWith(Suffix)) {
            algorithm = algorithm[0 ..< algorithm.size-Suffix.size];
            if (!(hasher := hashers.any(h -> h.algorithm.name == algorithm))) {
                return False;
            }
        } else {
            return False;
        }

        // the nc value must be 8 hex chars (because why TF not?); they get turned into an Int
        if (ncHex.size != 8) {
            return False;
        }
        Int nc = 0;
        for (Char ch : ncHex) {
            if (Nibble n := ch.isNibble()) {
                nc = nc << 4 | n;
            } else {
                return False;
            }
        }

        // response (which contains the password information) is required
        // the response is a hex string
        if (response.size & 1 == 1) {
            // weird, but the spec of course doesn't have an opinion on whether the size should be
            // evenly divisible by 2, so it's probably worth right justifying it
            response = $"0{response}";
        }
        Byte[] hash = new Byte[](response.size / 2);
        for (Int offset = 0, Int length = response.size; offset < length; offset += 2) {
            if (Nibble n0 := response[offset].isNibble(), Nibble n1 := response[offset+1].isNibble()) {
                hash += n0.toByte() << 4 | n1.toByte();
            } else {
                return False;
            }
        }
        Hash responseHash = hash.freeze(inPlace=True);

        return True, realmName, qop, userId, responseHash, hasher, opaque, nonce, uri, cnonce, ncHex, nc;
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
     * @return `True` iff the String contents were successfully decoded
     * @return (conditional) the decoded String contents
     */
    static conditional String decodeUtf8MimeHeader(String text) {
        // helper function to turn regular chars into ASCII bytes
        static conditional Byte isAttrChar(Char ch) {
            return switch (ch) {
                case 'A'..'Z', 'a'..'z', '0'..'9':
                case '!', '#', '$', '&', '+', '-', '.':
                case '^', '_', '`', '|', '~':
                    (True, ch.toByte());

                default: False;
            };
        }

        // helper function to turn "percent encoded" sequences into ASCII bytes
        static conditional (Byte, Int) decodePctEncoded(String s, Int offset) {
            Int length = s.size;
            if (offset + 2 > length) {
                return False;
            }

            if (Nibble n0 := s[offset++].isNibble(), Nibble n1 := s[offset++].isNibble()) {
                return True, n0.toByte() << 4 | n1.toByte(), offset;
            }

            return False;
        }

        static String Prefix = "UTF-8'";
        if (!text.startsWith(Prefix)) {
            return False;
        }

        Int offset;
        if (offset := text.indexOf('\'', Prefix.size)) {
            ++offset;
        } else {
            return False;
        }

        Int    length = text.size;
        Byte[] bytes  = new Byte[](length-offset);
        while (offset < length) {
            Char ch = text[offset++];
            if (Byte b := isAttrChar(ch)) {
                bytes += b;
            } else if (ch == '%', (Byte b, offset) := decodePctEncoded(text, offset)) {
                bytes += b;
            } else {
                return False;
            }
        }

        try {
            return True, bytes.unpackUtf8();
        } catch (Exception e) {}

        return False;
    }

    /**
     * Hash to hash-string conversion. Hashes are often converted to strings of lowercase hexits,
     * without the leading "0x", so that they can be concatenated with other strings, so that those
     * strings can be hashed, etc.
     *
     * @param nonce  the nonce value
     *
     * @return the nonce string
     */
    static String toString(Int nonce) = toString(nonce.toByteArray(Constant).as(Hash));

    /**
     * Hash to hash-string conversion. Hashes are often converted to strings of lowercase hexits,
     * without the leading "0x", so that they can be concatenated with other strings, so that those
     * strings can be hashed, etc.
     *
     * @param hash  the hash
     *
     * @return the hash string
     */
    static String toString(Hash hash) {
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
    static Hash toHash(String s, Signer hasher) {
        return hasher.sign(s.utf8()).bytes;
    }
}