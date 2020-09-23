module AddressBookApp
    {
    package db import AddressBookDB;

    void run()
        {
        @Inject Console console;
        // @Inject db.Connection dbc;

        db.Connection dbc = db.simulateInjection();

        db.Contacts contacts = dbc.contacts;

        db.Contact george = new db.Contact("George", "Washington");
        db.Contact john   = new db.Contact("John", "Adams");

        contacts.put(george.rolodexName, george);
        contacts.addContact(john);

        using (val tx = dbc.createTransaction())
            {
            tx.contacts.addPhone(george.rolodexName, new db.Phone(Work, "202-555-0000"));
            tx.contacts.addPhone(george.rolodexName, new db.Phone(Home, "202-555-0001"));
            }

        console.println("Contacts:");
        for (db.Contact contact : contacts.values)
            {
            console.println(contact);
            }
        }

    // ----- AUTO-GENERATED PROXY ------------------------------------------------------------------


    const ConnectionFront(db.Connection backConnection)
            implements db.Connection
        {

        }
    }
