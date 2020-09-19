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
    typedef (db.Transaction + AddressBookSchema) Transaction;

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


    // ---- AUTO-GENERATED DB MODULE ---------------------------------------------------------------

    static Connection simulateInjection()
        {
        return new AddressBookSchemaImpl();
        }

    /**
     * This is the thing that will get injected as Connection.
     */
    service AddressBookSchemaImpl
            extends IMDBRootSchemaImpl
            implements Connection
        {
        construct()
            {
            construct IMDBRootSchemaImpl();
            }
        finally
            {
            contacts   = new ContactsImpl();
            dbChildren = new HashMap();
            dbChildren.put(contacts.dbName, contacts);

            // TODO: user
            }

        @Override
        @Atomic @Unassigned Contacts contacts;

        @Override
        @Unassigned db.DBUser user;

        @Override
        public/protected TransactionImpl? transaction;

        @Override
        TransactionImpl createTransaction(
                    Duration? timeout = Null, String? name = Null,
                    UInt? id = Null, db.Transaction.Priority priority = Normal,
                    Int retryCount = 0)
            {
            return new TransactionImpl();
            }

        class ContactsImpl
                extends IMDBMapImpl<String, Contact>
                incorporates Contacts
            {
            construct()
                {
                construct IMDBMapImpl(this.AddressBookSchemaImpl, "contacts");
                }

            @Override
            Tuple dbInvoke(String | Function fn, Tuple args = Tuple:(), (Duration|DateTime)? when = Null)
                {
                if (fn == "addPhone" && when == Null)
                    {
                    assert args.is(Tuple<String, Phone>);

                    return addPhone(args[0], args[1]);
                    }
                throw new UnsupportedOperation(fn.toString());
                }
            }

        // REVIEW should this be a service?
        class TransactionImpl
                implements db.Transaction<AddressBookSchema>
                delegates AddressBookSchema(schema)
            {
            construct()
                {
                this.schema = this.AddressBookSchemaImpl;
                }

            private AddressBookSchema schema;

            @Override
            Boolean commit()
                {
                TODO
                }

            @Override
            void rollback()
                {
                }

            @Override
            void addCondition(db.Condition condition)
                {
                }
            }
        }


    // ----- InMemory_OODB_Impl (IMDB) -------------------------------------------------------------

    @Abstract class IMDBRootSchemaImpl
            extends IMDBObjectImpl
            implements db.RootSchema
        {
        construct()
            {
            construct IMDBObjectImpl(Null, "");
            }

        @Override
        @Unassigned
        public/private db.SystemSchema sys; // TODO
        }

    @Abstract class IMDBMapImpl<Key extends immutable Const, Value extends immutable Const>
            extends IMDBObjectImpl
            implements db.DBMap<Key, Value>
            delegates Map<Key, Value>(map)
        {
        construct(db.DBSchema schema, String name)
            {
            construct IMDBObjectImpl(schema, name);

            map = new HashMap<Key, Value>();
            }

        @Override
        @RO Collection<Value> values.get()
            {
            return new Array<Value>(Constant, map.values);
            }

        private Map<Key, Value> map;
        }

    @Abstract class IMDBObjectImpl
            implements db.DBObject
        {
        construct(db.DBObject? parent, String name)
            {
            dbParent   = parent;
            dbName     = name;
            dbChildren = Map:[];
            }

        @Override
        public/private db.DBObject? dbParent;

        @Override
        public/private String dbName;

        @Override
        public/protected Map<String, db.DBObject> dbChildren;
        }
    }