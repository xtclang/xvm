import DBObject.DBCategory;
import DBObject.Validator;
import DBObject.Rectifier;
import DBObject.Distributor;

/**
 * Persistent metadata information about a particular `DBObject`.
 *
 * Each database object may specify one or more parameters, such as `Key` and `Value` for a
 * `DBMap`. These are type constraints, which is to say that values are constrained to be "at
 * least" of these types. However, since the information must be stored persistently, it is
 * necessary for the database to know (in advance) what the actual possible types are for each
 * of these constraints; the constraint types for each type parameter name are specified in the
 * [typeParams] property, and any additional class types that may occur are required to be
 * enumerated in the [concreteClasses] property.
 */
const DBObjectInfo(
        Path                          path,
        Path[]                        childPaths,
        DBCategory                    category,
        Boolean                       transactional   = True,
//        TypeParamInfo[]               typeParams      = [],
        Validator[]                   validators      = [],
        Rectifier[]                   rectifiers      = [],
        Distributor[]                 distributors    = [],
        LifeCycle                     lifeCycle       = Current,
        )
    {
    assert()
        {
        path = path.normalize();
        if (!path.absolute)
            {
            assert:arg path[0].form == Name;
            path = ROOT + path;
            }
        }

    /**
     * Represents the life cycle state of a DBObject in the database.
     */
    enum LifeCycle {Current, Deprecated, Removed}

    /**
     * Represents a type parameter on a DBObjects.
     *
     * @param name             the name of the type parameter
     * @param type             the constraint type of the type parameter
     * @param concreteClasses  (optional) the list of supported classes that are allowed to be used;
     *                         an empty list indicates that the set of supported classes is not
     *                         constrained
     */
    static const TypeParamInfo(
            String  name,
            Type    type,
            Class[] concreteClasses = [],
            );

    /**
     * The simple name of the DBObject.
     */
    @RO String name.get()
        {
        return path == ROOT ? "" : path.name;
        }

    /**
     * The path of the parent of the DBObject, or `Null` iff this is the info for the `RootSchema`.
     */
    @RO Path? parentPath.get()
        {
        return path.parent;
        }

    /**
     * Given a `Connection` or any other `DBObject`, look up the `DBObject` indicated by this
     * `DBObjectInfo` and return it.
     *
     * @param obj  a `Connection` or any other `DBObject` from the same database
     *
     * @return the DBObject indicated by this `DBObjectInfo`, or `Null` if it could not be obtained
     */
    DBObject? lookupUsing(DBObject obj)
        {
        return obj.dbRoot.dbObjectFor(path);
        }
    }