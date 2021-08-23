/**
 * This is a database test application for the AddressBook test schema.
 *
 * To compile this test:
 *
 *     gradle compileOne -PtestName=addressApp
 *
 * To run this test using the "imdb" database implementation:
 *
 *     gradle -Dxvm.db.impl=imdb hostOne -PtestName=AddressBookApp
 *
 * To run this test using the "jsonDB" database implementation:
 *
 *     gradle -Dxvm.db.impl=json hostOne -PtestName=AddressBookApp
 *
 * See also: addressDB.x file for the database schema.
 */
module AddressBookApp
    {
    package db import AddressBookDB;

    void run()
        {
        @Inject Console console;
        @Inject Clock clock;
        @Inject db.Connection dbc;

        console.println($"actualType=({&dbc.actualType})\n");

//        db.Contacts contacts = dbc.contacts;
//
//        db.Contact george = new db.Contact("George", "Washington");
//        db.Contact john   = new db.Contact("John", "Adams");
//
//        contacts.put(george.rolodexName, george);
//        contacts.addContact(john);
//
        dbc.title.set($"My Contacts as of {clock.now.time.minute}");
        dbc.requestCount.adjustBy(2);

//
//        using (val tx = dbc.createTransaction())
//            {
//            tx.contacts.addPhone(george.rolodexName, new db.Phone(Work, "202-555-0000"));
//            tx.contacts.addPhone(george.rolodexName, new db.Phone(Home, "202-555-0001"));
//            tx.requestCount.adjustBy(1);
//            }
//
        console.println(dbc.title.get());
//        for (db.Contact contact : contacts.values)
//            {
//            console.println(contact);
//            }
        console.println($"Count: {dbc.requestCount.get()}");
        }
    }
