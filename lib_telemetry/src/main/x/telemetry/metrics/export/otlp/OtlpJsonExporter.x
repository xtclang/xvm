import json.Doc;
import json.JsonObject;
import json.Parser;
import web.Body;
import web.HttpClient;
import web.HttpMethod;
import web.HttpStatus;
import web.MediaType;
import web.RequestOut;
import web.ResponseIn;

import metrics.model.ResourceMetrics;

/**
 * A [MetricExporter] that serialises metric batches to OTLP JSON and POSTs them to a
 * configurable HTTP endpoint.
 *
 * Usage:
 *
 *     var exporter = new OtlpJsonExporter("http://localhost:4318");
 *     exporter.export(provider.collect());
 *
 * The exporter retries on transient errors (`429`, `502`, `503`, `504`), honouring any
 * `Retry-After` header on `429` responses. `400 Bad Request` is non-retryable and
 * returns [MetricExporter.Result.Failure] immediately.
 */
service OtlpJsonExporter
        implements MetricExporter {

    construct (String?             endpoint   = Null,
               Map<String, String> headers    = [],
               Int?                maxRetries = Null) {

        @Inject(ConfigOtlpEndpoint)   String? cfgEndpoint;
        @Inject(ConfigOtlpMaxRetries) Int?    cfgMaxRetries;

        this.endpoint   = endpoint ?: cfgEndpoint ?: "";
        this.active     = !this.endpoint.empty;
        this.maxRetries = maxRetries ?: cfgMaxRetries ?: DefaultMaxRetries;
        this.headers    = headers;
    }

    public/private String              endpoint;
    public/private Map<String, String> headers;
    public/private Int                 maxRetries;
    public/private Boolean             active;
    private        HttpClient          client = new HttpClient();

    @Inject Clock clock;

    /**
     * The OTLP endpoint URI.
     *
     * This is the concatenation of the configured endpoint and the configured endpoint suffix.
     */
    @Lazy String endpointUri.calc() {
        @Inject(ConfigOtlpEndpointSuffix) String? endpointSuffix;
        String suffix = endpointSuffix ?: DefaultEndpointSuffix;
        String sep    = endpoint.endsWith("/") || suffix.startsWith("/") ? "" : "/";
        return endpoint + sep + suffix;
    }

    /**
     * The injection name for the OTEL push endpoint configuration values.
     */
    static String ConfigOtlpPrefix = metrics.config.MetricsConfigPrefix  + "otlp";

    /**
     * The injection name for the OTEL push endpoint configuration values.
     */
    static String ConfigOtlpEndpoint = ConfigOtlpPrefix  + ".endpoint";

    /**
     * The injection name for the OTEL maximum reties configuration values.
     */
    static String ConfigOtlpMaxRetries = ConfigOtlpPrefix  + ".maxRetries";

    /**
     * The default value for the maximum retries.
     */
    static Int DefaultMaxRetries = 3;

    /**
     * The injection name for the OTEL endpoint suffix.
     */
    static String ConfigOtlpEndpointSuffix = ConfigOtlpPrefix  + ".endpointSuffix";

    /**
     * The default value for the endpoint suffix.
     */
    static String DefaultEndpointSuffix = "/v1/metrics";

    @Override
    Result export(ResourceMetrics[] metrics) {
        if (!active) {
            return DropData;
        }
        Doc body = OtlpEncoding.encodeRequest(metrics);
        return sendWithRetry(endpointUri, body, maxRetries);
    }

    @Override
    void forceFlush() {}

    @Override
    void shutdown() {
        active = False;
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * POSTs `body` to `url` with `Content-Type: application/json`. The [HttpClient]
     * serialises the [Doc] directly; passing a pre-rendered `String` would cause it
     * to be double-encoded as a JSON string literal. Returns the appropriate [Result],
     * retrying on transient errors up to `retriesLeft` times.
     */
    private Result sendWithRetry(String url, Doc body, Int retriesLeft) {
        try {
            RequestOut request = client.createRequest(
                HttpMethod.POST,
                new web.Uri(url),
                body,
                MediaType.Json);

            for ((String name, String value) : headers) {
                request.header.add(name, value);
            }

            ResponseIn response = client.send(request);
            HttpStatus status   = response.status;

            if (status == HttpStatus.OK) {
                warnOnPartialSuccess(response);
                return Success;
            }

            if (status == HttpStatus.BadRequest) {
                return Failure;
            }

            if (isRetryable(status) && retriesLeft > 0) {
                if (status == HttpStatus.TooManyRequests) {
                    Int seconds;
                    if (String headerValue ?= response.header["Retry-After"],
                        seconds             := Int.parse(headerValue),
                        seconds > 0) {
                        return new Result(Status.Failure, Duration.ofSeconds(seconds));
                    }
                }
                return sendWithRetry(url, body, retriesLeft - 1);
            }

            return Failure;
        } catch (Exception e) {
            return retriesLeft > 0
                ? sendWithRetry(url, body, retriesLeft - 1)
                : Failure;
        }
    }

    /**
     * Checks the OTLP response body for a non-zero `partialSuccess.rejectedDataPoints`
     * field and logs a warning to the console when data points were rejected by the
     * backend. Errors reading or parsing the body are silently ignored so that a
     * successful HTTP 200 is always reported as [MetricExporter.Result.Success].
     */
    private void warnOnPartialSuccess(ResponseIn response) {
        try {
            if (Body body ?= response.body) {
                Byte[] bytes = body.bytes;
                if (!bytes.empty) {
                    Doc doc = new Parser(bytes.unpackUtf8().toReader()).parseDoc();
                    if (doc.is(JsonObject)) {
                        Doc partial;
                        if (partial := doc.get("partialSuccess"),
                            partial.is(JsonObject)) {
                            JsonObject ps = partial;
                            Doc rejectedDoc;
                            if (rejectedDoc := ps.get("rejectedDataPoints"),
                                rejectedDoc.is(IntLiteral)) {
                                Int rejected = rejectedDoc.toInt();
                                if (rejected > 0) {
                                    String   suffix = "";
                                    Doc errDoc;
                                    if (errDoc := ps.get("errorMessage"), errDoc.is(String)) {
                                        suffix = $": {errDoc}";
                                    }
                                    @Inject Console console;
                                    console.print($|[telemetry] OTLP partial success: \
                                                   |{rejected} data point(s) rejected{suffix}
                                                 );
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors in response parsing — the export itself succeeded.
        }
    }

//    /**
//     * Reads the `Retry-After` response header (integer seconds form) and blocks the
//     * current service call for that duration before returning, so that [sendWithRetry]
//     * naturally waits before issuing the next attempt. Non-integer header values and
//     * parsing errors are silently ignored and the caller retries immediately.
//     */
//    private void delayIfRetryAfter(ResponseIn response) {
//        try {
//            Int seconds;
//            if (String headerValue ?= response.header["Retry-After"],
//                seconds             := Int.parse(headerValue),
//                seconds > 0) {
//                @Future Boolean resumed;
//                clock.schedule(Duration.ofSeconds(seconds), () -> { resumed = True; });
//                Boolean _ = resumed;
//            }
//        } catch (Exception e) {
//            // Ignore errors in header parsing — proceed with immediate retry.
//        }
//    }

    /**
     * Returns `True` for HTTP status codes that indicate a transient error where retrying
     * the same request is appropriate per the OTLP specification.
     */
    private static Boolean isRetryable(HttpStatus status) =
        status == HttpStatus.TooManyRequests
            || status == HttpStatus.BadGateway
            || status == HttpStatus.ServiceUnavailable
            || status == HttpStatus.GatewayTimeout;
}
