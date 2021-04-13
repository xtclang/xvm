module imdb.xtclang.org
    {
    package oodb import oodb.xtclang.org;

    import oodb.DBObject.DBCategory as Category;

    const DBObjectInfo(String   id,
                       Category category,
                       Type     type,
                       String   parentId      = "",
                       String[] childIds      = [],
                       Boolean  transactional = True,
                      )
          {
          }

    /**
     * This is an abstract base class for specific DB implementations that manage data in memory.
     */
    @Abstract
    class DBObjectStore(DBObjectInfo info, Appender<String> errs)
        {
        /**
         * The DBObjectInfo that identifies the configuration of this DBObjectStore.
         */
        public/protected DBObjectInfo info;

        /**
         * The id of the database object for which this storage exists.
         */
        String id.get()
            {
            return info.id;
            }

        /**
         * The `DBCategory` of the `DBObject`.
         */
        Category category.get()
            {
            return info.category;
            }

        /**
         * An error log that was provided to this storage when it was created, for the purpose of
         * logging detailed error information encountered in the course of operation.
         */
        public/protected Appender<String> errs;

        /**
         * TODO
         */
        @Abstract
        void apply(Int clientId);

        /**
         * TODO
         */
        @Abstract
        void discard(Int clientId);
        }
    }