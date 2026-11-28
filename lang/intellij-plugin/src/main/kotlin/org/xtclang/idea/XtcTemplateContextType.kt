package org.xtclang.idea

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType

/**
 * Where the Ecstasy live templates of liveTemplates/Ecstasy.xml apply: in Ecstasy source files, as a
 * language's own context scopes its templates. The platform's "Everywhere" context is every file,
 * so templates enabled there are offered in a Java or Markdown file too.
 */
class XtcTemplateContextType : TemplateContextType("Ecstasy") {
    override fun isInContext(context: TemplateActionContext): Boolean = context.file.language == XtcIntelliJLanguage
}
