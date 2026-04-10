package org.xtclang.idea.style

import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import org.xtclang.idea.XtcIntelliJLanguage

/**
 * Provides Code Style settings for XTC under Settings > Editor > Code Style > Ecstasy.
 *
 * Configures the standard "Tabs and Indents" panel with XTC's default values:
 * - 4-space indent (matching `lib_ecstasy/` conventions)
 * - 8-space continuation indent (for `extends`/`implements` lines)
 * - No tab characters
 * - 120-character right margin
 *
 * These defaults match the XTC standard library source style. When a project-level
 * `xtc-format.toml` config file is present, it takes precedence over these IDE settings.
 */
class XtcLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage(): Language = XtcIntelliJLanguage

    override fun customizeDefaults(
        commonSettings: CommonCodeStyleSettings,
        indentOptions: CommonCodeStyleSettings.IndentOptions,
    ) {
        indentOptions.INDENT_SIZE = 4
        indentOptions.CONTINUATION_INDENT_SIZE = 8
        indentOptions.TAB_SIZE = 4
        indentOptions.USE_TAB_CHARACTER = false
        commonSettings.RIGHT_MARGIN = 120
    }

    override fun customizeSettings(
        consumer: CodeStyleSettingsCustomizable,
        settingsType: SettingsType,
    ) {
        if (settingsType == SettingsType.INDENT_SETTINGS) {
            consumer.showStandardOptions(
                "INDENT_SIZE",
                "CONTINUATION_INDENT_SIZE",
                "TAB_SIZE",
                "USE_TAB_CHARACTER",
                "SMART_TABS",
                "KEEP_INDENTS_ON_EMPTY_LINES",
            )
        }
    }

    override fun getCodeSample(settingsType: SettingsType): String = CODE_SAMPLE

    companion object {
        private val CODE_SAMPLE =
            """
            module myapp {
                /**
                 * A person with a name and age.
                 */
                const Person(String name, Int age)
                        implements Stringable {
                    @Override
                    Int estimateStringLength() {
                        return name.size + 10;
                    }

                    @Override
                    Appender<Char> appendTo(Appender<Char> buf) {
                        return buf.addAll(${'$'}"{name} (age={age})");
                    }
                }

                void run() {
                    Person[] people = [
                        new Person("Alice", 30),
                        new Person("Bob", 25),
                    ];

                    for (Person person : people) {
                        @Inject Console console;
                        console.print(person);
                    }

                    switch (people.size) {
                    case 0:
                        console.print("No people");
                        break;
                    case 1:
                        console.print("One person");
                        break;
                    default:
                        console.print(${'$'}"Found {people.size} people");
                        break;
                    }
                }
            }
            """.trimIndent()
    }
}
