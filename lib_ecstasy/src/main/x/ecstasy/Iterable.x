/**
 * The Iterable interface allows an object to expose its contents as a series of elements. The two
 * requirements are that the number of elements be known via the [size] property, and a
 * representation of the series of elements be provided through the [iterator()] method.
 *
 * Among other things, the Iterable interface is the basis for Ecstasy collections.
 */
interface Iterable<Element> {
    /**
     * Determine the size of the Iterable object, which is the number of elements that an iterator
     * would emit.
     */
    @RO Int size;

    /**
     * True iff the Iterable object contains no elements.
     */
    @RO Boolean empty.get() = size == 0;

    /**
     * Obtain an iterator over the contents of the Iterable object.
     *
     * @return an Iterator
     */
    Iterator<Element> iterator();

    /**
     * Obtain the contents of this iterable source as an indexed, stably-ordered [List], which is
     * used as the array type in Ecstasy. Generally, the [Array] class will be used to satisfy this
     * request.
     *
     * @param mutability  the requested [Mutability](Array.Mutability) of the resulting array, or
     *                    `Null` to allow the iterable source to produce a [List] of its choosing;
     *                    if mutability is not specified, then the resulting array is not guaranteed
     *                    to be a stable snapshot until the caller [reifies](Collection.reify()] the
     *                    result
     *
     * @return a `List` of elements from this iterable source
     */
    Element[] toArray(Array.Mutability? mutability = Null) {
        Element[] result = new Element[](size); // mutable

        loop: for (Element element : this) {
            result[loop.count] = element;
        }

        return result.toArray(mutability, True);
    }
}
