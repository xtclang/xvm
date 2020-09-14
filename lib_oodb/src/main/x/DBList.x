/**
 * The database interface for a list of database keys or values of a specific type, based on the
 * standard `List` interface.
 *
 * A `DBList` is always transactional.
 */
interface DBList<Element>
        extends List<Element>
        extends DBObject
    {
    @Override
    @RO Boolean transactional.get()
        {
        return True;
        }
    }
