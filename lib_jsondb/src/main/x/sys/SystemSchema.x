import oodb.DBValue;
import oodb.DBInfo;
import oodb.DBMap;
import oodb.DBUser;
import oodb.DBList;
import oodb.DBSchema;
import oodb.DBQueue;
import oodb.DBLog;
import oodb.DBCounter;
import oodb.DBFunction;
import oodb.DBInvoke;
import oodb.DBTransaction;
import oodb.DBObject;
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
//        maps         = new DBMap<String, DBMap>() {};
//        queues       = new DBMap<String, DBQueue>() {};
//        lists        = new DBMap<String, DBList>() {};
//        logs         = new DBMap<String, DBLog>() {};
//        counters     = new DBMap<String, DBCounter>() {};
//        singletons   = new DBMap<String, DBValue>() {};
//        functions    = new DBMap<String, DBFunction>() {};
//        pending      = new DBList<DBInvoke>() {};
//        transactions = new DBLog<DBTransaction>() {};
//        errors       = new DBLog<String>() {};

        dbChildren   = Map:[
                "info"         = info,
//                "users"        = users,
//                "types"        = types,
//                "objects"      = objects,
//                "schemas"      = schemas,
//                "maps"         = maps,
//                "queues"       = queues,
//                "lists"        = lists,
//                "logs"         = logs,
//                "counters"     = counters,
//                "singletons"   = singletons,
//                "functions"    = functions,
//                "pending"      = pending,
//                "transactions" = transactions
                ];

//        ["info", "users", "types", "objects", "schemas", "maps", "queues", "lists", "logs",
//                "counters", "singletons", "functions", "pending", "transactions"]
//        [ info ,  users ,  types ,  objects ,  schemas ,  maps ,  queues ,  lists ,  logs ,
//                counters ,  singletons ,  functions ,  pending ,  transactions ]
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

    @Override public/private DBMap<String, DBMap>       maps;

    @Override public/private DBMap<String, DBQueue>     queues;

    @Override public/private DBMap<String, DBList>      lists;

    @Override public/private DBMap<String, DBLog>       logs;

    @Override public/private DBMap<String, DBCounter>   counters;

    @Override public/private DBMap<String, DBValue>     values;

    @Override public/private DBMap<String, DBFunction>  functions;

    @Override public/private DBList<DBInvoke>           pending;

    @Override public/private DBLog<DBTransaction>       transactions;

    @Override public/private DBLog<String>              errors;
    }