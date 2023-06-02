import DBObject.DBCategory;
import DBObject.Validator;
import DBObject.Rectifier;
import DBObject.Distributor;

/**
 * Metadata information about a particular `DBObject`.
 */
interface DBObjectInfo
        extends immutable Const {
    /**
     * Represents the life cycle state of a DBObject in the database.
     */
    enum LifeCycle {Current, Deprecated, Removed}

    /**
     * Represents a type parameter on a DBObjects.
     *
     * Each database object may specify one or more parameters, such as `Key` and `Value` for a
     * `DBMap`. These are type constraints, which is to say that values are constrained to be "at
     * least" of these types. However, since data is being stored persistently, it is often
     * necessary for a database to know (in advance) what the actual possible classes are for each
     * of these type constraints.
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
    @RO String name.get() {
        return path == ROOT ? "" : path.name;
    }

    /**
     * The absolute path of the DBObject within the database.
     */
    Path path;

    /**
     * The path of the parent of the DBObject, or `Null` iff this is the info for the `RootSchema`.
     */
    @RO Path? parentPath.get() {
        return path.parent;
    }

    /**
     * The names of the child DBObjects, if any.
     */
    String[] childNames;

    /**
     * The paths of the child DBObjects, if any.
     */
    @RO Path[] childPaths.get() {
        return new Path[childNames.size](s -> path + name).freeze(inPlace=True);
    }

    /**
     * The category of the DBObject.
     */
    DBCategory category;

    /**
     * The type parameters of the DBObject, such as `Key` and `Value` for a `DBMap`.
     */
    TypeParamInfo[] typeParams;

    /**
     * True iff the DBObject is transactional.
     */
    Boolean transactional;

    /**
     * An array of transactional [Validators](Validator) for the DBObject; may be empty.
     */
    Validator[] validators;

    /**
     * An array of transactional [Rectifiers](Rectifier) for the DBObject; may be empty.
     */
    Rectifier[] rectifiers;

    /**
     * An array of transactional [Distributors](Distributor) for the DBObject; may be empty.
     */
    Distributor[] distributors;

    /**
     * The current [LifeCycle] status of the DBObject.
     */
    LifeCycle lifeCycle;

    /**
     * Given a `Connection` or any other `DBObject`, look up the `DBObject` indicated by this
     * `DBObjectInfo` and return it.
     *
     * @param obj  a `Connection` or any other `DBObject` from the same database
     *
     * @return the DBObject indicated by this `DBObjectInfo`, or `Null` if it could not be obtained
     */
    DBObject? lookupUsing(DBObject obj) {
        return obj.dbRoot.dbObjectFor(path);
    }
}