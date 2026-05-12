/**
 * Top-level container for an export batch, grouping scope metrics by resource.
 *
 * Each batch typically contains one `ResourceMetrics` per distinct resource (e.g. per
 * service instance). This is the type passed to [MetricExporter.export].
 */
const ResourceMetrics(Resource       resource,
                      ScopeMetrics[] scopeMetrics = [],
                      String?        schemaUrl    = Null) {}
