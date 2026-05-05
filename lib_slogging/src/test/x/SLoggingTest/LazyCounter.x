/**
 * Service-backed counter for `Attr.lazy` tests. `Attr` is a `const`, so the supplier
 * carrier is frozen; a service reference is passable while a mutable local counter is not.
 */
service LazyCounter {
    public/private Int calls = 0;

    String value(String result) {
        ++calls;
        return result;
    }
}
