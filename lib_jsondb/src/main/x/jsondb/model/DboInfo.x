import oodb.DBObject.DBCategory;
import oodb.DBObject.Validator;
import oodb.DBObject.Rectifier;
import oodb.DBObject.Distributor;

import oodb.DBObjectInfo;


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
const DboInfo(
        Path                          path,
        DBCategory                    category,
        Int                           id,
        Int                           parentId,
        Int[]                         childIds        = [],
        String[]                      childNames      = [],
        Boolean                       transactional   = True,
        Validator[]                   validators      = [],
        Rectifier[]                   rectifiers      = [],
        Distributor[]                 distributors    = [],
        TypeParamInfo[]               typeParams      = [],
        LifeCycle                     lifeCycle       = Current,
        Map<String, immutable Object> options         = [],
        )
        implements DBObjectInfo {
    /**
     * Helper constructor for the auto-generated code.
     */
    construct(
             Path                          path,
             DBCategory                    category,
             Int                           id,
             Int                           parentId,
             Int[]                         childIds        = [],
             String[]                      childNames      = [],
             Boolean                       transactional   = True,
             Validator[]                   validators      = [],
             Rectifier[]                   rectifiers      = [],
             Distributor[]                 distributors    = [],
             Map<String, Type>             typeParamsTypes = [],
             Map<String, Class[]>          concreteClasses = [],
             LifeCycle                     lifeCycle       = Current,
             Map<String, immutable Object> options         = [],
             ) {
        TypeParamInfo[] typeParams = [];
        if (typeParamsTypes.size > 0) {
            assert typeParamsTypes.keys.containsAll(concreteClasses.keys);

            typeParams = new TypeParamInfo[];
            for ((String name, Type type) : typeParamsTypes) {
                typeParams += new TypeParamInfo(name, type, concreteClasses.getOrDefault(name, []));
            }
        } else {
            assert concreteClasses.empty;
        }

        this.path          = path;
        this.category      = category;
        this.id            = id;
        this.parentId      = parentId;
        this.childIds      = childIds;
        this.childNames    = childNames;
        this.transactional = transactional;
        this.validators    = validators;
        this.rectifiers    = rectifiers;
        this.distributors  = distributors;
        this.typeParams    = typeParams;
        this.lifeCycle     = lifeCycle;
        this.options       = options;
    }

    /**
     * A useful "name" for the DBObject.
     */
    String idString.get() {
        String pathStr = path.toString();
        return pathStr.size > 1 ? pathStr.substring(1) : pathStr;
    }

    /**
     * Verify that the DboInfo is valid.
     *
     * @return True if the check passes
     *
     * @throws Exception if the check fails
     */
    Boolean checkValid() {
        // system objects have IDs less than zero, but they are not checked
        assert id >= 0;

        // parent ids also have to be legit
        assert parentId >= 0;

        // id 0 is always the root schema, which has no parent (so it uses itself as its own parent)
        if (id == 0) {
            assert parentId == 0;
            assert name == "";
            assert path == ROOT;
        } else {
            assert parentId != id;

            if (String fault := oodb.isInvalidName(name)) {
                throw new IllegalState(fault);
            }

            // the last name in the path must be this name
            assert path[path.size-1].name == name;

            // each name must be valid
            Loop: for (Path pathPart : path) {
                if (String fault := oodb.isInvalidName(pathPart.name)) {
                    throw new IllegalState($"Path ({path.toString().quoted()}) element {Loop.count}: {fault}");
                }
            }
        }

        // child ids must be positive and unique (and different from this and parent id)
        if (!childIds.empty) {
            Set<Int> allIds = new HashSet();
            for (Int childId : childIds) {
                assert childId > 0;
                assert childId != id && childId != parentId;
                assert allIds.addIfAbsent(childId);
            }
        }

        // check transactionality
        switch (category) {
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
        for (TypeParamInfo typeParam : typeParams) {
            String paramName = typeParam.name;
            switch (category) {
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
            Type    paramType = typeParam.type;
            Class[] classes   = typeParam.concreteClasses;
            if (classes.empty) {
                assert Class clz := paramType.fromClass(), !clz.abstract;
            } else {
                for (Class clz : classes) {
                    assert clz.toType().isA(paramType) && !clz.abstract;
                }
            }
        }

        return True;
    }

    /**
     * Determine if the DboInfo specifies a parent id. The DboInfo with `id==0` does not
     * have a parent.
     *
     * @return True iff the DboInfo has a parent
     * @return (conditional) the id of the parent
     */
    conditional Int hasParent() {
        return id != 0, parentId;
    }

    /**
     * Create a copy of this DboInfo with a new parent.
     *
     * @param parent  the new parent
     *
     * @return the copy of this DboInfo with the specified parent
     */
    DboInfo withParent(DboInfo parent) {
        assert id != 0;
        return new DboInfo(
                path          = parent.path + name,
                category      = category,
                id            = id,
                parentId      = parent.id,
                childIds      = childIds,
                childNames    = childNames,
                transactional = transactional,
                validators    = validators,
                rectifiers    = rectifiers,
                distributors  = distributors,
                typeParams    = typeParams,
                lifeCycle     = lifeCycle,
                options       = options,
                );
    }

    /**
     * Create a copy of this DboInfo with a new child.
     *
     * @param child  the new child
     *
     * @return the copy of this DboInfo with the specified child added
     */
    DboInfo withChild(DboInfo child) {
        if (childIds.contains(child.id)) {
            return this;
        }

        return new DboInfo(
                path          = path,
                category      = category,
                id            = id,
                parentId      = parentId,
                childIds      = childIds   + child.id,
                childNames    = childNames + child.name,
                transactional = transactional,
                validators    = validators,
                rectifiers    = rectifiers,
                distributors  = distributors,
                typeParams    = typeParams,
                lifeCycle     = lifeCycle,
                options       = options,
                );
    }

    /**
     * Create a copy of this DboInfo with the specified new children.
     *
     * @param addIds  the child ids to add as children
     *
     * @return the copy of this DboInfo with the specified children added
     */
    DboInfo withChildren(Int[] addIds, String[] addNames) {
        if (childIds.containsAll(addIds)) {
            return this;
        }

        Int[]    mergeIds;
        String[] mergeNames;
        if (childIds.empty) {
            mergeIds   = addIds;
            mergeNames = addNames;
        } else {
            mergeIds   = new Array<Int>(Mutable, childIds);
            mergeNames = new Array<String>(Mutable, childNames);
            for (Int i : 0 ..< addIds.size) {
                Int addId = addIds[i];
                if (!mergeIds.contains(addId)) {
                    String addName = addNames[i];
                    assert !mergeNames.contains(addName);

                    mergeIds   += addId;
                    mergeNames += addName;
                }
            }
        }

        return new DboInfo(
                path          = path,
                category      = category,
                id            = id,
                parentId      = parentId,
                childIds      = mergeIds,
                childNames    = mergeNames,
                transactional = transactional,
                validators    = validators,
                rectifiers    = rectifiers,
                distributors  = distributors,
                typeParams    = typeParams,
                lifeCycle     = lifeCycle,
                options       = options,
                );
    }
}