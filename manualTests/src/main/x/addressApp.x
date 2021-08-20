module AddressBookApp
    {
    package db import AddressBookDB;

    void run()
        {
        @Inject Console console;
        @Inject Clock clock;
        @Inject db.Connection dbc;

        console.println($"actualType=({&dbc.actualType})\n");

        console.println(dbc.title.get());
        dbc.title.set($"My Contacts as of {clock.now.time.minute}");
        console.println(dbc.title.get());

//        db.Contacts contacts = dbc.contacts;
//
//        db.Contact george = new db.Contact("George", "Washington");
//        db.Contact john   = new db.Contact("John", "Adams");
//
//        contacts.put(george.rolodexName, george);
//        contacts.addContact(john);
//
//        dbc.requestCount.set(2);
//
//        dbc.title.set("My Contacts");
//
//        using (val tx = dbc.createTransaction())
//            {
//            tx.contacts.addPhone(george.rolodexName, new db.Phone(Work, "202-555-0000"));
//            tx.contacts.addPhone(george.rolodexName, new db.Phone(Home, "202-555-0001"));
//            tx.requestCount.adjustBy(1);
//            }
//
//        console.println(dbc.title.get());
//        for (db.Contact contact : contacts.values)
//            {
//            console.println(contact);
//            }
//        console.println($"Count: {dbc.requestCount.get()}");
        }
    }
