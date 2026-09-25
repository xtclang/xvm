import ecstasy.reflect.Annotation;
import ecstasy.reflect.AnnotationTemplate;
import ecstasy.reflect.Argument;

module RuntimeCapturedAnnotations {
    void run() {
        Type markerType = Marker;
        assert Class marker := markerType.fromClass();
        Type target = String;
        Type flagType = Flag;
        assert Class flag := flagType.fromClass();
        Type marked = target.annotate(new Annotation(flag));
        assert val flagAnnotation := marked.annotated();
        assert flagAnnotation.arguments.empty;
        String captured = "value=" + supply();
        val annotated = target.template.annotate(
                new AnnotationTemplate(marker.baseTemplate, [new Argument(captured)]));
        assert val anno := annotated.annotated();
        assert anno.arguments.size == 1;
        assert anno.arguments[0].value.as(String) == "value=17";
    }

    Int supply() = 17;

    annotation Marker(String text) into Object {}
    annotation Flag into Object {}
}
