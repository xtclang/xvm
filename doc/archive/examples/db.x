/**
 * Databases must be boot-strappable. That means that the first time an application is run (within
 * a particular context, for some definition of the word "context"), it must create the database.
 * Subsequent starts of the application will reload that previously-created database. (It must be
 * possible to create the database new and empty on each run, but that is the exception, not the
 * rule.)
 */
Database ensureAccountingDB()
    {
    @Inject Database? accounting;
    if (accounting != Null)
        {
        return accounting;
        }

    @Inject DatabaseBuilder builder;
    builder.name = "accounting";
    builder.add("accounts", Int, Account)                   // e.g. name, key type, value type
           .add("orders", Int, PurchaseOrder)
           .add("invoices", Int, Invoice)
           .add("vouchers", Int, Voucher)
           .add("cost_centers", Int, CostCenter);
    builder.populate("cost_centers",
            [
            new CostCenter(0, "Unassigned"),
            new CostCenter(1, "GL Adjustment")
            ]);
    builder.requireLog()
           .requireBackup();
    return builder.build();
    }

@Inject DatabaseBuilder builder;
builder.name = "tempdb";
builder.add("doc_upload", Int, json.Doc)
       .add("img_upload", String, Byte[]);
builder.ephemeral = True;
Database db = builder.build();

/**
 * Deployment tooling can also fill in gaps. For example, an app that says:
 *
 *     @Inject Database db;
 *
 * The deployment of that app can involve a step of identifying what database needs to be "attached"
 * to the name "db". That could be as simple as saying "use an empty database if one doesn't already
 * exist". It could also allow the deployer to indicate an existing (non-embedded) database to use.
 *
 * In order to "ensure a database" (create and populate the database the first time the app is run),
 * it may be helpful to split the database-configuration code into its own unit (perhaps with some
 * sort of naming convention or annotation):
 */
@DBConfig("accounting")
service SetUpAccountingDatabase
    {
    void configureDatabase(DatabaseBuilder builder)
        {
        // one would assume that this has already been done:
        //   builder.name = "accounting";

        builder.add("accounts", Int, Account)
               .add("orders", Int, PurchaseOrder)
               .add("invoices", Int, Invoice)
               .add("vouchers", Int, Voucher)
               .add("cost_centers", Int, CostCenter);
        builder.populate("cost_centers",
                [
                new CostCenter(0, "Unassigned"),
                new CostCenter(1, "GL Adjustment")
                ]);
        builder.requireLog()
               .requireBackup();
        }
    }

/**
 * Databases are separable resources. That means that a database could exist as a resource outside
 * of any one specific application.
 *
 * They're also embeddable resources. That means that a database could exist only within a single
 * application, and it would not be visible outside of that application.
 *
 * And they have to be movable from one to the other. An embedded database needs to be able to be
 * "promoted" (un-embedded) from an application, and an existing standalone database that is used
 * by only a single application (or by no application) needs to be migratable to an embedded form
 * within an application.
 */
if (db.embedded)
    {
    db.unembed();
    }

/**
 * Maybe a database is a module. Or said a different way, a database usable in a non-embedded manner
 * could be organized as a module. That way, all of the types that the db depends on can be in the
 * module "with" the database.
 */
@DatabaseModule
module AccountingDB
    {
    String name.get()
        {
        return "accounting";
        }

    void configureDatabase(DatabaseBuilder builder)
        {
        // ...
        }
    }

/**
 * Configuration must be data.
 *
 * In other words, anything that is done in configuration must be stored as data within the
 * database. Settings are all data, but so is the act of changing a setting. (Configuration
 * history.)
 *
 * But it also means that the result configuration document is capable of reconstructing the
 * entirety of the database design:
 */
@DatabaseModule
module AccountingDB
    {
    private json.Doc config.get()
        {
        //...
        }

    void configureDatabase(DatabaseBuilder builder)
        {
        builder.apply(config);
        }
    }

class AnyRandomClass
    {
    json.Doc dbConfig.get()
        {
        @Inject Database accounting;
        return accounting.config.toDoc();
        }
    }

/**
 * Databases must be versionable.
 *
 * This means that configuration changes are associated with a version.
 *
 * A database must be able to be rolled forward (upgraded) to a newer version, or rolled back to an
 * older version.
 */
Version ver = db.version;
// ...
db.upgradeTo(newVer);
// ...
db.rollbackUpgradeTo(ver);

/**
 * The reason that the database must be explicitly versioned is that as an application is modified,
 * the persistent form of the data for that application may be affected. The upgrade to the database
 * is the "effect" that needs to occur to make the data stored in the database compatible with the
 * new version of the application.
 *
 * An upgrade can be performed offline (in total), or incrementally online. It may even be something
 * that can be simulated without performing the upgrade, so that an A/B test between the old and
 * the new version can be used, and so that all of the servers running the application can move to
 * the new code before actually switching over the data to the new version.
 *
 * As importantly, it must be possible to roll back an upgrade, because sometimes things go wrong
 * during an upgrade. To achieve this, the migration must be designed in a bidirectional fashion.
 *
 * The challenge is how to represent the data (and associated code) in a manner that allows code
 * to transform from one form to the other. It is as if, for each data type that is modified in a
 * new version, there exists two functions:
 *
 *     newform = f(oldform)
 *     oldform = f-1(newform)
 *
 * Yet, from a code POV, both oldform and newform are likely the same class identity -- albeit two
 * different versions of that class. Should it be possible to use those classes to manipulate the
 * data? Or should the transformation functions only be permitted to work with the DOM of the
 * underlying data?
 *
 * It may be possible to represent the two different versions of the data-related module as two
 * different packages within a single "migration" module:
 */
module Migrate1_0_5to2_0
    {
    // I don't think that multiple different versions of the same module can be imported today;
    // this is currently likely to be an error (or should be)
    package db1_0_5 import:required AccountingDB v1.0.5.0.0.0;
    package db2_0   import:required AccountingDB v2.0.0.0.0.0;
    }

/**
 * It is important to be able to monitor and manage the size of the database.
 *
 * Backups could be largely automated by a platform implementation, but in the absence of full
 * automation (or based on some other need), it should be possible create a backup explicitly.
 * Storage size should be queryable, and to some extent manageable. For example, transaction logs
 * should be truncatable (e.g. by date), caches and indexes should be measurable and clearable /
 * compactable / prunable / flushable.
 */
Int total      = db.storage.totalSize;
Int backups    = db.storage.backupCount;
Int backupSize = db.storage.backupSize;

db.storage.compact();
db.storage.backup();
Map<DateTime, Backup> backups = db.storage.backups;

/**
 * Databases are tierable. This means that the "same" database can run across multiple datacenters,
 * somehow magically globally consistent, or can be running (in a subset form, drawing from the
 * back end) on a client device, or anything in-between, such as hanging-on-a-pole inside a 5g node.
 *
 * * Global - the app might be running in different data centers all around the world, but some data
 *   needs to be available (and consistent) everywhere the app is!
 * * Regional - must be able to partition by closeness to the point of highest utilization
 * * Jurisdictional - must be able to segregate data by legal edict
 * * Edge/CDN/Tower/Pole - caching the data closer to the end user (and potentially being able to
 *   operate on that data transactionally)
 * * Device - the app front end, running on a device, sharing some of the same code as is being
 *   utilized on the server, may have the ability to have a local view of "the database", like a
 *   cache, or it may even replicate the data for offline use (including queries and transactions).
 *
 * * Subsetted vs. Complete - is the data set a subset of the complete data set, or is it the
 *   complete data set?
 * * Partitionable vs. Monolith - is there a way of dividing an extent of data up using a
 *   predictable, repeatable (and consistent) function
 * *
 *
 * The trick is going to be to allow 99% of the consuming / producing code to be unchanged across
 * tiers, such that the same code can run on the client device, in the CDN, on the cloud, etc. That
 * means that the database API will be identical across tiers, and the database definition will
 * appear identical across tiers (except where that is purposefully avoided, e.g. for security
 * reasons).
 *
 * There are also use cases in which replication to tiers is important, e.g. for performance and
 * for offline access. This replication will need to behave in a persistent manner, i.e. the
 * reconnection of a client will need to send down the intersection of (i) only those items that
 * have changed, (ii) only those items that the client is allowed to have access to, and (iii)
 * only the portions of those items that the client is allowed to download (e.g. some user data
 * might be required by the client, but it won't carry password information down from the back end).
 *
 * There are also offline use cases in which data can be modified offline, and later submitted to
 * the back end when a connection is available.
 */

/**
 * An Ecstasy Database is a hierarchical concept. (Maybe it should be called a DataStore, a la
 * FileStore.) As such, it can be arranged like a directory hierarchy, and the "files" within that
 * hierarchy are one of a small set of forms:
 *
 * * Table. The most common form. A table is a collection of Ecstasy objects, each identified by
 *   an identity that is temporally unique within the table. A Table is a table of rows.
 *
 * * Log / Queue / List. An append-only data structure. Optionally FIFO (append-only, delete or
 *   take from front). This can be implemented on top of a table, but the semantics are such that
 *   a separate representation is warranted.
 *
 * * Other possibilities:
 * * * Single document? Like a file in a directory.
 * * * Columnar. An inverted tabular format; a table of columns. (Or is this just done automatically
 *     behind the scenes?)
 * * * Graph - any specialization for graph-db uses? Relationships as first class data?
 * * * Blob storage - any specialization for this use case? Photo uploads. Videos. Images. Whatever.
 */
typedef (NameSpace | Log | Table | Document) Entry;
interface NameSpace
    {
    Entry get(String name);
    }

/**
 * Schema design.
 *
 * This is the most complex thing to get a handle on. Languages, like Ecstasy, specialize in being
 * able to define arbitrarily complex and intertwined structures. There must be a away to naturally
 * design and organize a set of classes to compose a database from. The question is: How complex is
 * it to transform that "code" into a schema? And is this a bi-directional concept? For example,
 * could one begin by designing the schema, and from that, generating the code?
 *
 * The database is not intended as a point of integration, i.e. the database is not the center
 * of an architecture to be exposed willy-nilly, any more than raw read/write fields of a structure
 * should be used as a means of integration. While a database is an "active" participant (as opposed
 * to a structure's read/write field, which is purposefully passive), and while a database _could_
 * serve as a point of integration, we know from experience that using the data store as a point of
 * integration is fraught with risk. Specifically, because that is not its primary purpose, using
 * it as the point of integration opens up the private contents of the database without requiring
 * specific rules of mutation to be followed; while rules and triggers are often supported by
 * database engines, databases aren't typically designed expecting arbitrary access and manipulation
 * by unknown agents, and as such these forms of vulnerabilities will necessarily lead to errors --
 * particularly when employed by those who did not originally build the database and its related
 * application(s).
 *
 * So while it is necessary to allow multiple applications to share, access, and mutate a shared
 * database, it is not the purpose of the database design to act as a means of integration. For
 * example, we might have several modules built that consume and work with a single database, but
 * organizationally, we would consider that an anti-pattern, unless those modules (and the db) were
 * all being built and managed by the same team.
 *
 * The idea that a database should be designed (packaged, etc.) as a module does have a certain
 * attraction. Among other things, it allows the database to be versioned by leveraging some of the
 * same versioning capabilities that a module is designed for. And with the module being the
 * (or "a") compilation unit, it also opens up opportunities for code production, modification, etc.
 *
 * To explore these ideas, let's start by defining a module that contains a relatively simple
 * schema for running a basic e-commerce site:
 */
@DBSchema("ecommerce")  // <-- this is the default name of the database?
module MyShop
    {
    /**
     * In reality, we would support multiple emails, with one specified as primary, etc., but this
     * is just an example.
     *
     * The use of a "const" is convenient in some ways, and annoying (from a mutability standpoint)
     * in others. A few mutation (persistent-style) helpers are provided as examples. The nice thing
     * about a const is that it is likely to be JSON-able _almost_ by default.
     */
    // could say: @DBTable("users")
    // (instead of the table decl in the database below)
    const User(@DBKey Int id, String email, String address, String address2, String city, String state, String zip, String phone)
        {
        User updateEmail(String email)
            {
            return new User(id, email, address, address2, city, state, zip, phone);
            }
        User updateAddress(String address, String address2, String city, String state, String zip)
            {
            return new User(id, email, address, address2, city, state, zip, phone);
            }
        User updateEmail(String phone)
            {
            return new User(id, email, address, address2, city, state, zip, phone);
            }
        };

    const Item(@DBKey Int id, String stockNum, String desc, Dec price, Dec weight, @DBRelation(Image.uri) String[] imageURIs)
        {
        @RO @Abstract @DBLookup @Soft @Lazy         // <-- whoa, this is getting ridiculous
        Image[] images;
        }

    const Order(@DBKey Int id, Line[] lines)
        {
        static const Line(@DBRelation(Item.id) Int itemId, Int quantity, Dec quotedPrice)
            {
            @RO Dec extendedPrice.get()
                {
                return quantity * quotedPrice;
                }

            @RO @Abstract @DBLookup @Soft @Lazy
            Item item;
            }

        Dec total.get()
            {
            return lines.sum(extendedPrice); // TODO Stream re-do
            }
        }

    @DBTable("images")                         // this use of DBTable annotation seems redundant now
    const Image(@DBKey String uri, Byte[] image);

    @DBService
    service Database
        {
        /**
         * The database API has a relatively small number of critical responsibilities:
         * 1) enumerate and get references to tables (and lists/queues/logs)
         * 2) obtain configuration and schema info and metrics of the database
         * 3) alter the configuration or schema of the database, or perform management tasks related
         *    to the database
         */
        @RO @Abstract @DBTable Table<Int, User>  users; // could say @DBTable("users") but redundant in this case
        // TODO need example with multi-part key
        @RO @Abstract @DBTable Table<Int, Order> carts;
        @RO @Abstract @DBTable Table<Int, Item>  items;
        @RO @Abstract @DBTable Table<Int, Order> orders;
        @RO @Abstract @DBTable Table<Int, Order> images;

        // why would these each have a different annotation?
        @RO @Abstract @DBQueue Queue<Order>     ordersToProcess;
        @RO @Abstract @DBSet   Set<Order>       ordersToShip;
        @RO @Abstract @DBList  List<Order>      listForNoGoodReason;
        @RO @Abstract @DBLog   Appender<String> log;
        }
    }

@Inject Database ecommerce;
if (User user := ecommerce.users.find(123))
    {
    ecommerce.items.add(new Item(456, "AM-G7-XYZ", "Best whoozymawhatzit ever!", 1.99, 0.1, []));
    @Future Result result = ecommerce.images.forEach(image -> image.delete());

    @Future User user = ecommerce.users.find(789);
    &user.whenCompleted
    }

// old
//@Inject Database ecommerce;
User user = ecommerce.tableFor(User).find(123);// "users" doesn't give me type info, but "User" does
ecommerce.tableFor(Item).add(new Item(456, "AM-G7-XYZ", "Best whoozymawhatzit ever!", 1.99, 0.1, []));
// obvious question: how to mutate existing entries?
// how to do it inside a transaction?
// how to search?
// how to do "operations" i.e. processors?



/**
 * User identity. TODO
 */

/**
 * Transactions. TODO
 */

/**
 * Hierarchical replication. TODO
 */

/**
 * Data lifetime.
 *
 * Persistent. Ephemeral.
 */

/**
 * Eventual consistency.
 */

/**
 * If I store it, I need to be able to find it
 *
 * Some things need to be retained, including retaining older versions for the record
 *
 * Retention policies also have to be effective at getting rid of things
 *
 * Some retention policies are not fixed, per se; they are reactive: “Keep on XXXgb of this stuff”
 *
 */



--

@OODB.Schema
module EcommerceDBModule
    {
    package OODB import OODB.xtclang.org;

    @OODB.Schema2
    interface EcommerceDB
            extends Database
        {
        @RO Map<Int, Customer> customers;
        @RO Map<Int, Order> orders;

        @RO Queue<Order> reviewOrders;
        @RO List<Order> problemOrders;

        function Boolean(Order) notShipped = o -> o.shipped == null && o.canceled == null;

        @DBView(orders, notShipped) Map<Int, Order> pendingOrders;

        @Filter(orders, o -> o.shipped == Null && o.canceled == Null) DBView<Int, Order> processing;
        }

    @OODB.Schema3
    const Customer
        {
        @DBList L
        }

    const Order
        {
        DateTime created;
        DateTime? shipped;
        DateTime? canceled;
        Line[] lines;
        const Line(Int item, Int quantity, Dec price);
        }

    @DBTrigger(orders, ...)
        {
        }
    }

module Petstore
    {
    package mydatabase import EcommerceDBModule;
    import mydatabase.EcommerceDB;

    void foo()
        {
        @Inject EcommerceDB db;
        using (db.createTransaction())
            {
            if (Customer cust := db.customers.values.filter(c -> c.email == email).iterator().next())
                {
                db.customers[cust.id] = cust.with(lastUpdate = clock.now);
                }
            }

        using (db.createTransaction())
            {
            db.createBundle()
              .process(acct1, remove(amt))
              .process(acct2, add(amt))
              .execute();
            }

        using (db.createTransaction())
            {
            accounts.process(acct1, debit(amt))
                    .thenDo(accounts.process(acct2, credit(amt)));
            }
        }
    }


    {
    Account acct1 = ...
    Account acct2

    Bundle b = db.createBundle();
    b.withTable("orders").withKeys([o1, o2]);
    b.withTable("customers").withKeys([c1, c2]);

    db.proceess(Bundle b ->
        {
        BackTable t = b.getTable("orders");
        Key[] keys = b.get...
        .add(new Order(...))
        })

    for each: key -> act(ctx, entry(key))
    }

// worst case scenario
for (val entry : accts.entries)
    {
    Account acct = entry.value;
    if (acct.needsInterestToBeApplied())
        {
        entry.value = acct.with(balance = acct.balance * rate);
        }
    }

// still bad case scenario
for (val entry : accts.entries.filter(needsInterestToBeApplied()))
    {
    Account acct = entry.value;
    entry.value = acct.with(balance = acct.balance * rate);
    }

// not quite as bad case scenario
for (val entry : accts.entries.filter(needsInterestToBeApplied()))
    {
    accts.process(entry.key, a -> a.with(balance = acct.balance * rate));
    }

// "almost-best" case scenario
val ids = accts.values.filter(a -> a.needsInterestToBeApplied()).map(a -> a.id);
accts.project(ids, a -> a.balance *= rate);

// hypothetical
accts.do(a -> a.needsInterestToBeApplied(), a -> a.applyInterest());

// maybe..
accts.filterByValue(a -> a.needsInterestToBeApplied()).processAll(a -> a.applyInterest());


Condition
Predicate
Notification

Composition
    Scalar
        Consts - Numbers, String, Range/Interval, ...
    Container<Composition>
        Collection
        List/Array
        Set (of Scalars) e.g. enum-set
        Map<K,V>
        Queue

TableTrigger<Key, Value>
    {
    typedef function Map<Key, Value>.Entry Entry;

    String  name;

    Boolean active;
    }

TransactionalTableTrigger<Key, Value>
        extends TableTrigger<Key, Value>
    {
    function Boolean(Entry, Entry) process;
    }

ExPostFactoTableTrigger<Key, Value>
    {
    function Boolean(Entry, Entry) matches;

    Duration? delay;

    function void(Entry, Entry) process;
    }

DeferredTableAction
    {
    Duration? delay;
    function void(Entry, Entry) process;
    }

EntryAction
    {
    String name;

    Entry initial;
    Entry current;

    Duration delay;
    DateTime scheduled;

    cancelOn
    refreshOn
    rescheduleOn
    }

interface ???
    {
    EntryAction create(String name);
    EntryAction ensure(String name);
    void delete(String name);
    }

map
---
insert
update
delete
*any

collection
----------
add
remove
*any

Map:   (Entry prevEntry, Entry newEntry)
List:  (List prevList, List newList, List added, List removed)
Set:   (Set prevSet, Set newSet, Set added, Set removed)
Queue: (List added, List taken)

Transaction
  UInt id
  User
  Transaction origin
  DateTime
  State (Active, Committing, Committed, RolledBack)
  Conditions
  Contents


module ContactsDB
    {
    package db import OODB.xtclang.org;

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

        Contact eliminateDuplicates()
            {
            // first, go through phone #s and look for duplicates
            // ...

            // now, go through email addresses and look for duplicates
            // ...
            }
        }

    enum EmailCat {Home, Work, Other}

    const Email(EmailCat category, String email);

    enum PhoneCat {Home, Work, Mobile, Fax, Other}

    const Phone(PhoneCat category, String number);

    mixin MyContacts
            into Table<String, Contact>
        {
        void eliminateDuplicates()
            {
            project(keys, e ->
                {
                e.value = e.value.eliminateDuplicates();
// would work the same way as:
//                Contact orig = e.value;
//                Contact mod  = orig.eliminateDuplicates();
//                if (&orig != &mod)
//                    {
//                    e.value = mod;
//                    }
                return Null;
                });
            }
        }

    @db.Schema interface Contacts
        {
        @RO @MyContacts Table<String, Contact> contacts;
        // NO: @RO MyContacts<String, Contact> contacts; // where MyContacts implements Table
        }
    }

module ContactsApp
    {
    package contactsDB import ContactsDB;

    void run()
        {
        @Inject contactsDB.Contacts db;
        @Inject Console console;

        console.println("Contacts:");
        for (Contact contact : db.contacts.values)
            {
            console.println(contact);
            }
        }
    }

// a processor (stored procedure, function, etc.) runs inside the database, in a transactional
// context
// - has to be in the database's type system (otherwise it can't run there)
// - might be a named procedure (registered in a table of procedures), in which case it can be
//   executed by name (possibly with parameters)
// - fully bound functions (procedures requiring no parameters) can be scheduled, set up for post
//   transaction trigger, etc.
//   - but we need to be able to capture the arguments! because they need to be stored (e.g in the
//     tx that commits, if the execution is a post-tx trigger)
//   - the function, and all of the args for the function, need to be in the type system of the db
//

// how to decompose the type system?
// how to store pending function calls?

User
Schema
- Table
- List
- Queue
- Counter

Identity

TypeSystem + Version
Type
Action
Condition
Transaction

static @DBFunc("foo") void foo() {...}

// only some things are DBObject containers
// - Database
// - entries in a Map

// how to tie functions to DBObjects,

Database

names
=============
|Name |Count|
|-----|-----|
| Bob |  3  |
| Sue |  2  |
|-----|-----|

@Inject Database db;
Int count = db.tableFor("names").get("Sue").as(Int);

@Inject Table<String, Int> names;
Int count = names.get("Sue");


//

List<CustId>
List<@ByRef Customer>
List<Customer>

List<CustId>
List<@ByRef Customer>
List<Customer>


// ---

// db is a schema
// tx is a schema
// a table (etc.) reference comes from a schema
// - table from a tx is usable while that tx is still active
// - table from a db always "ensures tx" to issue commands to the database, which is likely more
//   expensive (context token lookup, I assume)

// ---

// simplest database

module ContactsDB
    {
    package db import OODB.xtclang.org;

    Map<String, String> contacts
    }

// ---

mixin Contacts
        into DBMap<Int, Contact>
    {
    @dbo Contact rename(Int key, String name)
        {
        if (Contact oldContact := get(key))
            {
            Contact newContact = oldContact.with(name=name);
            put(key, newContact);
            }
        else
            {
            // ...
            }
        }
    }

interface ContactsDB
        extends Schema
    {
    @RO Contacts contacts;
    }


const ContactsDBSchemaClientImpl // or service
        implements ContactsDB
    {
    Contacts contacts = new ContactsClientImpl()
    }

const ContactsClientImpl // or service
        extends GenericMapClientImpl
        incorporates Contacts
    {
    @Override
    @dbo Contact rename(Int key, String name)
        {
        Tuple result = dbInvoke("rename", (key, name));
        return result[0].as(Contact);
        }
    }
