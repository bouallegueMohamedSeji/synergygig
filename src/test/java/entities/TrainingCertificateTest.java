package entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class TrainingCertificateTest {

    @Test
    void defaultConstructor() {
        TrainingCertificate tc = new TrainingCertificate();
        assertEquals(0, tc.getId());
        assertNull(tc.getCertificateNumber());
        assertEquals(0, tc.getSignedByUserId());
        assertNull(tc.getSignatureData());
    }

    @Test
    void fourArgConstructor() {
        TrainingCertificate tc = new TrainingCertificate(1, 5, 10, "CERT-UUID-123");
        assertEquals(1, tc.getEnrollmentId());
        assertEquals(5, tc.getUserId());
        assertEquals(10, tc.getCourseId());
        assertEquals("CERT-UUID-123", tc.getCertificateNumber());
    }

    @Test
    void sixArgConstructor() {
        Timestamp ts = Timestamp.valueOf("2025-06-30 15:00:00");
        TrainingCertificate tc = new TrainingCertificate(1, 2, 5, 10, "CERT-456", ts);

        assertEquals(1, tc.getId());
        assertEquals(2, tc.getEnrollmentId());
        assertEquals(5, tc.getUserId());
        assertEquals(10, tc.getCourseId());
        assertEquals("CERT-456", tc.getCertificateNumber());
        assertEquals(ts, tc.getIssuedAt());
    }

    @Test
    void isSignedFalseByDefault() {
        TrainingCertificate tc = new TrainingCertificate(1, 5, 10, "CERT-001");
        assertFalse(tc.isSigned(), "Unsigned certificate should return false");
    }

    @Test
    void isSignedTrueWhenSignedProperly() {
        TrainingCertificate tc = new TrainingCertificate(1, 5, 10, "CERT-001");
        tc.setSignedByUserId(3);
        tc.setSignatureData("base64PngData");
        tc.setSignedAt(new Timestamp(System.currentTimeMillis()));
        assertTrue(tc.isSigned(), "Certificate with signedByUserId > 0 and signatureData should be signed");
    }

    @Test
    void isSignedFalseWithEmptySignatureData() {
        TrainingCertificate tc = new TrainingCertificate(1, 5, 10, "CERT-001");
        tc.setSignedByUserId(3);
        tc.setSignatureData("");
        assertFalse(tc.isSigned(), "Empty signature data should mean unsigned");
    }

    @Test
    void isSignedFalseWithZeroUserId() {
        TrainingCertificate tc = new TrainingCertificate(1, 5, 10, "CERT-001");
        tc.setSignedByUserId(0);
        tc.setSignatureData("base64PngData");
        assertFalse(tc.isSigned(), "Zero signedByUserId should mean unsigned");
    }

    @Test
    void settersAndGetters() {
        TrainingCertificate tc = new TrainingCertificate();
        tc.setId(10);
        tc.setEnrollmentId(2);
        tc.setUserId(5);
        tc.setCourseId(8);
        tc.setCertificateNumber("CERT-789");
        Timestamp issued = new Timestamp(System.currentTimeMillis());
        tc.setIssuedAt(issued);
        tc.setSignedByUserId(3);
        tc.setSignatureData("sigData");
        Timestamp signed = new Timestamp(System.currentTimeMillis());
        tc.setSignedAt(signed);

        assertEquals(10, tc.getId());
        assertEquals(2, tc.getEnrollmentId());
        assertEquals(5, tc.getUserId());
        assertEquals(8, tc.getCourseId());
        assertEquals("CERT-789", tc.getCertificateNumber());
        assertEquals(issued, tc.getIssuedAt());
        assertEquals(3, tc.getSignedByUserId());
        assertEquals("sigData", tc.getSignatureData());
        assertEquals(signed, tc.getSignedAt());
    }

    @Test
    void toStringContainsFields() {
        TrainingCertificate tc = new TrainingCertificate(1, 5, 10, "CERT-ABC");
        String s = tc.toString();
        assertTrue(s.contains("userId=5"));
        assertTrue(s.contains("courseId=10"));
        assertTrue(s.contains("CERT-ABC"));
        assertTrue(s.contains("signed=false"));
    }
}
