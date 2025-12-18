/**
 * Sample XTC module for testing the LSP server.
 */
module sample {
    /**
     * A simple Person class.
     */
    class Person {
        String name;
        Int age;

        construct(String name, Int age) {
            this.name = name;
            this.age = age;
        }

        String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }

        Boolean isAdult() {
            return age >= 18;
        }
    }

    /**
     * A service for managing users.
     */
    service UserService {
        private List<Person> users = [];

        void addUser(Person person) {
            users.add(person);
        }

        conditional Person findByName(String name) {
            for (Person p : users) {
                if (p.getName() == name) {
                    return True, p;
                }
            }
            return False;
        }
    }

    /**
     * The main entry point.
     */
    void run() {
        UserService svc = new UserService();
        svc.addUser(new Person("Alice", 30));
        svc.addUser(new Person("Bob", 25));

        if (Person alice := svc.findByName("Alice")) {
            @Inject Console console;
            console.print($"Found: {alice.getName()}");
        }
    }
}
