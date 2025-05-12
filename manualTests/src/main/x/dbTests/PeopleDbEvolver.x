/**
 * A stand-alone db evolution facility.
 *
 * To run, from "./manualTests/" directory:
 *      xec -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/DbEvolver.x
 */
module PeopleDbEvolver {
    package json   import json.xtclang.org;
    package jsondb import jsondb.xtclang.org;
    package oodb   import oodb.xtclang.org;

    import oodb.Connection;

    import json.Doc;
    import json.JsonObject;
    import jsondb.evolution.JsonEvolver;

    @Inject Console console;

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

    class EvolverV1_V2(Connection oldDb, Connection newDB)
            extends JsonEvolver(oldDb, newDB) {
        @Override
        protected conditional (Doc, Doc) transformMapEntry(Path path, Doc key, Doc value) {
            if (path == Path:/people) {
                JsonObject person = value.as(JsonObject);
                person["middleName"] = person.getOrNull("nickName");
                return (True, key, person);
            }
            return False;
        }
    }
}
