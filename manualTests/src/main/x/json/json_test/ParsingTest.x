
class ParsingTest {

    import json.Mapping;
    import json.ObjectInputStream;
    import json.Parser;
    import json.Schema;

    @Inject Console console;

    Schema jsonSchema = Schema.DEFAULT;

    static const Person(String? firstName, String? middleName, String? lastName) {}

    @Test
    void shouldParsePerson() {
        Person person  = new Person("One", "Two", "Three");
        String jsonStr = \|{"firstName":"One","middleName":"Two","lastName":"Three"}
                          ;

        Mapping<Person> valueMapping = jsonSchema.ensureMapping(Person);
        using (ObjectInputStream stream = new ObjectInputStream(jsonSchema, jsonStr.toReader())) {
            val parsed = valueMapping.read(stream.ensureElementInput());
            assert parsed == person;
        }
    }

    @Test
    void shouldParsePersonWithReverseFieldOrder() {
        Person person  = new Person("One", "Two", "Three");
        String jsonStr = \|{"lastName":"Three","middleName":"Two","firstName":"One"}
                          ;

        Mapping<Person> valueMapping = jsonSchema.ensureMapping(Person);
        using (ObjectInputStream stream = new ObjectInputStream(jsonSchema, jsonStr.toReader())) {
            val parsed = valueMapping.read(stream.ensureElementInput());
            assert parsed == person;
        }
    }

    @Test
    void shouldParsePersonUsingNestedParser() {
        Person person  = new Person("One", "Two", "Three");
        String jsonStr = \|{"tx":1, "k":19, "v":{"firstName":"One","middleName":"Two","lastName":"Three"}}
                          ;
        Parser parser  = new Parser(jsonStr.toReader());

        using (val objectParser = parser.expectObject()) {
            objectParser.expectKey("tx");
            objectParser.skipDoc();
            objectParser.expectKey("k");
            objectParser.skipDoc();
            objectParser.expectKey("v");

            using (ObjectInputStream stream = new ObjectInputStream(jsonSchema, objectParser)) {
                Mapping<Person> valueMapping = jsonSchema.ensureMapping(Person);
                val             value        = valueMapping.read(stream.ensureElementInput());
                assert value == person;
            }
        }
    }
}