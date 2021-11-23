/**
 * This is a database schema for the AddressBook test.
 *
 * To compile this test:
 *
 *     gradle compileOne -PtestName=addressDB
 *
 * See the addressApp.x file for the test program that uses this database schema.
 */
module AddressBookDB
        incorporates Database
    {
    package oodb import oodb.xtclang.org;

    import oodb.Connection;
    import oodb.Database;
    import oodb.DBCounter;
    import oodb.DBMap;
    import oodb.DBValue;
    import oodb.Initial;
    import oodb.NoTx;
    import oodb.Transaction;
    import oodb.RootSchema;

    interface AddressBookSchema
            extends RootSchema
        {
        @RO Contacts contacts;
        @RO @NoTx DBCounter requestCount;
        @RO @Initial("Untitled") DBValue<String> title;
        }

    /**
     * This is the interface that will get injected.
     */
    typedef (Connection<AddressBookSchema> + AddressBookSchema) Connection;

    /**
     * This is the interface that will come back from createTransaction.
     */
    typedef (Transaction<AddressBookSchema> + AddressBookSchema) Transaction;

    mixin Contacts
            into DBMap<String, Contact>
        {
        void addContact(Contact contact)
            {
            String name = contact.rolodexName;
            if (contains(name))
                {
                throw new IllegalState($"already exists {name}");
                }
            put(name, contact);
            }

        void addPhone(String name, Phone phone)
            {
            if (Contact oldContact := get(name))
                {
                Contact newContact = oldContact.withPhone(phone);
                put(name, newContact);
                }
            else
                {
                throw new IllegalState($"no contact {name}");
                }
            }
        }

    const Contact(String firstName, String lastName, Email[] emails = [], Phone[] phones = [])
        {
        // @PKey
        String rolodexName.get()
            {
            return $"{lastName}, {firstName}";
            }

        String fullName.get()
            {
            return $"{firstName} {lastName}";
            }

        Contact withPhone(Phone phone)
            {
            return new Contact(firstName, lastName, emails, phones + phone);
            }

        Contact addEmail(Email email)
            {
            return new Contact(firstName, lastName, emails + email, phones);
            }
        }

    enum EmailCat {Home, Work, Other}

    const Email(EmailCat category, String email);

    enum PhoneCat {Home, Work, Mobile, Fax, Other}

    const Phone(PhoneCat category, String number);
    }