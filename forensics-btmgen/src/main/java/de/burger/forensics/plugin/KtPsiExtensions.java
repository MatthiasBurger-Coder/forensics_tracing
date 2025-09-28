// DEST: src/main/java/de/burger/forensics/plugin/KtPsiExtensions.java
package de.burger.forensics.plugin;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtElement;

final class KtPsiExtensions {

    private KtPsiExtensions() {
        throw new AssertionError("No instances.");
    }

    /**
     * Utility helper for retrieving 1-based line numbers for Kotlin PSI elements.
     * Returns -1 if the backing document is not available.
     */
    private static int getLineNumber(@NotNull KtElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return -1;
        }
        FileViewProvider viewProvider = file.getViewProvider();
        if (viewProvider == null) {
            return -1;
        }
        Document document = viewProvider.getDocument();
        if (document == null) {
            return -1;
        }
        return document.getLineNumber(element.getTextOffset()) + 1;
    }
}
