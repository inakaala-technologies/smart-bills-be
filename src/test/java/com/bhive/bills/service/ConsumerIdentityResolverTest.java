package com.bhive.bills.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConsumerIdentityResolverTest {

    private final ConsumerIdentityResolver resolver = new ConsumerIdentityResolver();

    @Test
    void trims_identity_values_before_hashing() {
        ConsumerIdentity padded = resolver.resolve(
                "kseb",
                Map.of("ConsumerNo", "  12345 ")
        );
        ConsumerIdentity normalized = resolver.resolve(
                "KSEB",
                Map.of("ConsumerNo", "12345")
        );

        assertEquals("12345", padded.primaryConsumerNumber());
        assertEquals(normalized.hash(), padded.hash());
    }

    @Test
    void composite_identity_uses_account_and_discom_in_declared_order() {
        ConsumerIdentity identity = resolver.resolve(
                "UPPCL",
                Map.of("AccountNo", " ACC-1 ", "Discom", " DISCOM-X ")
        );
        ConsumerIdentity changedAccount = resolver.resolve(
                "UPPCL",
                Map.of("AccountNo", "ACC-2", "Discom", "DISCOM-X")
        );
        ConsumerIdentity changedDiscom = resolver.resolve(
                "UPPCL",
                Map.of("AccountNo", "ACC-1", "Discom", "DISCOM-Y")
        );

        assertEquals("ACC-1", identity.primaryConsumerNumber());
        assertEquals(java.util.List.of("AccountNo", "Discom"), identity.fieldNames());
        assertNotEquals(identity.hash(), changedAccount.hash());
        assertNotEquals(identity.hash(), changedDiscom.hash());
    }

    @Test
    void ignores_optional_goa_online_udf2() {
        ConsumerIdentity withoutOptional = resolver.resolve(
                "GOAONLINE",
                Map.of("UDF1", " CONNECTION-1 ", "UID", " USER-1 ")
        );
        ConsumerIdentity withOptional = resolver.resolve(
                "GOAONLINE",
                Map.of("UDF1", "CONNECTION-1", "UID", "USER-1", "UDF2", "meter-variant")
        );

        assertEquals(withoutOptional.hash(), withOptional.hash());
    }

    @Test
    void rejects_unknown_provider_mapping_and_missing_identity_fields() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve("UNKNOWN", Map.of())
        );
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve("UPPCL", Map.of("AccountNo", "ACC-1"))
        );
    }

    @Test
    void resolves_new_service_identity_from_its_complete_documented_input_set() {
        ConsumerIdentity identity = resolver.resolve(
                "INS_PNB_METLIFE",
                Map.of(
                        "PMLIPolicyNo", "POL-1",
                        "PMLIApplicationNo", "APP-1",
                        "PMLINameAsPerPolicy", "NAME"
                )
        );

        assertEquals("POL-1", identity.primaryConsumerNumber());
        assertEquals(
                java.util.List.of(
                        "PMLIPolicyNo", "PMLIApplicationNo", "PMLINameAsPerPolicy"
                ),
                identity.fieldNames()
        );
    }

    @Test
    void defines_identity_fields_for_every_supported_provider() {
        Map<String, Map<String, String>> inputs = new java.util.LinkedHashMap<>();
        inputs.put("KSEB", Map.of("ConsumerNo", "k"));
        inputs.put("GUVNL", Map.of("consno", "g"));
        inputs.put("PSPCL", Map.of("PSPCLACCOUNT", "p"));
        inputs.put("JKPDD", Map.of("consumerid", "j"));
        inputs.put("NBPDCL", Map.of("ConsumerId", "n"));
        inputs.put("BSESDELHI", Map.of("ca_number", "b"));
        inputs.put("MPWZ", Map.of("consumer_no", "m"));
        inputs.put("UPPCL", Map.of("AccountNo", "u", "Discom", "d"));
        inputs.put("GOAONLINE", Map.of("UDF1", "g1", "UID", "g3"));
        inputs.put("KIADB", Map.of("RegNum", "i"));
        inputs.put("ULB_UP", Map.of("CONNECTIONNO", "w"));

        assertEquals(java.util.List.of("ConsumerNo"), resolver.resolve("KSEB", inputs.get("KSEB")).fieldNames());
        assertEquals(java.util.List.of("consno"), resolver.resolve("GUVNL", inputs.get("GUVNL")).fieldNames());
        assertEquals(java.util.List.of("PSPCLACCOUNT"), resolver.resolve("PSPCL", inputs.get("PSPCL")).fieldNames());
        assertEquals(java.util.List.of("consumerid"), resolver.resolve("JKPDD", inputs.get("JKPDD")).fieldNames());
        assertEquals(java.util.List.of("ConsumerId"), resolver.resolve("NBPDCL", inputs.get("NBPDCL")).fieldNames());
        assertEquals(java.util.List.of("ca_number"), resolver.resolve("BSESDELHI", inputs.get("BSESDELHI")).fieldNames());
        assertEquals(java.util.List.of("consumer_no"), resolver.resolve("MPWZ", inputs.get("MPWZ")).fieldNames());
        assertEquals(java.util.List.of("AccountNo", "Discom"), resolver.resolve("UPPCL", inputs.get("UPPCL")).fieldNames());
        assertEquals(java.util.List.of("UDF1", "UID"), resolver.resolve("GOAONLINE", inputs.get("GOAONLINE")).fieldNames());
        assertEquals(java.util.List.of("RegNum"), resolver.resolve("KIADB", inputs.get("KIADB")).fieldNames());
        assertEquals(java.util.List.of("CONNECTIONNO"), resolver.resolve("ULB_UP", inputs.get("ULB_UP")).fieldNames());
    }
}
