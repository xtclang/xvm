import oodb.DBValue;
import oodb.model.DBInfo;

/**
 * An implementation of the DBValue for DBInfo.
 */
class DBInfoSingleton
        extends SystemObject
        implements DBValue<DBInfo>
    {
    construct(SystemSchema:protected parent)
        {
        construct SystemObject(parent.catalog, parent, "info");
        }

    @Override
    DBInfo get()
        {
        // TODO can cache a lot of this information on the Catalog
        return new DBInfo(
                name     = catalog.dir.toString(),
                version  = catalog.version ?: assert,   // TODO review the assert
                created  = catalog.statusFile.created,
                modified = catalog.statusFile.modified, // TODO get timestamp from last tx
                accessed = catalog.dir.accessed.maxOf(catalog.statusFile.modified),
                readable = catalog.statusFile.readable,
                writable = catalog.statusFile.writable && !catalog.readOnly,
                size     = catalog.dir.size);
        }
    }