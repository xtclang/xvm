class ServerDBCounter
        extends ServerDBObject
        implements oodb.DBCounter
    {
    construct(oodb.DBObject? parent, String name)
        {
        construct ServerDBObject(parent, DBCounter, name);
        }

    protected Int value_ = 0;

    @Override
    Int get()
        {
        return value_;
        }

    @Override
    void set(Int value)
        {
        value_ = value;
        }

    @Override
    void adjustBy(Int value)
        {
        value_ += value;
        }
    }
