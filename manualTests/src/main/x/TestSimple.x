module TestSimple
    {
    @Inject Console console;

    package oodb import oodb.xtclang.org;

    import ecstasy.reflect.PropertyTemplate;
    import ecstasy.reflect.TypeTemplate;

    import oodb.DBObject.DBCategory;

    void run()
        {
        }

    void test(PropertyTemplate prop)
        {
        Map<DBCategory, PropertyTemplate[]> mapProps = new HashMap();

        for ((DBCategory category, TypeTemplate dbType) : DB_TEMPLATES)
            {
            if (prop.type.isA(dbType))
                {
                mapProps.process(category, entry ->
                    {
                    PropertyTemplate[] props = entry.exists
                            ? entry.value
                            : new Array<PropertyTemplate>();
                    entry.value = props.add(prop);
                    return entry.key;
                    });
                }
            }
        }

    static Map<DBCategory, TypeTemplate> DB_TEMPLATES =
        Map:[
            DBMap = oodb.DBMap.baseTemplate.type
            ];
    }