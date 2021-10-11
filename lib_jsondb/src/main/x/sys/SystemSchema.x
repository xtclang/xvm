import oodb.DBCounter;
import oodb.DBInfo;
import oodb.DBList;
import oodb.DBLog;
import oodb.DBMap;
import oodb.DBObject;
import oodb.DBPending;
import oodb.DBProcessor;
import oodb.DBQueue;
import oodb.DBSchema;
import oodb.DBTransaction;
import oodb.DBUser;
import oodb.DBValue;
import oodb.RootSchema;

/**
 * An implementation of the SystemSchema interface for the JSON DB implementation.
 */
class SystemSchema
        extends SystemObject
        implements oodb.SystemSchema
    {
    construct(Catalog catalog, RootSchema parent)
        {
        construct SystemObject(catalog, parent, "sys");
        }
    finally
        {
        info         = new DBInfoSingleton(this:protected);
//        users        = new DBMap<String, DBUser>() {};
//        types        = new DBMap<String, Type>() {};
//        objects      = new DBMap<String, DBObject>() {};
//        schemas      = new DBMap<String, DBSchema>() {};
//        counters     = new DBMap<String, DBCounter>() {};
//        values       = new DBMap<String, DBValue>() {};
//        maps         = new DBMap<String, DBMap>() {};
//        lists        = new DBMap<String, DBList>() {};
//        queues       = new DBMap<String, DBQueue>() {};
//        processors   = new DBMap<String, DBProcessor>() {};
//        logs         = new DBMap<String, DBLog>() {};
//        pending      = new DBList<DBPending>() {};
//        transactions = new DBLog<DBTransaction>() {};
//        errors       = new DBLog<String>() {};

        dbChildren   = Map:
                [
                "info"         = info,
//                "users"        = users,
//                "types"        = types,
//                "objects"      = objects,
//                "schemas"      = schemas,
//                "counters"     = counters,
//                "values"       = values,
//                "maps"         = maps,
//                "lists"        = lists,
//                "queues"       = queues,
//                "processors"   = processors,
//                "logs"         = logs,
//                "pending"      = pending,
//                "transactions" = transactions,
//                "errors"       = errors,
                ];

//        ["info", "users", "types", "objects", "schemas", "counters", "values", "maps", "lists",
//                "queues", "processors", "logs", "pending", "transactions", "errors",]
//        [ info ,  users ,  types ,  objects ,  schemas ,  counters ,  values ,  maps ,  lists ,
//                 queues ,  processors ,  logs ,  pending ,  transactions ,  errors ,]
//
//        dbChildren = new ListMap<String, DBObject>(
//        );
        }

    @Override DBCategory dbCategory.get()
        {
        return DBSchema;
        }

    @Override public/private Map<String, DBObject!>     dbChildren;

    @Override public/private DBValue<DBInfo>            info;

    @Override public/private DBMap<String, DBUser>      users;

    @Override public/private DBMap<String, Type>        types;

    @Override public/private DBMap<String, DBObject>    objects;

    @Override public/private DBMap<String, DBSchema>    schemas;

    @Override public/private DBMap<String, DBCounter>   counters;

    @Override public/private DBMap<String, DBValue>     values;

    @Override public/private DBMap<String, DBMap>       maps;

    @Override public/private DBMap<String, DBList>      lists;

    @Override public/private DBMap<String, DBQueue>     queues;

    @Override public/private DBMap<String, DBProcessor> processors;

    @Override public/private DBMap<String, DBLog>       logs;

    @Override public/private DBList<DBPending>          pending;

    @Override public/private DBLog<DBTransaction>       transactions;

    @Override public/private DBLog<String>              errors;
    }