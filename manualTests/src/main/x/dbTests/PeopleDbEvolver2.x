/**
 * A stand-alone db evolution facility.
 *
 * To run, from "./manualTests/" directory:
 *      xec -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/DbEvolver2.x
 */
module PeopleDbEvolver2 {
    package oodb   import oodb.xtclang.org;

    import oodb.Connection;
    import oodb.evolution.Evolver;

    @Inject Console console;

    package v1 import peopleDB require v:1.0;
    package v2 import peopleDB require v:2.0;

    void run() {
        @Inject Console   console;
        @Inject Directory curDir;

        Directory buildDirOld = curDir.dirFor("lib/v1");
        assert buildDirOld.fileFor("PeopleDB.xtc").exists;

        Directory buildDirNew = curDir.dirFor("lib/v2");
        assert buildDirNew.fileFor("PeopleDB.xtc").exists;

        Directory dataDirOld = curDir.dirFor("data/peopleDB.v1").ensure();
        Directory dataDirNew = curDir.dirFor("data/peopleDB.v2").ensure();

        Connection oldDB = jsondb.createConnection("PeopleDB", dataDirOld, buildDirOld);
        Connection newDB = jsondb.createConnection("PeopleDB", dataDirNew, buildDirNew);

        try {
            new EvolverV1_V2(oldDB, newDB).evolve();
        } catch (Exception e) {
            console.print($|*** Failed to evolve the DB; {e}
                           |*** please remove the {dataDirNew} directory
                         );
        } finally {
            oldDB.close();
            newDB.close();
        }
    }


    class EvolverV1_V2(Connection oldDB, Connection newDB)
            extends Evolver(oldDb, newDB) {
    protected void evolveDBMap(DBMap oldDBMap, DBMap newDBMap);

    /**
     * Evolve an old `DBValue` into a new one.
     */
    @Abstract
    protected void evolveDBValue(DBValue oldDBValue, DBValue newDBValue);

    }
    v1.Person p1 = ...;
    v2.Person p2 = new v2.Person(p1.firstName, p1.lastName, middleName="whatever new", ...);
}
