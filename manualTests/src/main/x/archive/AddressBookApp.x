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

    import db.Connection;
    import db.Contacts;
    import db.Contact;
    import db.Phone;

    void run()
        {
        @Inject Console console;
        @Inject Clock clock;
        @Inject Connection dbc;

        Contacts contacts = dbc.contacts;

        Contact george = new Contact("George", "Washington");
        Contact john   = new Contact("John", "Adams");

        using (val tx = dbc.createTransaction())
            {
            if (contacts.size > 0)
                {
                contacts.remove(george.rolodexName);
                contacts.remove(john.rolodexName);
                }

            contacts.put(george.rolodexName, george);
            contacts.addContact(john);

            dbc.title.set($"My Contacts as of {clock.now.timeOfDay}");
            dbc.requestCount.adjustBy(2);
            }

        using (val tx = dbc.createTransaction())
            {
            tx.contacts.addPhone(george.rolodexName, new Phone(Work, "202-555-0000"));
            tx.contacts.addPhone(john.rolodexName, new Phone(Home, "202-555-0001"));
            tx.requestCount.adjustBy(1);
            }

        using (val tx = dbc.createTransaction())
            {
            console.print(dbc.title.get());
            for (Contact contact : contacts.values)
                {
                console.print(contact);
                }
            console.print($"Count: {dbc.requestCount.get()}");
            }
        }
    }
