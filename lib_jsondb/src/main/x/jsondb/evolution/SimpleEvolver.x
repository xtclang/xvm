import oodb.*;
import oodb.evolution.*;
import json.*;

import DBObject.DBCategory;

/**
 * The simple evolver implementation that simply copies the content of the DB if there are no
 * type conflicts between the schemas; fails otherwise.
 *
 * It could be easily extended to process all the encountered conflicts by hand.
 */
class SimpleEvolver
        implements Evolver {
    @Inject Console console; // TODO remove

    @Override
    void evolve(Connection oldDB, Connection newDB) {
        console.print($">>> Evolving {oldDB} to {newDB}");

        // REVIEW: should that be a single transaction or DBObject by DBObject?
        using (newDB.createTransaction()) {
            evolveChildren(new Context(oldDB, newDB), oldDB, newDB);
        }
    }

    /**
     * Evolve all children of the specified `DBObject`.
     */
    void evolveChildren(Context context, DBObject oldObject, DBObject newObject) {
        Map<String, DBObject> oldChildren = oldObject.dbChildren;
        Map<String, DBObject> newChildren = newObject.dbChildren;

        Set<DBObject> removed = new ListSet();
        Set<DBObject> added   = new ListSet();

        for ((String name, DBObject oldChild) : oldChildren) {
            if (DBObject newChild := newChildren.get(name)) {
                DBCategory oldCategory = oldChild.dbCategory;
                DBCategory newCategory = newChild.dbCategory;

                assert oldCategory == newCategory as "Inter-category evolution is not supported";

                switch (oldCategory) {
                case DBSchema:
                    if (oldChild.dbPath == Path:/sys) {
                        // skip "sys_tables"
                        return;
                    }
                    break;

                case DBCounter:
                    evolveDBCounter(context, oldChild.as(DBCounter), newChild.as(DBCounter));
                    break;
                case DBValue:
                    evolveDBValue(context, oldChild.as(DBValue), newChild.as(DBValue));
                    break;
                case DBMap:
                    evolveDBMap(context, oldChild.as(DBMap), newChild.as(DBMap));
                    break;
                case DBList:
                    // TODO
                    break;
                case DBQueue:
                    // TODO
                    break;
                case DBProcessor:
                    // TODO
                    break;
                case DBLog:
                    // REVIEW: is there any reason to evolve the logs?
                    break;
                }

                evolveChildren(context, oldChild, newChild);
            } else {
                removed += oldChild;
            }
        }

        for ((String name, DBObject newChild) : newChildren) {
            if (!oldChildren.get(name)) {
                added += newChild;
            }
        }

        removed.forEach(child -> processRemovedChild(context, child));
        added  .forEach(child -> processAddedChild  (context, child));
    }

    /**
     * Evolve an old `DBMap` into a new one.
     */
    void evolveDBMap(Context context, DBMap oldDBMap, DBMap newDBMap) {
        console.print($">>> Evolving {oldDBMap.dbName} map");

        Type oldKeyType = oldDBMap.Key;
        Type oldValType = oldDBMap.Value;
        Type newKeyType = newDBMap.Key;
        Type newValType = newDBMap.Value;
        Path oldPath    = oldDBMap.dbPath;

        for ((Const oldKey, Const oldVal) : oldDBMap) {

            Doc oldJsonKey = context.old2json(oldKeyType, oldKey);
            Doc oldJsonVal = context.old2json(oldValType, oldVal);

            if ((Doc newJsonKey, Doc newJsonVal) :=
                    transformMapEntry(context, oldPath, oldJsonKey, oldJsonVal)) {

                Const newKey = context.json2new(newKeyType, newJsonKey);
                Const newVal = context.json2new(newValType, newJsonVal);
                newDBMap.put(newKey, newVal);
            }
        }
    }

    /**
     * Evolve an old `DBValue` into a new one.
     */
    void evolveDBValue(Context context, DBValue oldDBValue, DBValue newDBValue) {
        console.print($">>> Evolving {oldDBValue.dbName} value");

        Doc oldJsonVal = context.old2json(oldDBValue.Value, oldDBValue.get());

        if (Doc newJsonVal := transformValue(context, oldDBValue.dbPath, oldJsonVal)) {
            Const newVal = context.json2new(newDBValue.Value, newJsonVal);
            newDBValue.set(newVal);
        }
    }

    /**
     * Evolve an old `DBCounter` into a new one.
     */
    void evolveDBCounter(Context context, DBCounter oldDBCounter, DBCounter newDBCounter) {
        console.print($">>> Evolving {oldDBCounter.dbName} value");

        newDBCounter.set(oldDBCounter.get());
    }

    /**
     * Evolve an old `DBMap` entry into a new entry.
     */
    conditional (Doc, Doc) transformMapEntry(Context context, Path path, Doc key, Doc value) =
            (True, key, value);

    /**
     * Evolve an old `DBValue` value into a new one.
     */
    conditional Doc transformValue(Context context, Path path, Doc value) = (True, value);

    /**
     * Process removed DBObjects.
     */
    void processRemovedChild(Context context, DBObject oldChild) {
        switch ( oldChild.dbCategory) {
        case DBCounter:
            processRemovedDBCounter(context, oldChild.as(DBCounter));
            break;
        case DBValue:
            processRemovedDBValue(context, oldChild.as(DBValue));
            break;
        case DBMap:
            processRemovedDBMap(context, oldChild.as(DBMap));
            break;
        case DBList:
            // TODO
            break;
        case DBQueue:
            // TODO
            break;
        case DBProcessor:
            // TODO
            break;
        case DBLog:
            // REVIEW: is there any reason to process removed logs?
            break;
        }
    }

    /**
     * Process removed `DBMap`.
     */
    void processRemovedDBMap(Context context, DBMap oldDBMap) {}

    /**
     * Process removed `DBValue`.
     */
    void processRemovedDBValue(Context context, DBValue oldDBValue) {}

    /**
     * Process removed `DBCounter`.
     */
    void processRemovedDBCounter(Context context, DBCounter oldDBCounter) {}

    /**
     * Process added DBObjects.
     */
    void processAddedChild(Context context, DBObject newChild) {
        switch (newChild.dbCategory) {
        case DBCounter:
            processAddedDBCounter(context, newChild.as(DBCounter));
            break;
        case DBValue:
            processAddedDBValue(context, newChild.as(DBValue));
            break;
        case DBMap:
            processAddedDBMap(context, newChild.as(DBMap));
            break;
        case DBList:
            // TODO
            break;
        case DBQueue:
            // TODO
            break;
        case DBProcessor:
            // TODO
            break;
        case DBLog:
            // REVIEW: is there any reason to process added logs?
            break;
        }
    }

    /**
     * Process added `DBMap`.
     */
    void processAddedDBMap(Context context, DBMap oldDBMap) {}

    /**
     * Process added `DBValue`.
     */
    void processAddedDBValue(Context context, DBValue oldDBValue) {}

    /**
     * Process added `DBCounter`.
     */
    void processAddedDBCounter(Context context, DBCounter oldDBCounter) {}
}
