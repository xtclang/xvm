module AddressBookDB
    {
    package db import OODB.xtclang.org;

    interface AddressBookSchema
            extends db.RootSchema
        {
        @RO Contacts contacts;
        }

    /**
     * This is the interface that will get injected.
     */
    typedef (db.Connection<AddressBookSchema> + AddressBookSchema) Connection;

    /**
     * This is the interface that will come back from createTransaction.
     */
    typedef (db.Transaction<AddressBookSchema> + AddressBookSchema) Transaction;

    mixin Contacts
            into db.DBMap<String, Contact>
        {
        // TODO @DBOp tag?
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
        @db.PKey String rolodexName.get()
            {
            return lastName + ", " + firstName;
            }

        String fullName.get()
            {
            return firstName + ' ' + lastName;
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

    // !!! TEMPORARY !!!
    static Connection simulateInjection()
        {
        return ServerAddressBookSchema.createConnection();
        }
    }