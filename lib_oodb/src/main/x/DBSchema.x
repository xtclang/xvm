/**
 * A `DBSchema` is a `DBObject` that is used to hierarchically organize database contents.
 */
interface DBSchema
        extends DBObject
    {
    @Override
    @RO Boolean transactional.get()
        {
        return False;
        }
    }