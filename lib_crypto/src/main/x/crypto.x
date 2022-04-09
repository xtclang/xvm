/**
 * The Ecstasy standard module for basic cryptographic algorithm support.
 */
module crypto.xtclang.org
    {
    /**
     * A cryptographic key is represented as a sequence of bytes.
     */
    typedef Byte[] Key;

    /**
     * There are cases in which annotations are permitted to be added to a returned stream, by
     * various methods creating and returning a stream. The annotations to be added can be specified
     * by passing a single annotation, or an array of annotations.
     */
    typedef Annotation[] | Annotation as Annotations;
    }
