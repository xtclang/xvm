/**
 * A representation of a Uniform Resource Locator (URL) reference.
 *
 * @see: https://www.rfc-editor.org/rfc/rfc1738
 */
const Url
        extends Uri
        implements Destringable {
    // ----- constructors --------------------------------------------------------------------------
    @Override
    construct(String text, String defaultScheme = "http") {
        assert (String?    scheme,
                String?    authority,
                String?    user,
                String?    host,
                IPAddress? ip,
                UInt16?    port,
                String?    path,
                String?    query,
                String?    opaque,
                String?    fragment) := parse(text, s -> throw new IllegalArgument(s));

        if (scheme == Null) {
            assert !defaultScheme.empty;
            text = $"{defaultScheme}://{text}";
            assert (scheme, authority, user, host, ip, port, path, query, opaque, fragment) :=
                parse(text, s -> throw new IllegalArgument(s));
        }
        construct Uri(scheme, authority, user, host, ip, port, path, query, opaque, fragment);
    }
}


