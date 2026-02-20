package tn.esprit.synergygig.dao;

import org.junit.jupiter.api.*;
import tn.esprit.synergygig.entities.Offer;
import tn.esprit.synergygig.entities.enums.OfferStatus;
import tn.esprit.synergygig.entities.enums.OfferType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OfferDAOTest {

    static OfferDAO dao;
    static Offer testOffer;

    @BeforeAll
    static void setup() {
        dao = new OfferDAO();
    }

    // ===============================
    // TEST INSERT
    // ===============================
    @Test
    @Order(1)
    void testInsert() throws Exception {

        testOffer = new Offer(
                "DAO Test Offer",
                "Testing insert DAO",
                OfferType.GIG,
                4, // utiliser un user existant
                "test.jpg"
        );

        testOffer.setStatus(OfferStatus.DRAFT);

        dao.insertOne(testOffer);

        List<Offer> offers = dao.selectAll();

        boolean exists = offers.stream()
                .anyMatch(o -> o.getTitle().equals("DAO Test Offer"));

        assertTrue(exists);
    }

    // ===============================
    // TEST UPDATE
    // ===============================
    @Test
    @Order(2)
    void testUpdate() throws Exception {

        List<Offer> offers = dao.selectAll();

        Offer offer = offers.stream()
                .filter(o -> o.getTitle().equals("DAO Test Offer"))
                .findFirst()
                .orElse(null);

        assertNotNull(offer);

        offer.setTitle("DAO Updated Offer");
        dao.updateOne(offer);

        List<Offer> updatedList = dao.selectAll();

        boolean updated = updatedList.stream()
                .anyMatch(o -> o.getTitle().equals("DAO Updated Offer"));

        assertTrue(updated);
    }

    // ===============================
    // TEST DELETE
    // ===============================
    @Test
    @Order(3)
    void testDelete() throws Exception {

        List<Offer> offers = dao.selectAll();

        Offer offer = offers.stream()
                .filter(o -> o.getTitle().equals("DAO Updated Offer"))
                .findFirst()
                .orElse(null);

        assertNotNull(offer);

        dao.deleteOne(offer);

        List<Offer> afterDelete = dao.selectAll();

        boolean exists = afterDelete.stream()
                .anyMatch(o -> o.getTitle().equals("DAO Updated Offer"));

        assertFalse(exists);
    }
}
