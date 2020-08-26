module TestCollections
    {
    @Inject ecstasy.io.Console console;

    void run(  )
        {
        console.println("Collection tests");

        testLinkedList1();
        testLinkedList2();
        }

    class Phone(String desc, String number)
        {
        // scenario #1 - all phone numbers in the list, starting with this one
        @LinkedList Phone? next;
        List<Phone> list.get()
            {
            return &next;
            }

        @Override
        String toString()
            {
            return desc + " " + number;
            }
        }

    class Person(String name, Int age)
        {
        // scenario #2 - ancestors, starting with this person's parent (assume asexual reproduction,
        // since this is linked list structure, instead of a tree)
        @LinkedList(omitThis=True) Person? parent;
        List<Person> ancestors.get()
            {
            return &parent;
            }
//
//        // scenario #3 - all siblings, including this person
//        @LinkedList(prev=prevSibling) Person? nextSibling;
//        Person? prevSibling;
//        List<Person> siblings.get()
//            {
//            return &nextSibling;
//            }
//
//        // scenario #4 - all children of this person
//        @LinkedList(next=nextSibling, prev=prevSibling, omitThis=True) Person? child;
//        List<Person> children.get()
//            {
//            return &child;
//            }
//
//        // scenario #5 - all phone numbers of this person
//        @LinkedList(Phone.next) Phone? phone; // scenario #5
//        List<Phone> phoneNumbers.get()
//            {
//            return &phone;
//            }

        @Override
        String toString()
            {
            return name + " " + age;
            }
        }

    void testLinkedList1()
        {
        console.println("Scenario 1");

        Phone       first = new Phone("home", "555-1212");
        List<Phone> list  = first.list;

        list +=  new Phone("work", "555-3456");
        list.add(new Phone("cell", "555-9876"));

        Loop: for (Phone p : list)
            {
            console.println($"[{Loop.count}] {p}");
            }
        }

    void testLinkedList2()
        {
        console.println("Scenario 2");

        Person       george    = new Person("George III", 5);
        List<Person> ancestors = george.ancestors;

        ancestors += new Person("George II", 25);
        ancestors += new Person("George I" , 48);

        Loop: for (Person p : ancestors)
            {
            console.println($"[{Loop.count}] {p}");
            }
        }
    }