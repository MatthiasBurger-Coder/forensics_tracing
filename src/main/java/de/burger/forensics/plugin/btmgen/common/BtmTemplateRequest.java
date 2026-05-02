package de.burger.forensics.plugin.btmgen.common;

import java.util.Objects;
import java.util.Optional;

/**
 * Build-tool-neutral request for rendering a single explicit rule template.
 */
public record BtmTemplateRequest(
        String templateId,
        String className,
        String methodName,
        Optional<String> methodDesc
) {

    public BtmTemplateRequest {
        templateId = defaultTemplateId(templateId);
        className = Objects.requireNonNull(className, "className");
        methodName = Objects.requireNonNull(methodName, "methodName");
        methodDesc = Objects.requireNonNull(methodDesc, "methodDesc");
    }

    public BtmTemplateRequest(String templateId, String className, String methodName, String methodDesc) {
        this(templateId, className, methodName, Optional.ofNullable(methodDesc));
    }

    private static String defaultTemplateId(String templateId) {
        String candidate = Objects.requireNonNull(templateId, "templateId");
        return candidate.isBlank() ? BtmGenerationDefaults.DEFAULT_TEMPLATE_ID : candidate;
    }
}
