module imdb.xtclang.org
    {
    package oodb import oodb.xtclang.org;

    // ---- server side ----------------------------------------------------------------------------

    class ServerDBObject
            implements oodb.DBObject
        {
        construct(oodb.DBObject? parent, DBCategory category, String name)
            {
            dbParent   = parent;
            dbCategory = category;
            dbName     = name;
            dbChildren = Map:[];
            }

        @Override
        public/private oodb.DBObject? dbParent;

        @Override
        public/private String dbName;

        @Override
        public/private DBCategory dbCategory;

        @Override
        public/protected Map<String, oodb.DBObject> dbChildren;
        }

    @Abstract
    class ServerRootSchema
            extends ServerDBObject
            implements oodb.RootSchema
        {
        construct()
            {
            construct ServerDBObject(Null, DBSchema, "");
            }

        @Override
        @Unassigned
        public/private oodb.SystemSchema sys; // TODO
        }


    // ---- client side ----------------------------------------------------------------------------

    @Abstract
    class ClientDBObject
            implements oodb.DBObject
            delegates  oodb.DBObject(dbObject_)
        {
        construct(ServerDBObject dbObject,
                  function Boolean() isAutoCommit = () -> False)
            {
            dbObject_     = dbObject;
            isAutoCommit_ = isAutoCommit;
            }

        protected ServerDBObject     dbObject_;
        protected function Boolean() isAutoCommit_;

        @Override
        Map<String, oodb.DBObject> dbChildren.get()
            {
            TODO("snapshot of dbObject_.dbChildren");
            }
        }

    @Abstract
    class ClientRootSchema
            extends ClientDBObject
            implements oodb.RootSchema
        {
        construct(ServerRootSchema dbSchema)
            {
            construct ClientDBObject(dbSchema);
            }

        @Override
        oodb.SystemSchema sys.get()
            {
            TODO
            }
        }
    }