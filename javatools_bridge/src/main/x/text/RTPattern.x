import ecstasy.text.Matcher;
import ecstasy.text.Pattern;

const RTPattern
        implements Pattern
    {
    @Override
    @RO String pattern;

    @Override
    Matcher match(String input);
    }