package entities;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class OfferTest {

    @Test
    void testDefaultConstructor() {
        Offer offer = new Offer();
        assertEquals(0, offer.getId());
        assertNull(offer.getTitle());
        assertNull(offer.getStatus());
        assertEquals(0.0, offer.getAmount());
    }

    @Test
    void testParameterizedConstructor() {
        Date start = Date.valueOf(LocalDate.of(2026, 4, 1));
        Date end = Date.valueOf(LocalDate.of(2026, 12, 31));
        Offer offer = new Offer("Java Dev", "Remote Java position", "FULL_TIME", "PUBLISHED",
                "Java,Spring", "Remote", 5000.0, "USD", 1, 2, start, end);

        assertEquals("Java Dev", offer.getTitle());
        assertEquals("Remote Java position", offer.getDescription());
        assertEquals("FULL_TIME", offer.getOfferType());
        assertEquals("PUBLISHED", offer.getStatus());
        assertEquals("Java,Spring", offer.getRequiredSkills());
        assertEquals("Remote", offer.getLocation());
        assertEquals(5000.0, offer.getAmount());
        assertEquals("USD", offer.getCurrency());
        assertEquals(1, offer.getOwnerId());
        assertEquals(2, offer.getDepartmentId());
        assertEquals(start, offer.getStartDate());
        assertEquals(end, offer.getEndDate());
    }

    @Test
    void testFullConstructorWithId() {
        Date start = Date.valueOf(LocalDate.of(2026, 3, 1));
        Date end = Date.valueOf(LocalDate.of(2026, 6, 30));
        Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2026, 2, 1, 8, 0));
        Offer offer = new Offer(10, "Designer", "UI/UX", "FREELANCE", "OPEN",
                "Figma", "Paris", 3000.0, "EUR", 5, null, start, end, ts);

        assertEquals(10, offer.getId());
        assertEquals("Designer", offer.getTitle());
        assertEquals("FREELANCE", offer.getOfferType());
        assertEquals("OPEN", offer.getStatus());
        assertNull(offer.getDepartmentId());
        assertEquals(ts, offer.getCreatedAt());
    }

    @Test
    void testOfferTypeConstants() {
        assertEquals("FULL_TIME", Offer.TYPE_FULL_TIME);
        assertEquals("PART_TIME", Offer.TYPE_PART_TIME);
        assertEquals("FREELANCE", Offer.TYPE_FREELANCE);
        assertEquals("INTERNSHIP", Offer.TYPE_INTERNSHIP);
        assertEquals("CONTRACT", Offer.TYPE_CONTRACT);
    }

    @Test
    void testStatusConstants() {
        assertEquals("DRAFT", Offer.STATUS_DRAFT);
        assertEquals("PUBLISHED", Offer.STATUS_PUBLISHED);
        assertEquals("COMPLETED", Offer.STATUS_COMPLETED);
        assertEquals("CANCELLED", Offer.STATUS_CANCELLED);
        assertEquals("OPEN", Offer.STATUS_OPEN);
        assertEquals("CLOSED", Offer.STATUS_CLOSED);
    }

    @Test
    void testSettersAndGetters() {
        Offer offer = new Offer();
        offer.setId(7);
        offer.setTitle("Backend Engineer");
        offer.setAmount(4500.0);
        offer.setCurrency("TND");
        offer.setOwnerId(3);

        assertEquals(7, offer.getId());
        assertEquals("Backend Engineer", offer.getTitle());
        assertEquals(4500.0, offer.getAmount());
        assertEquals("TND", offer.getCurrency());
        assertEquals(3, offer.getOwnerId());
    }

    @Test
    void testToString() {
        Offer offer = new Offer();
        offer.setId(1);
        offer.setTitle("Test Offer");
        String str = offer.toString();
        assertNotNull(str);
        assertTrue(str.contains("Test Offer"));
    }
}
