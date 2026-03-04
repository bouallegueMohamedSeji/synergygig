package entities;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ContractTest {

    @Test
    void testDefaultConstructor() {
        Contract c = new Contract();
        assertEquals(0, c.getId());
        assertNull(c.getStatus());
        assertEquals(0.0, c.getAmount());
        assertEquals(0, c.getNegotiationRound());
    }

    @Test
    void testParameterizedConstructor() {
        Date start = Date.valueOf(LocalDate.of(2026, 4, 1));
        Date end = Date.valueOf(LocalDate.of(2026, 12, 31));
        Contract c = new Contract(1, 2, 3, "Terms of service", 5000.0, "USD", "DRAFT", start, end);

        assertEquals(1, c.getOfferId());
        assertEquals(2, c.getApplicantId());
        assertEquals(3, c.getOwnerId());
        assertEquals("Terms of service", c.getTerms());
        assertEquals(5000.0, c.getAmount());
        assertEquals("USD", c.getCurrency());
        assertEquals("DRAFT", c.getStatus());
        assertEquals(start, c.getStartDate());
        assertEquals(end, c.getEndDate());
    }

    @Test
    void testFullConstructor() {
        Date start = Date.valueOf(LocalDate.of(2026, 1, 1));
        Date end = Date.valueOf(LocalDate.of(2026, 6, 30));
        Timestamp signed = Timestamp.valueOf(LocalDateTime.of(2026, 1, 5, 14, 0));
        Timestamp created = Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 9, 0));
        Contract c = new Contract(10, 1, 2, 3, "Full terms", 8000.0, "EUR",
                "ACTIVE", 25, "low risk", "0xabc123", "qr://url", signed, start, end, created);

        assertEquals(10, c.getId());
        assertEquals(25, c.getRiskScore());
        assertEquals("low risk", c.getRiskFactors());
        assertEquals("0xabc123", c.getBlockchainHash());
        assertEquals("qr://url", c.getQrCodeUrl());
        assertEquals(signed, c.getSignedAt());
        assertEquals(created, c.getCreatedAt());
    }

    @Test
    void testStatusConstants() {
        assertEquals("DRAFT", Contract.STATUS_DRAFT);
        assertEquals("PENDING_REVIEW", Contract.STATUS_PENDING_REVIEW);
        assertEquals("COUNTER_PROPOSED", Contract.STATUS_COUNTER_PROPOSED);
        assertEquals("PENDING_SIGNATURE", Contract.STATUS_PENDING_SIGNATURE);
        assertEquals("ACTIVE", Contract.STATUS_ACTIVE);
        assertEquals("COMPLETED", Contract.STATUS_COMPLETED);
        assertEquals("TERMINATED", Contract.STATUS_TERMINATED);
        assertEquals("DISPUTED", Contract.STATUS_DISPUTED);
    }

    @Test
    void testNegotiationFields() {
        Contract c = new Contract();
        c.setCounterAmount(6000.0);
        c.setCounterTerms("Counter terms here");
        c.setNegotiationNotes("Discussed during meeting");
        c.setNegotiationRound(3);

        assertEquals(6000.0, c.getCounterAmount());
        assertEquals("Counter terms here", c.getCounterTerms());
        assertEquals("Discussed during meeting", c.getNegotiationNotes());
        assertEquals(3, c.getNegotiationRound());
    }

    @Test
    void testSettersAndGetters() {
        Contract c = new Contract();
        c.setId(42);
        c.setOfferId(5);
        c.setApplicantId(10);
        c.setOwnerId(3);
        c.setStatus("ACTIVE");
        c.setBlockchainHash("0xdef456");

        assertEquals(42, c.getId());
        assertEquals(5, c.getOfferId());
        assertEquals(10, c.getApplicantId());
        assertEquals(3, c.getOwnerId());
        assertEquals("ACTIVE", c.getStatus());
        assertEquals("0xdef456", c.getBlockchainHash());
    }

    @Test
    void testToString() {
        Contract c = new Contract();
        c.setId(1);
        c.setStatus("DRAFT");
        String str = c.toString();
        assertNotNull(str);
    }
}
