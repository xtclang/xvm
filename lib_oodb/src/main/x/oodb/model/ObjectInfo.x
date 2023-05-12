import DBObject.DBCategory;
import DBObject.Validator;
import DBObject.Rectifier;
import DBObject.Distributor;

/**
 * Simple implementation of the DBObjectInfo interface.
 */
const ObjectInfo(
        Path                          path,
        Path[]                        childPaths,
        DBCategory                    category,
        TypeParamInfo[]               typeParams,
        Boolean                       transactional   = True,
        Validator[]                   validators      = [],
        Rectifier[]                   rectifiers      = [],
        Distributor[]                 distributors    = [],
        LifeCycle                     lifeCycle       = Current,
        )
        implements DBObjectInfo
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
    }