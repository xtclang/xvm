import json.Doc;
import json.Mapping;
import json.Parser;
import json.Printer;
import json.Schema as JsonSchema;

import jsondb.Client.Worker;

import oodb.*;


/**
 * The evolution context.
 */
const Context(Connection oldDB, Connection newDB,
              Version? oldVersion = Null, Version? newVersion = Null) {

    /**
     * TODO
     * @return `True` iff the old object has been found
     * @return (conditional) the old object
     */
    conditional DBObject getOld(Path path) = Nullable.notNull(oldDB.dbObjectFor(path));

    /**
     * TODO
     * @return `True` iff the new object has been found
     * @return (conditional) the new object
     */
    conditional DBObject getNew(Path path) = Nullable.notNull(newDB.dbObjectFor(path));

    /**
     * Transform an object from the old DB to a json `Doc`.
     *
     * Note: the returned `Doc` is *mutable*.
     */
    <Serializable> Doc old2json(Type type, Serializable value) = obj2json(oldDB, type, value);

    /**
     * Transform a json `Doc` to an object in the new DB .
     */
    Const json2new(Type type, Doc jsonValue) = json2obj(newDB, type, jsonValue);

    /**
     * Transform an object from the the specified DB to a json `Doc`.
     */
    <Serializable> Doc obj2json(Connection db, Type type, Serializable value) {
        Client     client     = db.as(Inner).outer.as(Client);
        Catalog    catalog    = client.catalog;
        JsonSchema jsonSchema = catalog.jsonSchema;

        Mapping<type.DataType> mapping = jsonSchema.ensureMapping(type).as(Mapping<type.DataType>);

        String jsonString = client.worker.writeUsing(mapping, value);

        return new Parser(jsonString.toReader()).parseDoc();
    }

    /**
     * Transform a json `Doc` to an object in the specified DB .
     */
    Const json2obj(Connection db, Type type, Doc jsonValue) {
        Client     client     = db.as(Inner).outer.as(Client);
        Catalog    catalog    = client.catalog;
        JsonSchema jsonSchema = catalog.jsonSchema;
        String     jsonString = Printer.DEFAULT.render(jsonValue);

        Mapping<type.DataType> mapping = jsonSchema.ensureMapping(type).as(Mapping<type.DataType>);
        return client.worker.readUsing(mapping, jsonString).as(Const);
    }
}