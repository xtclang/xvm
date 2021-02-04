module TestSimple.test.org
    {
    @Inject Console console;

    package db import oodb.xtclang.org;

    import ecstasy.reflect.ClassTemplate;
    import ecstasy.reflect.ComponentTemplate;
    import ecstasy.reflect.PropertyTemplate;
    import ecstasy.reflect.TypeTemplate;

    import db.DBObject.DBCategory;
    import P1.P2.jdb.Catalog;


    // TestSimple.test.org as Class                     as ClassTemplate
    //    path    = TestSimple.test.org:                     ditto
    //    name    = TestSimple                               ditto
    //    display = TestSimple.test.org:                     TestSimple
    //    q-name  = TestSimple.test.org

    // db.DBObject.DBCategory as Class                  as ClassTemplate
    //    path    = oodb.xtclang.org:DBObject.DBCategory     ditto
    //    name    = DBCategory                               ditto
    //    display = db.DBObject.DBCategory                   db.DBObject.DBCategory

    // P1.P2.Point2 as Class                            as ClassTemplate
    //    path    = TestSimple.test.org:P1.P2.Point2         ditto
    //    name    = Point2                                   ditto
    //    display = P1.P2.Point2                             P1.P2.Point2

    // Catalog as Class                                  as ClassTemplate
    //    path    = jsondb.xtclang.org:Catalog               ditto
    //    name    = Catalog                                  ditto
    //    display = P1.P2.jdb.Catalog                        P1.P2.jdb.Catalog
    void run()
        {
        display(TestSimple, TestSimple);
        display(Point, Point);
        display(P1.P2.Point2, P1.P2.Point2);
        display(Catalog, Catalog);
        display(DBCategory, DBCategory);
        }

    void display(Class clz, Type type)
        {
        ClassTemplate ct = clz.baseTemplate;
        String s = clz.name;

        console.println($"class        ={clz} path={clz.path} name={clz.name} display={clz.displayName}");
        console.println($"classTemplate={ct} path={ct.path} name={ct.name} display={ct.displayName}");

        TypeTemplate tt = type.template;
        console.println($"type         ={type}");
        console.println($"typeTemplate ={tt}");

        console.println($"containingModule={ct.containingModule}\n");
        }

    const Point(Int x, Int y);

    package P1
        {
        package P2
            {
            package jdb import jsondb.xtclang.org;

            const Point2<T extends Number>(T x);
            }
        }
    }