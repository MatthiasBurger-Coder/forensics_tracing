package de.burger.forensics.plugin.btmgen.common;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BtmTemplateRequestTest {

    @Test
    void defaultsBlankTemplateIdAndKeepsDescriptor() {
        BtmTemplateRequest request = new BtmTemplateRequest(
                " ",
                "com.example.Foo",
                "bar",
                "(I)V"
        );

        assertEquals(BtmGenerationDefaults.DEFAULT_TEMPLATE_ID, request.templateId());
        assertEquals("com.example.Foo", request.className());
        assertEquals("bar", request.methodName());
        assertEquals(Optional.of("(I)V"), request.methodDesc());
    }

    @Test
    void allowsMissingDescriptor() {
        BtmTemplateRequest request = new BtmTemplateRequest(
                "CUSTOM",
                "com.example.Foo",
                "bar",
                (String) null
        );

        assertEquals(Optional.empty(), request.methodDesc());
    }

    @Test
    void rejectsMissingRequiredValues() {
        assertThrows(NullPointerException.class, () ->
                new BtmTemplateRequest(null, "com.example.Foo", "bar", Optional.empty()));
        assertThrows(NullPointerException.class, () ->
                new BtmTemplateRequest("CUSTOM", null, "bar", Optional.empty()));
        assertThrows(NullPointerException.class, () ->
                new BtmTemplateRequest("CUSTOM", "com.example.Foo", null, Optional.empty()));
        assertThrows(NullPointerException.class, () ->
                new BtmTemplateRequest("CUSTOM", "com.example.Foo", "bar", (Optional<String>) null));
    }
}
