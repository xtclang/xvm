import oodb.*;
import oodb.evolution.AbstractEvolver;

import json.Doc;
import json.Mapping;
import json.Parser;
import json.Printer;
import json.Schema as JsonSchema;

import DBObject.DBCategory;

/**
 * The simple evolver implementation that simply copies the content of the DB if there are no
 * type conflicts between the schemas; fails otherwise.
 *
 * It allows subclasses to override specific object evolution by using the corresponding JSON values.
 *
 * It could be easily extended to process all the encountered conflicts by hand.
 */
class JsonEvolver(Connection oldDB, Connection newDB)
        extends AbstractEvolver(oldDB, newDB) {

    @Inject Console console; // TODO remove

    @Override
    void evolve() {
        console.print($">>> Evolving {oldDB} to {newDB}");
        super();
    }

    @Override
    protected void evolveDBMap(DBMap oldDBMap, DBMap newDBMap) {
        console.print($">>> Evolving {oldDBMap.dbName} map");

        Type oldKeyType = oldDBMap.Key;
        Type oldValType = oldDBMap.Value;
        Type newKeyType = newDBMap.Key;
        Type newValType = newDBMap.Value;
        Path oldPath    = oldDBMap.dbPath;

        for ((Const oldKey, Const oldVal) : oldDBMap) {

            Doc oldJsonKey = obj2json(oldDB, oldKeyType, oldKey);
            Doc oldJsonVal = obj2json(oldDB, oldValType, oldVal);

            if ((Doc newJsonKey, Doc newJsonVal) :=
                    transformMapEntry(oldPath, oldJsonKey, oldJsonVal)) {

                Const newKey = json2obj(newDB, newKeyType, newJsonKey);
                Const newVal = json2obj(newDB, newValType, newJsonVal);
                newDBMap.put(newKey, newVal);
            }
        }
    }

    @Override
    protected void evolveDBValue(DBValue oldDBValue, DBValue newDBValue) {
        console.print($">>> Evolving {oldDBValue.dbName} value");

        Doc oldJsonVal = obj2json(oldDB, oldDBValue.Value, oldDBValue.get());

        if (Doc newJsonVal := transformValue(oldDBValue.dbPath, oldJsonVal)) {
            Const newVal = json2obj(newDB, newDBValue.Value, newJsonVal);
            newDBValue.set(newVal);
        }
    }

    @Override
    protected void evolveDBCounter(DBCounter oldDBCounter, DBCounter newDBCounter) {
        console.print($">>> Evolving {oldDBCounter.dbName} value");

        super(oldDBCounter, newDBCounter);
    }

    /**
     * Evolve an old `DBMap` entry into a new entry.
     */
    protected conditional (Doc, Doc) transformMapEntry(Path path, Doc key, Doc value) = (True, key, value);

    /**
     * Evolve an old `DBValue` value into a new one.
     */
    protected conditional Doc transformValue(Path path, Doc value) = (True, value);

    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Transform an object from the the specified DB to a json `Doc`.
     */
    static <Serializable> Doc obj2json(Connection db, Type type, Serializable value) {
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
    static Const json2obj(Connection db, Type type, Doc jsonValue) {
        Client     client     = db.as(Inner).outer.as(Client);
        Catalog    catalog    = client.catalog;
        JsonSchema jsonSchema = catalog.jsonSchema;
        String     jsonString = Printer.DEFAULT.render(jsonValue);

        Mapping<type.DataType> mapping = jsonSchema.ensureMapping(type).as(Mapping<type.DataType>);
        return client.worker.readUsing(mapping, jsonString).as(Const);
    }
}
