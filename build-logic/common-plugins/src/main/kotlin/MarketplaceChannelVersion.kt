import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The newest version of a JetBrains Marketplace plugin in one of its channels, such as LSP4IJ's `nightly`,
 * that an IDE line accepts. It is read from the Marketplace's plugin repository listing, the one IDEs
 * read for a custom plugin repository. As a value source it is read again on each build, so a newer
 * version invalidates the configuration cache.
 */
abstract class MarketplaceChannelVersion : ValueSource<String, MarketplaceChannelVersion.Parameters> {
    interface Parameters : ValueSourceParameters {
        val pluginId: Property<String>
        val channel: Property<String>

        /** The IDE line the version must accept, such as `IU-262`. */
        val build: Property<String>
    }

    override fun obtain(): String {
        val pluginId = parameters.pluginId.get()
        val channel = parameters.channel.get()
        val build = parameters.build.get()
        val query = listOf("channel" to channel, "pluginId" to pluginId, "build" to build)
            .joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, UTF_8)}" }
        val uri = URI("https://plugins.jetbrains.com/plugins/list?$query")
        val response = try {
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
                .send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            throw GradleException("Cannot read the newest $pluginId of the Marketplace's $channel channel from $uri: $e", e)
        }
        if (response.statusCode() != 200) {
            throw GradleException("Cannot read the Marketplace's $channel channel: $uri answered ${response.statusCode()}")
        }
        return MarketplaceListing.newestVersion(response.body())
            ?: throw GradleException("The Marketplace's $channel channel has no $pluginId for $build ($uri)")
    }
}

/**
 * Reads a Marketplace plugin repository listing: the version of its newest plugin, by the date of its upload, so
 * the listing's order doesn't matter. An empty listing, which the Marketplace answers when no version fits, has none.
 */
internal object MarketplaceListing {
    fun newestVersion(listing: String): String? {
        if (listing.isBlank()) {
            return null
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val plugins = factory.newDocumentBuilder().parse(InputSource(StringReader(listing))).getElementsByTagName("idea-plugin")
        return (0 until plugins.length)
            .map { plugins.item(it) as Element }
            .maxByOrNull { it.getAttribute("date").toLongOrNull() ?: Long.MIN_VALUE }
            ?.getElementsByTagName("version")
            ?.item(0)
            ?.textContent
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }
}
