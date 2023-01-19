/**
 * The Ecstasy standard module for basic cryptographic algorithm support.
 */
module crypto.xtclang.org
    {
    import ecstasy.reflect.Annotation;

    /**
     * There are cases in which annotations are permitted to be added to a returned stream, by
     * various methods creating and returning a stream. The annotations to be added can be specified
     * by passing a single annotation, or an array of annotations.
     */
    typedef Annotation[] | Annotation as Annotations;

    /**
     * Describes the type of key that an algorithm requires:
     *
     * * `Secret` - a "symmetric" key or a private key, which must be carefully protected;
     * * `Public` - a public key only, with the private key portion of the "public/private key pair"
     *   unavailable within the current context;
     * * `Pair` - both the public key and the corresponding private key, which must be carefully
     *   protected.
     */
    enum KeyForm {Secret, Public, Pair}
    }