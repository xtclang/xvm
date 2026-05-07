import json.Doc;
import json.JsonObject;
import json.Parser;

import slogging.AnyValue;
import slogging.HandlerOptions;
import slogging.JSONHandler;
import slogging.Level;
import slogging.Record;

/**
 * Tests the shipped JSON handler's document shape. This is intentionally stricter than
 * string contains checks: the rendered output must parse through `lib_json`.
 */
class JSONHandlerTest {

    @Test
    void shouldRenderParseableJsonWithEscapingAndNestedGroups() {
        JSONHandler handler = new JSONHandler();
        Exception   boom    = new Exception("bad \"card\"");
        Map<String, AnyValue> userAttrs = Map:["id"="u_3"];
        Record      record  = new Record(
                time       = new Time("2019-05-22T120123.456Z"),
                message    = "charged \"ok\"",
                level      = Level.Info,
                attrs      = Map:[
                        "amount" = 1099,
                        "user"   = userAttrs,
                ],
                exception  = boom,
                sourceFile = "PaymentService.x",
                sourceLine = 42,
        );

        JsonObject obj = handler.toJson(record);

        assert obj["level"]  == "INFO";
        assert obj["msg"]    == "charged \"ok\"";
        assert obj["amount"] == 1099;

        assert obj["user"].is(JsonObject);
        JsonObject user = obj["user"].as(JsonObject);
        assert user["id"] == "u_3";

        assert obj["exception"].is(JsonObject);
        JsonObject exception = obj["exception"].as(JsonObject);
        assert exception["message"] == "bad \"card\"";

        assert obj["source"].is(JsonObject);
        JsonObject source = obj["source"].as(JsonObject);
        assert source["file"] == "PaymentService.x";
        assert source["line"] == 42;
    }

    @Test
    void shouldRenderGroupedDerivedLoggerAsNestedJson() {
        JSONHandler handler = new JSONHandler();
        Map<String, AnyValue> paymentAttrs = Map:["amount"=1099];
        Record      record  = new Record(
                time    = new Time("2019-05-22T120123.456Z"),
                message = "charged",
                level   = Level.Info,
                attrs   = Map:["payments"=paymentAttrs],
        );

        JsonObject obj = handler.toJson(record);

        assert obj["payments"].is(JsonObject);
        JsonObject payments = obj["payments"].as(JsonObject);
        assert payments["amount"] == 1099;
    }

    @Test
    void shouldApplyHandlerOptionsRedaction() {
        JSONHandler handler = new JSONHandler(
                new HandlerOptions(Level.Info, ["token"]));
        Record record = new Record(
                time    = new Time("2019-05-22T120123.456Z"),
                message = "auth",
                level   = Level.Info,
                attrs   = Map:["token"="secret", "user"="u_1"],
        );

        JsonObject obj = handler.toJson(record);

        assert obj["token"] == "***";
        assert obj["user"]  == "u_1";
    }

    private JsonObject parseObject(String rendered) {
        Doc doc = new Parser(rendered.toReader()).parseDoc();
        assert doc.is(JsonObject);
        return doc.as(JsonObject);
    }
}
