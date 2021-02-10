import oodb.DBObject;

/**
 * An implementation of the DBObject interface for the various emulated system objects in the JSON
 * DB implementation.
 */
class SystemObject
        implements DBObject
    {
    /**
     * Construct a SystemObject.
     *
     * @param catalog   the containing Catalog
     * @param dbParent  the containing DBOject
     * @param dbName    the simple name of this DBObject
     */
    construct(Catalog catalog, DBObject? dbParent, String dbName)
        {
        // TODO GG OR CP BUGBUG: assert dbParent == Null ^ dbName != "";  // NPE (note: should have been "^^")
        assert dbParent == Null ^^ dbName != "";

        this.catalog  = catalog;
        this.dbParent = dbParent;
        this.dbName   = dbName;
        // TODO GG this.dbPath   = dbParent?.dbPath + "/" + dbName : dbName;
        this.dbPath   = dbParent == Null ? dbName : (dbParent.as(DBObject).dbPath + "/" + dbName);
        }

    /**
     * The Catalog for the database.
     */
    protected/private Catalog catalog;

    @Override public/protected DBObject? dbParent;

    @Override public/protected String dbName;

    @Override public/protected String dbPath;

    @Override Map<String, DBObject!> dbChildren.get()
        {
        return Map:[];
        }

    @Override Boolean transactional.get()
        {
        return False;
        }
    }