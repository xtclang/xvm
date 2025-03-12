/**
 * A stand-alone test for PeopleDB.
 *
 * To run, from "./manualTests/" directory:
 *      xcc -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/PeopleDB.x
 *      xec -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/dbTests/PeopleTest.x
 */
module PeopleTest
        incorporates TerminalAppMixin("People DB Test") {

    package cli      import cli.xtclang.org;
    package oodb     import oodb.xtclang.org;
    package jsondb   import jsondb.xtclang.org;
    package peopleDB import PeopleDB;

    import cli.*;
    import oodb.*;
    import peopleDB.*;

    @Inject Console console;

    @Override
    void run(String[] args) = Runner.run(this, args, shutdown=shutdown);

    @Lazy (Connection<Contacts> + Contacts) contacts.calc() {
        @Inject Directory curDir;
        assert curDir.fileFor("src/main/x/dbTests/PeopleDB.x").exists
                as "Not in \"manualTests\" directory";

        Directory buildDir = curDir.dirFor("build/xtc/main/lib");
        assert buildDir.fileFor("PeopleDB.xtc").exists
                as "PeopleDB must be compiled to the build/xtc/main/lib directory";

        Directory dataDir = curDir.dirFor("data/peopleDB").ensure();
        return jsondb.createConnection("PeopleDB", dataDir, buildDir).as(Connection<Contacts> + Contacts);
    }

    void shutdown() {
        contacts.close();
    }

    // ----- stateless API -------------------------------------------------------------------------

    @Command("list", "Show list of contacts")
    void listPeople() {
        using (contacts.createTransaction(readOnly=True)) {
            val people = contacts.people;
            if (people.empty) {
                console.print("No contacts. (Use the \"add\" command to add a contact.)");
            } else {
                for ((Int key, Person person) : people) {
                    console.print($"{key} : {person}");
                }
                console.print($"{people.size} contact(s).");
            }
        }
    }

    @Command("find", "Find one or more contacts containing the specified text")
    void findPeople(String text) {
        using (contacts.createTransaction(readOnly=True)) {
            val people = contacts.people;
            Int found = 0;
            text = text.toLowercase();
            for ((Int key, Person person) : people) {
                if (person.toString().toLowercase().indexOf(text)) {
                    ++found;
                    console.print($"{key} : {person}");
                }
            }
            console.print($"{found} contact(s) found.");
        }
    }

    @Command("add", "Add a contact; specify up to three (first/middle/last) names")
    void addPerson(String first, String? middle = Null, String? last = Null) {
        if (last == Null && middle != Null) {
            last   = middle;
            middle = Null;
        }
        Person person = new Person(Null, first, middle, last);
        Int key = contacts.people.add(person);
        console.print("Added Person to database:");
        console.print($"{key} : {person}");
    }

    @Command("remove", "Remove a contact by its key")
    void remove(Int key) {
        using (contacts.createTransaction()) {
            val people = contacts.people;
            if (Person person := people.get(key)) {
                people.remove(key);
                console.print("Removed Person from database:");
                console.print($"{key} : {person}");
            } else {
                console.print($"No such Person {key} in the database.");
            }
        }
    }

    @Command("first", "Change or set or remove a first name")
    void setFirst(Int key, String name) {
        using (contacts.createTransaction()) {
            modify(key, p -> p.with(firstName=name));
        }
    }

    @Command("middle", "Change or set or remove a middle name")
    void setmiddle(Int key, String name) {
        modify(key, p -> p.with(middleName=name));
    }

    @Command("last", "Change or set or remove a last name")
    void setlast(Int key, String name) {
        modify(key, p -> p.with(lastName=name));
    }

    @Command("nick", "Change or set or remove a nickname")
    void setNick(Int key, String name) {
        modify(key, p -> p.with(nickName=name));
    }

    @Command("phone", "Change or set or remove a phone number")
    void setPhone(Int key, String text) {
        modify(key, p -> p.with(phone=text));
    }

    @Command("email", "Change or set or remove an email address")
    void setEmail(Int key, String text) {
        modify(key, p -> p.with(email=text));
    }

    void modify(Int key, function Person(Person) modify) {
        using (contacts.createTransaction()) {
            val people = contacts.people;
            if (Person person := people.get(key)) {
                console.print("Before:");
                console.print($"{key} : {person}");
                person = modify(person);
                people.put(key, person);
                console.print("After:");
                console.print($"{key} : {person}");
            } else {
                console.print($"No such Person {key} in the database.");
            }
        }
    }
}