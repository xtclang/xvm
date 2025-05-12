/**
 * PeopleDB v1.
 *
 * To compile, from "./manualTests/" directory:
 *      xcc -L build/xtc/main/lib -o lib/v1 --set-version 1.0 src/main/x/dbTests/v1/PeopleDB.x
 */
@Database
module PeopleDB {
    package oodb import oodb.xtclang.org;

    import oodb.*;

    /**
     * Database schema.
     */
    interface Contacts
            extends RootSchema {
        /**
         * Table containing the people.
         */
        @RO People people;

        /**
         * Unique key generator for "people".
         */
        @RO DBCounter personKey;
    }

    /**
     * The "People" table.
     */
    mixin People
            into DBMap<Int, Person> {
        Int add(Person person) {
            Int key = dbCounterFor(/personKey).next();
            assert putIfAbsent(key, person);
            return key;
        }
    }

    /**
     * The "Person" structure that goes into the "People" table.
     */
    const Person(String? nickName,
                 String? firstName,
                 String? lastName,
                ) {

        Person with(String? nickName   = Null,
                    String? firstName  = Null,
                    String? lastName   = Null,
                   ) {
            return new Person(
                nickName   = nullableString(nickName   ?: this.nickName),
                firstName  = nullableString(firstName  ?: this.firstName),
                lastName   = nullableString(lastName   ?: this.lastName),
            );
        }

        String displayName.get() {
            StringBuffer buf = new StringBuffer();
            if (String name ?= firstName, !name.empty) {
                name.appendTo(buf);
            }
            if (String name ?= nickName, !name.empty) {
                if (!buf.empty) {
                    buf += ' ';
                }
                name.quoted().appendTo(buf);
            }
            if (String name ?= lastName, !name.empty) {
                if (!buf.empty) {
                    buf += ' ';
                }
                name.appendTo(buf);
            }
            return buf.empty ? "<no-name>" : buf.toString();
        }

        static String? nullableString(String? s) {
            return (s?.empty : False) ? Null : s;
        }

        @Override
        String toString() = displayName;
    }
}