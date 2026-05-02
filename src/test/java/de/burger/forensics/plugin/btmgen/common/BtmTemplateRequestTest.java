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
        assertThrows(NullPointerException.class, BtmTemplateRequestTest::templateRequestWithoutTemplateId);
        assertThrows(NullPointerException.class, BtmTemplateRequestTest::templateRequestWithoutClassName);
        assertThrows(NullPointerException.class, BtmTemplateRequestTest::templateRequestWithoutMethodName);
        assertThrows(NullPointerException.class, BtmTemplateRequestTest::templateRequestWithoutMethodDescription);
    }

    private static void templateRequestWithoutTemplateId() {
        new BtmTemplateRequest(null, "com.example.Foo", "bar", Optional.empty());
    }

    private static void templateRequestWithoutClassName() {
        new BtmTemplateRequest("CUSTOM", null, "bar", Optional.empty());
    }

    private static void templateRequestWithoutMethodName() {
        new BtmTemplateRequest("CUSTOM", "com.example.Foo", null, Optional.empty());
    }

    private static void templateRequestWithoutMethodDescription() {
        new BtmTemplateRequest("CUSTOM", "com.example.Foo", "bar", (Optional<String>) null);
    }
}
