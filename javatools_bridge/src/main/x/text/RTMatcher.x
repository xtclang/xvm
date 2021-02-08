import ecstasy.text.Matcher;
import ecstasy.text.Pattern;

const RTMatcher
        implements Matcher
    {
    @Override Boolean matched.get() { TODO("native"); }

    @Override Int groupCount.get() { TODO("native"); }

    @Override Pattern pattern.get() { TODO("native"); }

    @Override
    @Op("[]")
    String? group(Int index);

    @Override
    Boolean find();

    @Override
    String replaceAll(String replacement);

    @Override
    Matcher reset();
    }