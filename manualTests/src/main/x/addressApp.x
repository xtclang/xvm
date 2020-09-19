module AddressBookApp
    {
    package db import AddressBookDB;

    void run()
        {
        @Inject Console console;
        // @Inject db.Connection dbc;

        db.Connection dbc = db.simulateInjection();

//        using (val tx = dbc.createTransaction())
//            {
//            tx.contacts.addContact(new db.Contact("George", "Washington"));
//            tx.contacts.addContact(new db.Contact("John", "Adams"));
//            }

        db.Contacts contacts = dbc.contacts;

        contacts.addContact(new db.Contact("George", "Washington"));
        contacts.addContact(new db.Contact("John", "Adams"));

        console.println("Contacts:");
        for (db.Contact contact : contacts.values)
            {
            console.println(contact);
            }
        }

    // ----- AUTO-GENERATED PROXY ------------------------------------------------------------------

    }
