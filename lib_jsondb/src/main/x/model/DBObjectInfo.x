/**
 * Metadata information about a particular `DBOBject`.
 */
const DBObjectInfo(Catalog  catalog,
                   Int      id,
                   Int?     parentId,
                   Int[]    childIds,
                   String   name,
                   String   path,
                   Category category)
    {
    /**
     * The service that provides metadata information.
     */
    protected/private Catalog catalog;

    /**
     * The id for the DBObject that this provides the information for.
     */
    Int id;

    /**
     * The metadata information for the parent of the `DBObject`.
     */
    @Lazy DBObjectInfo parent.calc()
        {
        TODO
        }

    /**
     * The metadata information for the children of the `DBObject`.
     */
    @Lazy DBObjectInfo[] children.calc()
        {
        TODO
        }

    /**
     * The name of the `DBObject`.
     */
    String name;

    /**
     * The "path" of the `DBObject`.
     */
    @Lazy String path.calc()
        {
        TODO
        }

    enum Category {Schema, Map, List, Queue, Log, Counter, Singleton}

    /**
     * The category of the `DBObject`.
     */
    Category category;
    }
