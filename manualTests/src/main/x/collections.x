module TestCollections
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        console.print("Collection tests");

        testLinkedList();
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

        // scenario #3 - all siblings, including this person
        @LinkedList(prev=prevSibling) Person? nextSibling;
        Person? prevSibling;
        List<Person> siblings.get()
            {
            return &nextSibling;
            }

        // scenario #4 - all children of this person
        @LinkedList(next=nextSibling, prev=prevSibling, omitThis=True) Person? child;
        List<Person> children.get()
            {
            return &child;
            }

        // scenario #5 - all phone numbers of this person
        @LinkedList(Phone.next) Phone? phone; // scenario #5
        List<Phone> phoneNumbers.get()
            {
            return &phone;
            }

        @Override
        String toString()
            {
            return name + " " + age;
            }
        }

    void testLinkedList()
        {
        console.print("LinkedList scenario 1");

        Phone       first  = new Phone("home", "555-1212");
        List<Phone> phones = first.list;

        phones +=  new Phone("work", "555-3456");
        phones.add(new Phone("cell", "555-9876"));

        Loop: for (Phone p : phones)
            {
            console.print($"[{Loop.count}] {p}");
            }

        Person george1 = new Person("George I", 48);
        Person george2 = new Person("George II", 25);
        Person albert3 = new Person("Albert III", 5);
        Person brian3  = new Person("Brian III", 7);
        Person chad3   = new Person("Chad III", 11);

        albert3.ancestors.add(george2);
        george2.ancestors.add(george1);

        george2.children.add(albert3);
        george2.children.add(brian3);
        george2.children.add(chad3);

        george1.phoneNumbers.add(new Phone("home", "555-1212"));
        george1.phoneNumbers.add(new Phone("work", "555-3456"));
        george1.phoneNumbers.add(new Phone("cell", "555-9876"));

        console.print("LinkedList scenario 2");

        Loop: for (Person p : albert3.ancestors)
            {
            console.print($"[{Loop.count}] {p}");
            }

        console.print("LinkedList scenario 3");

        Loop: for (Person p : albert3.siblings)
            {
            console.print($"[{Loop.count}] {p}");
            }

        console.print("LinkedList scenario 4");

        Loop: for (Person p : george2.children)
            {
            console.print($"[{Loop.count}] {p}");
            }

        console.print("LinkedList scenario 5");

        Loop: for (Phone p : george1.phoneNumbers)
            {
            console.print($"[{Loop.count}] {p}");
            }
        }
    }