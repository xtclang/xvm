import json.Doc;
import json.JsonObject;
import json.Parser;

import logging.BasicLogger;
import logging.JsonLogSink;
import logging.JsonLogSinkOptions;
import logging.Logger;

/**
 * Tests the production JSON sink's semantic document shape.
 */
class JsonLogSinkTest {

    @Test
    void shouldRenderStructuredEventWithSourceAndRedaction() {
        JsonLogSink sink   = new JsonLogSink(
                new JsonLogSinkOptions(logging.Level.Info, ["card"]));
        ListLogSink capture = new ListLogSink();
        Logger      logger  = new BasicLogger("json.payment", capture);

        logger.atInfo()
              .addKeyValue("requestId", "r_42")
              .addKeyValue("card",      "4111111111111111")
              .log("charged");

        JsonObject obj = parseObject(sink.render(capture.events[0]));

        assert obj["logger"]    == "json.payment";
        assert obj["message"]   == "charged";
        assert obj["requestId"] == "r_42";
        assert obj["card"]      == "***";
    }

    @Test
    void shouldRenderLogAtSourceMetadata() {
        JsonLogSink sink   = new JsonLogSink();
        ListLogSink list   = new ListLogSink();
        Logger      probe  = new BasicLogger("json.source", list);

        probe.logAt(logging.Level.Warn, "slow", "PaymentService.x", 42);

        JsonObject obj = parseObject(sink.render(list.events[0]));
        assert obj["source"].is(JsonObject);
        JsonObject source = obj["source"].as(JsonObject);
        assert source["file"] == "PaymentService.x";
        assert source["line"] == 42;
    }

    private JsonObject parseObject(String rendered) {
        Doc doc = new Parser(rendered.toReader()).parseDoc();
        assert doc.is(JsonObject);
        return doc.as(JsonObject);
    }
}
