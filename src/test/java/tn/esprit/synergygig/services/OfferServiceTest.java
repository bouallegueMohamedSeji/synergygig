package tn.esprit.synergygig.services;

import org.junit.jupiter.api.*;
import tn.esprit.synergygig.entities.Offer;
import tn.esprit.synergygig.entities.enums.OfferStatus;
import tn.esprit.synergygig.entities.enums.OfferType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OfferServiceTest {

    static OfferService service;
    static int testOfferId;

    @BeforeAll
    static void setup() {
        service = new OfferService();
    }

    @Test
    @Order(1)
    void testAjouterOffer() throws Exception {

        Offer offer = new Offer(
                "Test Offer",
                "Test Description",
                OfferType.GIG,
                1,
                "test.jpg"
        );

        service.addOffer(offer);

        List<Offer> offers = service.getAllOffers();

        assertFalse(offers.isEmpty());

        boolean found = offers.stream()
                .anyMatch(o -> o.getTitle().equals("Test Offer"));

        assertTrue(found);
    }

    @Test
    @Order(2)
    void testPublishOffer() throws Exception {

        List<Offer> offers = service.getAllOffers();

        Offer offer = offers.stream()
                .filter(o -> o.getTitle().equals("Test Offer"))
                .findFirst()
                .orElse(null);

        assertNotNull(offer);

        service.publishOffer(offer);

        List<Offer> updated = service.getAllOffers();

        Offer updatedOffer = updated.stream()
                .filter(o -> o.getId() == offer.getId())
                .findFirst()
                .orElse(null);

        assertEquals(OfferStatus.PUBLISHED, updatedOffer.getStatus());
    }

    @Test
    @Order(3)
    void testDeleteOffer() throws Exception {

        List<Offer> offers = service.getAllOffers();

        Offer offer = offers.stream()
                .filter(o -> o.getTitle().equals("Test Offer"))
                .findFirst()
                .orElse(null);

        assertNotNull(offer);

        service.deleteOffer(offer);

        List<Offer> afterDelete = service.getAllOffers();

        boolean exists = afterDelete.stream()
                .anyMatch(o -> o.getId() == offer.getId());

        assertFalse(exists);
    }
}
