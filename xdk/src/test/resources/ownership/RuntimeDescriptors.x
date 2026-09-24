/**
 * Field initializers generated after activation must work for distinct generic compositions.
 */
module RuntimeDescriptors {
    void run() {
        Box<Int> first = new Box<Int>(7);
        Box<Int> second = new Box<Int>(9);
        Box<String> text = new Box<String>("value");
        assert first.content == 7;
        assert second.content == 9;
        assert text.content == "value";
        assert first.count == 41;
        assert second.count == 41;
        assert text.count == 41;

        Type base = Box;
        Type textType = base.parameterize([String]);
        assert textType == Box<String>;
        assert textType == base.parameterize([String]);
        assert textType != base.parameterize([Int]);
        Type nullableText = textType | Nullable;
        assert Type underlying := nullableText.isNullable();
        assert underlying == textType;
        Type unchanged = String.parameterize();
        assert unchanged == String;
        assert first.label == "initialized";
        assert first.zero == 0;
        assert text.zero == 0;
        assert first.empty == Null;
        assert text.empty == Null;
        first.count++;
        assert first.count == 42;
        assert second.count == 41;
        assert text.count == 41;
    }

    class Box<Element>(Element content) {
        Int count = 41;
        String label = "initialized";
        Int zero;
        String? empty;
    }
}
