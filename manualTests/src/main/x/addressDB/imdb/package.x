package imdb
    {
    // ---- server side ----------------------------------------------------------------------------

    class ServerDBObject
            implements db.DBObject
        {
        construct(db.DBObject? parent, DBCategory category, String name)
            {
            dbParent   = parent;
            dbCategory = category;
            dbName     = name;
            dbChildren = Map:[];
            }

        @Override
        public/private db.DBObject? dbParent;

        @Override
        public/private String dbName;

        @Override
        public/private DBCategory dbCategory;

        @Override
        public/protected Map<String, db.DBObject> dbChildren;
        }

    @Abstract
    class ServerRootSchema
            extends ServerDBObject
            implements db.RootSchema
        {
        construct()
            {
            construct ServerDBObject(Null, DBSchema, "");
            }

        @Override
        @Unassigned
        public/private db.SystemSchema sys; // TODO
        }


    // ---- client side ----------------------------------------------------------------------------

    @Abstract
    class ClientDBObject
            implements db.DBObject
            delegates db.DBObject(dbObject)
        {
        construct(ServerDBObject dbObject)
            {
            this.dbObject = dbObject;
            }

        protected db.DBObject dbObject;

        @Override
        Map<String, db.DBObject> dbChildren.get()
            {
            TODO("snapshot of dbObject.dbChildren");
            }
        }

    @Abstract
    class ClientRootSchema
            extends ClientDBObject
            implements db.RootSchema
        {
        construct(ServerRootSchema dbSchema)
            {
            construct ClientDBObject(dbSchema);
            }

        @Override
        db.SystemSchema sys.get()
            {
            TODO
            }
        }
    }