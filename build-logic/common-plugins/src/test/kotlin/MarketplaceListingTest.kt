import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.xml.sax.SAXParseException

/**
 * Reads the newest version from the Marketplace's plugin repository listing, as `plugins/list` answers it for a
 * channel, such as LSP4IJ's nightly, and an IDE line: the listings here are shaped as it answered on 2026-10-27.
 */
class MarketplaceListingTest {
    @Test
    fun `takes the version of the newest upload, wherever the listing names it`() {
        val listing =
            """
            <?xml version='1.0' encoding='UTF-8'?><plugin-repository><ff>"Plugin Development"</ff>
            <category name="Plugin Development">
            <idea-plugin downloads="1507436" size="4639843" date="1793112915000" updatedDate="1793112915000" url="">
            <name>LSP4IJ</name><id>com.redhat.devtools.lsp4ij</id><version>0.21.1-20261027-145307</version></idea-plugin>
            <idea-plugin downloads="1507436" size="4639843" date="1793117052000" updatedDate="1793117052000" url="">
            <name>LSP4IJ</name><id>com.redhat.devtools.lsp4ij</id>
            <description><![CDATA[<p>LSP4IJ</p><version>not this one</version>]]></description>
            <version>0.21.1-20261027-160212</version><idea-version min="n/a" max="n/a" since-build="242.0"/></idea-plugin>
            </category></plugin-repository>
            """.trimIndent()
        assertEquals("0.21.1-20261027-160212", MarketplaceListing.newestVersion(listing))
    }

    @Test
    fun `an empty listing names no version`() {
        assertNull(MarketplaceListing.newestVersion(""))
        assertNull(MarketplaceListing.newestVersion("<plugin-repository><category name=\"Plugin Development\"/></plugin-repository>"))
    }

    @Test
    fun `a listing with a document type is refused`() {
        val listing =
            """<?xml version="1.0"?><!DOCTYPE plugin-repository [<!ENTITY v "1.0">]>""" +
                "<plugin-repository><idea-plugin><version>&v;</version></idea-plugin></plugin-repository>"
        assertThrows(SAXParseException::class.java) { MarketplaceListing.newestVersion(listing) }
    }
}
