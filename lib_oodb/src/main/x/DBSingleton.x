/**
 * A database singleton object is a _containerless_ `DBObject` in the database; in other words, it
 * is not held in a `DBList` or a `DBMap`, or any other container. Instead, the name dereferences to
 * the object itself, which must be an immutable Const
 */
interface DBSingleton
        extends DBObject
        extends immutable Const
    {
    }
