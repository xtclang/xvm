package org.xvm.lsp.protocol

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TestEventTest {
    @Test
    fun `a line of xunit's json report is its event, and its fields are the event's`() {
        val line =
            """##xunit {"event":"finished","id":"Mathy:void fails()","status":"failed",""" +
                """"message":"a \"quoted\" line\nand the next","durationMillis":12}""" + "\n"

        assertThat(TestEvent.parse(line)).isEqualTo(
            TestEvent(
                TestEvent.FINISHED,
                id = "Mathy:void fails()",
                status = "failed",
                message = "a \"quoted\" line\nand the next",
                durationMillis = 12,
            ),
        )
        assertThat(TestEvent.parse("""##xunit {"event":"run","version":1}""")).isEqualTo(TestEvent(TestEvent.RUN, version = 1))
        assertThat(TestEvent.parse("""##xunit {"event":"run","version":1}""" + "\r\n")).isEqualTo(TestEvent(TestEvent.RUN, version = 1))
    }

    @Test
    fun `a field or event it does not know is read as any other`() {
        assertThat(TestEvent.parse("""##xunit {"event":"started","id":"M:","since":"later","container":true}"""))
            .isEqualTo(TestEvent(TestEvent.STARTED, id = "M:", container = true))
        assertThat(TestEvent.parse("""##xunit {"event":"published","id":"M:"}""")?.event).isEqualTo("published")
    }

    @Test
    fun `other output is no event, nor is a marked line that is not an event's object`() {
        assertThat(TestEvent.parse("inside works\n")).isNull()
        assertThat(TestEvent.parse("""{"event":"run","version":1}""")).isNull()
        assertThat(TestEvent.parse("##xunit not json")).isNull()
        assertThat(TestEvent.parse("""##xunit ["event"]""")).isNull()
        assertThat(TestEvent.parse("""##xunit {"id":"M:"}""")).isNull()
        assertThat(TestEvent.parse("""##xunit {"event":3}""")).isNull()
        assertThat(TestEvent.parse("""##xunit {"event":"finished","durationMillis":"long"}""")).isNull()
    }

    @Test
    fun `an event's object, as the DAP event xtcTest carries it, is the event, and nothing else is`() {
        assertThat(TestEvent.of(JsonParser.parseString("""{"event":"skipped","id":"M:t","message":"disabled"}""")))
            .isEqualTo(TestEvent(TestEvent.SKIPPED, id = "M:t", message = "disabled"))
        assertThat(TestEvent.of(JsonParser.parseString("""{"id":"M:t"}"""))).isNull()
        assertThat(TestEvent.of(JsonParser.parseString(""""finished""""))).isNull()
    }

    @Test
    fun `an event's line reads as the event, whose module starts its ID`() {
        val event = TestEvent(TestEvent.STARTED, id = "app.examples.org:tests.MathTest", parent = "app.examples.org:", container = true)

        assertThat(event.line()).startsWith(TestEvent.MARKER).endsWith("\n")
        assertThat(TestEvent.parse(event.line())).isEqualTo(event)
        assertThat(event.module()).isEqualTo("app.examples.org")
        assertThat(TestEvent(TestEvent.RUN, version = 1).module()).isEmpty()
    }
}
