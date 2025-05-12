import DBObject.DBCategory;

/**
 * An abstract evolver implementation.
 *
 * It allows subclasses to override specific
 *
 * It could be easily extended to process all the encountered conflicts by hand.
 */
@Abstract
class AbstractEvolver(Connection oldDB, Connection newDB)
        implements Evolver {

    @Override
    void evolve() {
        // REVIEW: should that be a single transaction or DBObject by DBObject?
        using (newDB.createTransaction()) {
            evolveChildren(oldDB, newDB);
        }
    }

    /**
     * Evolve all children of the specified `DBObject`.
     */
    protected void evolveChildren(DBObject oldObject, DBObject newObject) {
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
                    evolveDBCounter(oldChild.as(DBCounter), newChild.as(DBCounter));
                    break;
                case DBValue:
                    evolveDBValue(oldChild.as(DBValue), newChild.as(DBValue));
                    break;
                case DBMap:
                    evolveDBMap(oldChild.as(DBMap), newChild.as(DBMap));
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

                evolveChildren(oldChild, newChild);
            } else {
                removed += oldChild;
            }
        }

        for ((String name, DBObject newChild) : newChildren) {
            if (!oldChildren.get(name)) {
                added += newChild;
            }
        }

        removed.forEach(child -> processRemovedChild(child));
        added  .forEach(child -> processAddedChild  (child));
    }

    /**
     * Evolve an old `DBMap` into a new one.
     */
    @Abstract
    protected void evolveDBMap(DBMap oldDBMap, DBMap newDBMap);

    /**
     * Evolve an old `DBValue` into a new one.
     */
    @Abstract
    protected void evolveDBValue(DBValue oldDBValue, DBValue newDBValue);

    /**
     * Evolve an old `DBCounter` into a new one.
     */
    protected void evolveDBCounter(DBCounter oldDBCounter, DBCounter newDBCounter) {
        newDBCounter.set(oldDBCounter.get());
    }

    /**
     * Process removed DBObjects.
     */
    protected void processRemovedChild(DBObject oldChild) {
        switch (oldChild.dbCategory) {
        case DBCounter:
            processRemovedDBCounter(oldChild.as(DBCounter));
            break;
        case DBValue:
            processRemovedDBValue(oldChild.as(DBValue));
            break;
        case DBMap:
            processRemovedDBMap(oldChild.as(DBMap));
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
    protected void processRemovedDBMap(DBMap oldDBMap) {}

    /**
     * Process removed `DBValue`.
     */
    protected void processRemovedDBValue(DBValue oldDBValue) {}

    /**
     * Process removed `DBCounter`.
     */
    protected void processRemovedDBCounter(DBCounter oldDBCounter) {}

    /**
     * Process added DBObjects.
     */
    protected void processAddedChild(DBObject newChild) {
        switch (newChild.dbCategory) {
        case DBCounter:
            processAddedDBCounter(newChild.as(DBCounter));
            break;
        case DBValue:
            processAddedDBValue(newChild.as(DBValue));
            break;
        case DBMap:
            processAddedDBMap(newChild.as(DBMap));
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
    protected void processAddedDBMap(DBMap oldDBMap) {}

    /**
     * Process added `DBValue`.
     */
    protected void processAddedDBValue(DBValue oldDBValue) {}

    /**
     * Process added `DBCounter`.
     */
    protected void processAddedDBCounter(DBCounter oldDBCounter) {}
}
