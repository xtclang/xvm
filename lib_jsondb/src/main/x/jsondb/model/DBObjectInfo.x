import oodb.DBObject.DBCategory as Category;
import oodb.DBObject.Validator;
import oodb.DBObject.Rectifier;
import oodb.DBObject.Distributor;

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
        String                        name,
        Path                          path,
        Category                      category,
        Int                           id,
        Int                           parentId,
        Int[]                         childIds        = [],
        Boolean                       transactional   = True,
        Validator[]                   validators      = [],
        Rectifier[]                   rectifiers      = [],
        Distributor[]                 distributors    = [],
        Map<String, Type>             typeParams      = [],
        Map<String, Class[]>          concreteClasses = [],
        LifeCycle                     lifeCycle       = Current,
        Map<String, immutable Object> options         = [],
        )
    {
    enum LifeCycle {Current, Deprecated, Removed}

    /**
     * A useful "name" for the DBObject.
     */
    String idString.get()
        {
        String pathStr = path.toString();
        return pathStr.size > 1 ? pathStr.substring(1) : pathStr;
        }

    /**
     * Verify that the DBObjectInfo is valid.
     *
     * @return True if the check passes
     *
     * @throws Exception if the check fails
     */
    Boolean checkValid()
        {
        // system objects have IDs less than zero, but they are not checked
        assert id >= 0;

        // parent ids also have to be legit
        assert parentId >= 0;

        // id 0 is always the root schema, which has no parent (so it uses itself as its own parent)
        if (id == 0)
            {
            assert parentId == 0;
            assert name == "";
            assert path == ROOT;
            }
        else
            {
            assert parentId != id;

            if (String fault := oodb.isInvalidName(name))
                {
                throw new IllegalState(fault);
                }

            // the last name in the path must be this name
            assert path[path.size-1].name == name;

            // each name must be valid
            Loop: for (Path pathPart : path)
                {
                if (String fault := oodb.isInvalidName(pathPart.name))
                    {
                    throw new IllegalState($"Path ({path.toString().quoted()}) element {Loop.count}: {fault}");
                    }
                }
            }

        // child ids must be positive and unique (and different from this and parent id)
        if (!childIds.empty)
            {
            Set<Int> allIds = new HashSet();
            for (Int childId : childIds)
                {
                assert childId > 0;
                assert childId != id && childId != parentId;
                assert allIds.addIfAbsent(childId);
                }
            }

        // check transactionality
        switch (category)
            {
            case DBSchema:
                // schema is always non-transactional
                assert !transactional;
                break;

            case DBCounter:
            case DBLog:
                // these can be either transactional or extra-transactional
                break;

            default:
                // the other DBObjects must all be transactional
                assert transactional;
                break;
            }

        // check type parameters
        for ((String paramName, Type paramType) : typeParams)
            {
            switch (category)
                {
                case DBMap:
                    assert paramName == "Key" || paramName == "Value";
                    break;

                case DBValue:
                    assert paramName == "Value";
                    break;

                case DBList:
                case DBLog:
                case DBQueue:
                case DBProcessor:
                    assert paramName == "Element";
                    break;

                default:
                    throw new IllegalState($|{category} {name.quoted()} specifies a type parameter\
                                            | {paramName.quoted()}; no type parameters are supported
                                          );
                }

            // if paramType is not a concrete class, then there must be concreteClasses
            // specified, otherwise they are optional; furthermore, all concreteClasses must
            // be concrete classes, and must be "isA" of the param type
            Class[]? classes = concreteClasses.getOrNull(paramName);
            if (classes == Null || classes.empty)
                {
                assert Class clz := paramType.fromClass(), !clz.abstract;
                }
            else
                {
                for (Class clz : classes)
                    {
                    assert clz.toType().isA(paramType) && !clz.abstract;
                    }
                }
            }

        assert typeParams.keys.containsAll(concreteClasses.keys);

        return True;
        }

    /**
     * Determine if the DBObjectInfo specifies a parent id. The DBObjectInfo with `id==0` does not
     * have a parent.
     *
     * @return True iff the DBObjectInfo has a parent
     * @return (conditional) the id of the parent
     */
    conditional Int hasParent()
        {
        return id != 0, parentId;
        }

    /**
     * Create a copy of this DBObjectInfo with a new parent.
     *
     * @param parent  the new parent
     *
     * @return the copy of this DBObjectInfo with the specified parent
     */
    DBObjectInfo withParent(DBObjectInfo parent)
        {
        assert id != 0;
        return new DBObjectInfo(
                name            = name,
                path            = parent.path + name,
                category        = category,
                id              = id,
                parentId        = parent.id,
                childIds        = childIds,
                transactional   = transactional,
                validators      = validators,
                rectifiers      = rectifiers,
                distributors    = distributors,
                typeParams      = typeParams,
                concreteClasses = concreteClasses,
                lifeCycle       = lifeCycle,
                );
        }

    /**
     * Create a copy of this DBObjectInfo with a new child.
     *
     * @param child  the new child
     *
     * @return the copy of this DBObjectInfo with the specified child added
     */
    DBObjectInfo withChild(DBObjectInfo child)
        {
        if (childIds.contains(child.id))
            {
            return this;
            }

        return new DBObjectInfo(
                name            = name,
                path            = path,
                category        = category,
                id              = id,
                parentId        = parentId,
                childIds        = childIds + child.id,
                transactional   = transactional,
                validators      = validators,
                rectifiers      = rectifiers,
                distributors    = distributors,
                typeParams      = typeParams,
                concreteClasses = concreteClasses,
                lifeCycle       = lifeCycle,
                );
        }

    /**
     * Create a copy of this DBObjectInfo with the specified new children.
     *
     * @param addIds  the child ids to add as children
     *
     * @return the copy of this DBObjectInfo with the specified children added
     */
    DBObjectInfo withChildren(Int[] addIds)
        {
        if (childIds.containsAll(addIds))
            {
            return this;
            }

        Int[] mergeIds;
        if (childIds.empty)
            {
            mergeIds = addIds;
            }
        else
            {
            mergeIds = new Array<Int>(Mutable, childIds);
            for (Int addId : addIds)
                {
                if (!mergeIds.contains(addId))
                    {
                    mergeIds.add(addId);
                    }
                }
            }

        return new DBObjectInfo(
                name            = name,
                path            = path,
                category        = category,
                id              = id,
                parentId        = parentId,
                childIds        = mergeIds,
                transactional   = transactional,
                validators      = validators,
                rectifiers      = rectifiers,
                distributors    = distributors,
                typeParams      = typeParams,
                concreteClasses = concreteClasses,
                lifeCycle       = lifeCycle,
                );
        }
    }
