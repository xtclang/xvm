/**
 * The database interface for a "table" of information, represented as a _dictionary_ style data
 * structure of key/value pairs: the `Map` interface.
 *
 * A `DBMap` is always transactional.
 */
interface DBMap<Key, Value>
        extends Map<Key, Value>
        extends DBObject
    {
// REVIEW
//    @Override
//    interface Entry
//        {
//        /**
//         * The Entry as it existed at the start of the current transaction; if no transaction is
//         * active, then this will be the same Entry as `this`.
//         */
//        @RO Entry original;
//
//        /**
//         * If the DBMap is audited, then this contains previous versions of the Entry, indexed by
//         * DateTime.
//         */
//        @RO Map<DateTime, Entry> changeLog;
//
//        @Override
//        Entry reify();
//
//        <Referent> Referent extract(Property<Value, Referent> prop)
//            {
//            return prop.get(value);
//            }
//        }

    @Override
    @RO Boolean transactional.get()
        {
        return True;
        }
    }
