/**
 * A stand-alone db evolution facility.
 *
 * To run, from "./manualTests/" directory:
 *      xec -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/DbEvolver2.x
 */
module PeopleDbEvolver2 {
    package oodb   import oodb.xtclang.org;

    import oodb.*;
    import oodb.evolution.AbstractEvolver;

    @Inject Console console;

    package v1 import PeopleDB v:1.0;
    package v2 import PeopleDB v:2.0;

    void run() {
        @Inject Console   console;
        @Inject Directory curDir;

        v1.Person p1 = new v1.Person(Null, "Joe", "Doe");
        v2.Person p2 = new v2.Person(Null, "Joe", "Middle", "Doe");

        console.print(p1);
        console.print(p2);

//        Directory buildDirOld = curDir.dirFor("lib/v1");
//        assert buildDirOld.fileFor("PeopleDB.xtc").exists;
//
//        Directory buildDirNew = curDir.dirFor("lib/v2");
//        assert buildDirNew.fileFor("PeopleDB.xtc").exists;
//
//        Directory dataDirOld = curDir.dirFor("data/peopleDB.v1").ensure();
//        Directory dataDirNew = curDir.dirFor("data/peopleDB.v2").ensure();
//
//        Connection oldDB = jsondb.createConnection("PeopleDB", dataDirOld, buildDirOld);
//        Connection newDB = jsondb.createConnection("PeopleDB", dataDirNew, buildDirNew);
//
//        try {
//            new EvolverV1_V2(oldDB, newDB).evolve();
//        } catch (Exception e) {
//            console.print($|*** Failed to evolve the DB; {e}
//                           |*** please remove the {dataDirNew} directory
//                         );
//        } finally {
//            oldDB.close();
//            newDB.close();
//        }
    }

//    class EvolverV1_V2(Connection oldDB, Connection newDB)
//            extends AbstractEvolver(oldDB, newDB) {
//
//        @Override
//        protected void evolveDBMap(DBMap oldDBMap, DBMap newDBMap) {
//
//            for ((Const oldKey, Const oldVal) : oldDBMap) {
//                v1.Person p1 = oldVal.as(v1.Person);
//                v2.Person p2 = new v2.Person(
//                    p1.nickName,
//                    p1.firstName,
//                    p1.nickName, // prime the new (middleName) property with (nickName)
//                    p1.lastName);
//                newDBMap.put(oldKey.as(Int), p2);
//           }
//        }
//
//        @Override
//        protected void evolveDBValue(DBValue oldDBValue, DBValue newDBValue) {
//            TODO
//        }
//    }
}
