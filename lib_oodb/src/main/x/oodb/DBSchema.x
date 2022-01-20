/**
 * A `DBSchema` is a `DBObject` that is used to hierarchically organize database contents.
 */
interface DBSchema
        extends DBObject
    {
    // ----- DBObject methods ----------------------------------------------------------------------

    @Override
    @RO DBSchema!? dbParent;

    @Override
    @RO DBCategory dbCategory.get()
        {
        return DBSchema;
        }

    @Override
    @RO Boolean transactional.get()
        {
        return False;
        }
    }