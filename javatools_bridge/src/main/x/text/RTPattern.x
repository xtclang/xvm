import ecstasy.text.Matcher;
import ecstasy.text.Pattern;

const RTPattern
        implements Pattern
        delegates Stringable(pattern)
    {
    @Override
    @RO String pattern;

    @Override
    Matcher match(String input);
    }