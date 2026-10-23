import ecstasy.io.IOException;

import json.Mapping;
import json.ObjectInputStream;
import json.Parser;
import json.Printer;
import json.Schema;

import xunit.assertions.assertThrows;

class ParsingTest {

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

    @Test
    void shouldParseUnicodeEscapes() {
        assert parse(quoted(u("0041") + u("00e9") + u("00C9") + u("2028")))
                == "A" + 0xE9.toChar() + 0xC9.toChar() + 0x2028.toChar();
    }

    @Test
    void shouldParseASurrogatePairAsOneCharacter() {
        assert parse(quoted(u("D83D") + u("DE00"))) == "" + 0x1F600.toChar();
    }

    @Test
    void shouldReadBackTheControlCharactersItWrites() {
        String text = "a" + 0x01.toChar() + 0x1F.toChar() + "b";
        String json = Printer.DEFAULT.render(text);
        assert json == quoted("a" + u("0001") + u("001F") + "b");
        assert parse(json) == text;
    }

    @Test
    void shouldRejectABadUnicodeEscape() {
        assertThrows(IOException, () -> parse(quoted(u("00G1"))));
        assertThrows(IOException, () -> parse(quoted(u("DE00"))));
        assertThrows(IOException, () -> parse(quoted(u("D83D") + "x")));
        assertThrows(IOException, () -> parse(quoted(u("D83D") + u("0041"))));
    }

    private String parse(String text) = new Parser(text.toReader()).parseDoc().as(String);

    private String quoted(String text) = "\"" + text + "\"";

    /**
     * @return the JSON escape of the UTF-16 unit that the hexits spell, made this way since the
     *         compiler reads a backslash, a "u" and four hexits as that character, also in a
     *         String literal
     */
    private String u(String hexits) = "\\" + "u" + hexits;
}
