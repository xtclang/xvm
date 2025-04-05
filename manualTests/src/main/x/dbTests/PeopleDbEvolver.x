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
    import json.*;
    import jsondb.evolution.*;

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

        Connection oldConn = jsondb.createConnection("PeopleDB", dataDirOld, buildDirOld);
        Connection newConn = jsondb.createConnection("PeopleDB", dataDirNew, buildDirNew);

        try {
            new EvolverV1_V2().evolve(oldConn, newConn);
        } catch (Exception e) {
            console.print($|*** Failed to evolve the DB; {e}
                           |*** please remove the {dataDirNew} directory
                         );
        } finally {
            oldConn.close();
            newConn.close();
        }
    }

    class EvolverV1_V2
            extends SimpleEvolver {
        @Override
        conditional (Doc, Doc) transformMapEntry(Context context, Path path, Doc key, Doc value) {
            if (path == Path:/people) {
                JsonObject person = value.as(JsonObject);
                person["middleName"] = person.getOrNull("nickName");
                return (True, key, person);
            }
            return False;
        }
    }
}
