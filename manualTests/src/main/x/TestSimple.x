module TestSimple
    {
    @Inject Console console;

    package db import oodb.xtclang.org;

    import ecstasy.reflect.ClassTemplate;
    import ecstasy.reflect.ComponentTemplate;
    import ecstasy.reflect.PropertyTemplate;
    import ecstasy.reflect.TypeTemplate;

    import db.DBObject.DBCategory;

    void run()
        {
        Class clz = DBCategory;
        ClassTemplate ct = clz.baseTemplate;

        console.println($"ct={ct} path={ct.path} display={ct.displayName}");
        console.println(ct.containingFile.mainModule.moduleNamesByPath);

        Type type = DBCategory;
        TypeTemplate tt = type.template;

        console.println($"tt={tt}");
        }
    }