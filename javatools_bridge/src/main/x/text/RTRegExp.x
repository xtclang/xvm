import ecstasy.text.Pattern;
import ecstasy.text.RegExp;

service RTRegExp
        implements RegExp
    {
    @Override
    Pattern compile(String regex);
    }