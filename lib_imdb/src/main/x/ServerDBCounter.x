class ServerDBCounter
        extends ServerDBObject
        implements db.DBCounter
    {
    construct(db.DBObject? parent, String name)
        {
        construct ServerDBObject(parent, DBCounter, name);
        }

    @Override
    Int get()
        {
        return value;
        }

    @Override
    void set(Int value)
        {
        this.value = value;
        }

    @Override
    void adjustBy(Int value)
        {
        this.value += value;
        }

    protected Int value = 0;
    }
