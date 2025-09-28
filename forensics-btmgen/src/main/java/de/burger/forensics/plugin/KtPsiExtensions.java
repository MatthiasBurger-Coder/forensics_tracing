package de.burger.forensics.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.psi.KtElement;

final class KtPsiExtensions {

    private KtPsiExtensions() {
        throw new AssertionError("No instances.");
    }

    /**
     * Utility helper for retrieving 1-based line numbers for Kotlin PSI elements.
     * Returns -1 if the containing file or its text is not available.
     */
    private static int getLineNumber(@NotNull KtElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return -1;
        }
        CharSequence text = file.getText();
        if (text == null) {
            return -1;
        }
        int offset = element.getTextOffset();
        if (offset < 0) {
            return -1;
        }
        int line = 1;
        int limit = Math.min(offset, text.length());
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
