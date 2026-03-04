package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class JobApplicationTest {

    @Test
    void defaultConstructor() {
        JobApplication ja = new JobApplication();
        assertEquals(0, ja.getId());
        assertEquals(0, ja.getOfferId());
        assertNull(ja.getStatus());
        assertNull(ja.getCoverLetter());
        assertNull(ja.getAiScore());
        assertNull(ja.getAiFeedback());
    }

    @Test
    void statusConstants() {
        assertEquals("PENDING", JobApplication.STATUS_PENDING);
        assertEquals("REVIEWED", JobApplication.STATUS_REVIEWED);
        assertEquals("SHORTLISTED", JobApplication.STATUS_SHORTLISTED);
        assertEquals("ACCEPTED", JobApplication.STATUS_ACCEPTED);
        assertEquals("REJECTED", JobApplication.STATUS_REJECTED);
        assertEquals("WITHDRAWN", JobApplication.STATUS_WITHDRAWN);
    }

    @Test
    void fourArgConstructor() {
        JobApplication ja = new JobApplication(10, 5, "I'm interested", "PENDING");

        assertEquals(10, ja.getOfferId());
        assertEquals(5, ja.getApplicantId());
        assertEquals("I'm interested", ja.getCoverLetter());
        assertEquals("PENDING", ja.getStatus());
    }

    @Test
    void fullConstructor() {
        Timestamp applied = Timestamp.valueOf("2025-06-01 10:00:00");
        Timestamp reviewed = Timestamp.valueOf("2025-06-05 15:00:00");
        JobApplication ja = new JobApplication(1, 10, 5, "Cover letter text",
                "SHORTLISTED", 85, "Good match", applied, reviewed);

        assertEquals(1, ja.getId());
        assertEquals(10, ja.getOfferId());
        assertEquals(5, ja.getApplicantId());
        assertEquals("Cover letter text", ja.getCoverLetter());
        assertEquals("SHORTLISTED", ja.getStatus());
        assertEquals(85, ja.getAiScore());
        assertEquals("Good match", ja.getAiFeedback());
        assertEquals(applied, ja.getAppliedAt());
        assertEquals(reviewed, ja.getReviewedAt());
    }

    @Test
    void nullableAiScore() {
        JobApplication ja = new JobApplication(10, 5, "Letter", "PENDING");
        assertNull(ja.getAiScore());
        ja.setAiScore(92);
        assertEquals(92, ja.getAiScore());
        ja.setAiScore(null);
        assertNull(ja.getAiScore());
    }

    @Test
    void settersAndGetters() {
        JobApplication ja = new JobApplication();
        ja.setId(5);
        ja.setOfferId(20);
        ja.setApplicantId(8);
        ja.setCoverLetter("My cover letter");
        ja.setStatus("ACCEPTED");
        ja.setAiScore(90);
        ja.setAiFeedback("Excellent candidate");
        Timestamp applied = new Timestamp(System.currentTimeMillis());
        ja.setAppliedAt(applied);
        Timestamp reviewed = new Timestamp(System.currentTimeMillis() + 86400000);
        ja.setReviewedAt(reviewed);

        assertEquals(5, ja.getId());
        assertEquals(20, ja.getOfferId());
        assertEquals(8, ja.getApplicantId());
        assertEquals("My cover letter", ja.getCoverLetter());
        assertEquals("ACCEPTED", ja.getStatus());
        assertEquals(90, ja.getAiScore());
        assertEquals("Excellent candidate", ja.getAiFeedback());
        assertEquals(applied, ja.getAppliedAt());
        assertEquals(reviewed, ja.getReviewedAt());
    }

    @Test
    void toStringContainsFields() {
        JobApplication ja = new JobApplication(10, 5, "Letter", "PENDING");
        ja.setId(1);
        String s = ja.toString();
        assertTrue(s.contains("id=1"));
        assertTrue(s.contains("offerId=10"));
        assertTrue(s.contains("applicantId=5"));
        assertTrue(s.contains("PENDING"));
    }
}
