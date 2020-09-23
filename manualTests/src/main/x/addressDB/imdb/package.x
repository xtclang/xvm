package imdb
    {
    // ---- server side ----------------------------------------------------------------------------

    class ServerDBObject
            implements db.DBObject
        {
        construct(db.DBObject? parent, String name)
            {
            dbParent   = parent;
            dbName     = name;
            dbChildren = Map:[];
            }

        @Override
        public/private db.DBObject? dbParent;

        @Override
        public/private String dbName;

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
            construct ServerDBObject(Null, "");
            }

        @Override
        @Unassigned
        public/private db.SystemSchema sys; // TODO
        }


    // ---- client side ----------------------------------------------------------------------------

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