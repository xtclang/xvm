/**
 * A simple database of people.
 *
 * This database also includes a simple test suite that will use XUnit DB to create instances of
 * this database for the tests to use.
 * The tests can be executed from a command line inside the manualTests directory:
 *
 *    xtc test -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/PeopleDB.x
 *
 */
@Database
module PeopleDB {
    package oodb    import oodb.xtclang.org;
    package xunitdb import xunit_db.xtclang.org;

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
                 String? middleName,
                 String? lastName,
                 String? phone      = Null,
                 String? email      = Null,
                ) {

        Person with(String? nickName   = Null,
                    String? firstName  = Null,
                    String? middleName = Null,
                    String? lastName   = Null,
                    String? phone      = Null,
                    String? email      = Null,
                   ) {
            return new Person(
                nickName   = nullableString(nickName   ?: this.nickName),
                firstName  = nullableString(firstName  ?: this.firstName),
                middleName = nullableString(middleName ?: this.middleName),
                lastName   = nullableString(lastName   ?: this.lastName),
                phone      = nullableString(phone      ?: this.phone),
                email      = nullableString(email      ?: this.email),
            );
        }

        String displayName.get() {
            StringBuffer buf = new StringBuffer();
            if (String name ?= firstName, !name.empty) {
                name.appendTo(buf);
            }
            if (String name ?= middleName, !name.empty) {
                if (!buf.empty) {
                    buf += ' ';
                }
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
        String toString() {
            String s = displayName;
            if (phone != Null) {
                s = $"{s}\n    phone: {phone}";
            }
            if (email != Null) {
                s = $"{s}\n    email: {email}";
            }
            return s;
        }
    }

    // ----- testing -------------------------------------------------------------------------------

    /**
     * Simple tests for the Contacts schema using XUnit DB.
     */
    class AddPersonTests {

        typedef (oodb.Connection<Contacts>  + Contacts) as Connection;

        @Inject Connection conn;

        @Test
        void shouldAddPerson() {
            Person person = new Person("Foo", "One", "Two", "Three");
            Int    id     = conn.people.add(person);
            assert Person fromDb := conn.people.get(id);
            assert fromDb == person;
        }

        @Test
        void shouldUseCounterForPersonKey() {
            DBCounter counter = conn.personKey;
            Int       before  = counter.get();
            Int       id      = conn.people.add(new Person("Foo", "One", "Two", "Three"));
            assert id == before;
            assert counter.get() == before + 1;
        }
    }
}