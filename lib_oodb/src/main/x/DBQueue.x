/**
 * The database interface for a queue of database keys or values of a specific type, based on the
 * standard `Queue` interface.
 *
 * A `DBQueue` is always transactional.
 */
interface DBQueue<Element>
        extends Queue<Element>
        extends DBObject
    {
    /**
     * Obtain all of the contents of the `Queue` as a `List`. This method is potentially an
     * incredibly expensive operation, so it should only be used if the size of the `Queue` is
     * known to be small and there are no other processes concurrently operating on the Queue.
     *
     * This is roughly the equivalent of:
     *
     *     List<Element> list = new Element[];
     *     while (Element e := next())
     *         {
     *         list.add(e);
     *         }
     *     return list;
     *
     * @return a `List` with all of the elements in the `Queue`
     */
    List<Element> takeAll();

    @Override
    @RO Boolean transactional.get()
        {
        return True;
        }

    /**
     * Represents a change to a database queue.
     *
     * The contents of the "pre" and "post" queues may not be available, or may be extremely
     * expensive to provide to the caller.
     */
    @Override
    interface Change
        {
        /**
         * The elements appended to the `Queue`.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently added within the transaction _may_ appear in the list.
         */
        List<Element> added;

        /**
         * The elements taken from the `Queue`.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently taken within the transaction _may_ appear in the list.
         */
        List<Element> removed;
        }
    }
