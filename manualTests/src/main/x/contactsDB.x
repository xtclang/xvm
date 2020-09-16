module ContactsDB
    {
    package db import OODB.xtclang.org;

    interface Schema
            extends db.DBSchema
        {
        @RO Contacts contacts;
        }

    /**
     * This is the thing that will get injected.
     */
    typedef (db.Connection<Schema> + Schema) Connection;

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
        return new SchemaImpl();
        }

    service SchemaImpl
            implements Connection
        {
        construct()
            {
            }
        finally
            {
            contacts = new ContactsImpl(this);
            }

        @Override
        TransactionImpl createTransaction(Duration? timeout = Null, String? name = Null,
                                          UInt? id = Null, db.Transaction.Priority priority = Normal,
                                          Int retryCount = 0)
            {
            return new TransactionImpl(this);
            }

        @Override
        conditional TransactionImpl currentTransaction()
            {
            TransactionImpl? tx = currentTx;
            return tx == Null ? False : (True, tx);
            }

        private TransactionImpl? currentTx;
        }

    class TransactionImpl
            implements db.Transaction
            delegates Schema(schema)
        {
        construct(Schema schema)
            {
            this.schema = schema;
            }

        private Schema schema;

        @Override
        Boolean commit()
            {
            TODO
            }

        @Override
        void rollback()
            {
            }
        }

    class ContactsImpl
            extends DBMapImpl<String, Contact>
            incorporates Contacts
        {
        construct(Schema schema)
            {
            construct DBMapImpl(schema, "Contacts");
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


    // ----- InMemory_OODB_Impl (IMDB) -------------------------------------------------------------

    @Abstract class DBObjectImpl
            implements db.DBObject
        {
        construct(db.DBObject? parent, String name)
            {
            dbParent   = parent;
            dbName     = name;
            dbChildren = new HashMap();
            }

        @Override
        public/private db.DBObject? dbParent;

        @Override
        public/private String dbName;

        @Override
        Map<String, db.DBObject> dbChildren;
        }

    @Abstract class DBMapImpl<Key extends immutable Const, Value extends immutable Const>
            extends DBObjectImpl
            implements db.DBMap<Key, Value>
            delegates Map<Key, Value>(map)
        {
        construct(db.DBSchema schema, String name)
            {
            construct DBObjectImpl(schema, name);

            map = new HashMap<Key, Value>();
            }

        private Map<Key, Value> map;
        }
    }