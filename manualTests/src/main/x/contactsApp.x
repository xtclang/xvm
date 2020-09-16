module ContactsApp
    {
    package db import ContactsDB;

    void run()
        {
        @Inject Console console;
        // @Inject db.Connection dbc;

        db.Connection dbc = db.simulateInjection();

        dbc.contacts.addContact(new db.Contact("George", "Washington"));
        dbc.contacts.addContact(new db.Contact("John", "Adams"));

        console.println("Contacts:");
        for (db.Contact contact : dbc.contacts.values)
            {
            console.println(contact);
            }
        }

    // ----- AUTO-GENERATED PROXY ------------------------------------------------------------------

    }
