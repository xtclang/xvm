import oodb.DBObject.DBCategory as Category;

/**
 * Persistent metadata information about a particular `DBObject`.
 *
 * Each database object may specify one or more parameters, such as `Key` and `Value` for a
 * `DBMap`. These are type constraints, which is to say that values are constrained to be "at
 * least" of these types. However, since the information must be stored persistently, it is
 * necessary for the database to know (in advance) what the actual possible types are for each
 * of these constraints; the constraint types for each type parameter name are specified in the
 * [typeParameters] property, and the possible types (any sub-classes that may occur) are required
 * to be enumerated in the [acceptableSubClasses] property.
 *
 * TODO lifecycle (e.g. "deprecated" and "retired")
 */
const DBObjectInfo(
        String               name,
        String               path,
        Category             category,
        Int                  id,
        Int                  parentId,
        Int[]                childIds             = [],
        Boolean              transactional        = True,
        Map<String, Type>    typeParameters       = Map:[],
        Map<String, Class[]> acceptableSubClasses = Map:[])
    {
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
            assert path == "";
            }
        else
            {
            assert parentId != id;

            if (String fault := oodb.isInvalidName(name))
                {
                throw new IllegalState(fault);
                }

            String[] names = path.split('/');
            Int      count = names.size;

            // the path has to at least include this name
            assert count > 0;

            // the last name in the path must be this name
            assert names[count-1] == name;

            // each name must be valid
            Loop: for (String pathPart : names)
                {
                if (String fault := oodb.isInvalidName(pathPart))
                    {
                    throw new IllegalState($"Path ({path.quoted()}) element {Loop.count}: {fault}");
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
        for ((String paramName, Type paramType) : typeParameters)
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
                    assert paramName == "Element";
                    break;

                case DBFunction:
                    // "ParamType[0]", "ReturnType[1]", etc. since there are `n` types
                    String index;
                    if (paramName.startsWith("ReturnType["))
                        {
                        assert paramName.endsWith(']');
                        index = paramName["ReturnType[".size .. paramName.size-2];
                        }
                    else
                        {
                        assert paramName.startsWith("ParamType[") && paramName.endsWith(']');
                        index = paramName["ParamType[".size .. paramName.size-2];
                        }

                    Int n = new IntLiteral(index).toInt64();
                    assert n >= 0;
                    break;

                default:
                    throw new IllegalState($|{category} {name.quoted()} specifies a type parameter\
                                            | {paramName.quoted()}; no type parameters are supported
                                          );
                }

            switch (paramName)
                {
                case "Key":
                    assert category == DBMap;
                    break;

                case "Value":
                    assert category == DBMap || category == DBValue;
                    break;

                case "Element":
                    assert category == DBLog || category == DBList || category == DBQueue;
                    break;

                case "ParamTypes":
                case "ReturnTypes":
                    assert category == DBFunction;
                    break;

                default:
                    throw new IllegalState($"Unsupported type parameter {paramName.quoted()}");
                }
            }

        // TODO acceptableSubClasses

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
                name                 = name,
                path                 = parent.id == 0 ? name : $"{parent.path}/{name}",
                category             = category,
                id                   = id,
                parentId             = parent.id,
                childIds             = childIds,
                transactional        = transactional,
                typeParameters       = typeParameters,
                acceptableSubClasses = acceptableSubClasses);
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
                name                 = name,
                path                 = path,
                category             = category,
                id                   = id,
                parentId             = parentId,
                childIds             = childIds + child.id,
                transactional        = transactional,
                typeParameters       = typeParameters,
                acceptableSubClasses = acceptableSubClasses);
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
                name                 = name,
                path                 = path,
                category             = category,
                id                   = id,
                parentId             = parentId,
                childIds             = mergeIds,
                transactional        = transactional,
                typeParameters       = typeParameters,
                acceptableSubClasses = acceptableSubClasses);
        }
    }
