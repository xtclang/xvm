import json.Mapping;
import json.ObjectInputStream;
import json.Parser;
import json.Schema;

import json.annotations.JsonProperty;

class JsonPropertyTest {

    Schema jsonSchema = Schema.DEFAULT;

    static const Data(@JsonProperty("$data") String? s, String foo) {}

    @Test
    void shouldParseValueWithCustomPropertyName() {
        Data   data    = new Data("One", "Two");
        String jsonStr = \|{"$data":"One", "foo":"Two"}
                          ;
        Mapping<Data> valueMapping = jsonSchema.ensureMapping(Data);
        using (ObjectInputStream stream = new ObjectInputStream(jsonSchema, jsonStr.toReader())) {
            val parsed = valueMapping.read(stream.ensureElementInput());
            assert parsed == data;
        }
    }

    @Test
    public void shouldWriteValueWithCustomPropertyName() {
        StringBuffer buf  = new StringBuffer();
        Data         data = new Data("One", "Two");
        jsonSchema.createObjectOutput(buf).write(data);
        assert buf.toString() == \|{"$data":"One","foo":"Two"}
                                  ;
    }
}
