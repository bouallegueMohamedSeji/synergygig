package tn.esprit.synergygig.services;

import org.junit.jupiter.api.*;
import tn.esprit.synergygig.entities.Application;
import tn.esprit.synergygig.entities.Offer;
import tn.esprit.synergygig.entities.enums.ApplicationStatus;
import tn.esprit.synergygig.entities.enums.OfferStatus;
import tn.esprit.synergygig.entities.enums.OfferType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApplicationServiceTest {

    static OfferService offerService;
    static ApplicationService applicationService;
    static Offer testOffer;
    static Application testApplication;

    static final int TEST_USER_ID = 4;      // GIG_WORKER
    static final int SECOND_USER_ID = 3;    // EMPLOYEE


    @BeforeAll
    static void setup() throws Exception {

        offerService = new OfferService();
        applicationService = new ApplicationService();

        testOffer = new Offer(
                "Test Application Offer",
                "Offer for testing application",
                OfferType.GIG,
                TEST_USER_ID,
                "test.jpg"
        );

        offerService.addOffer(testOffer);

        // 🔥 récupérer la dernière offre insérée
        List<Offer> offers = offerService.getAllOffers();
        testOffer = offers.get(offers.size() - 1);

        assertNotNull(testOffer);

        offerService.publishOffer(testOffer);
    }


    // ===============================
    // TEST APPLY
    // ===============================
    @Test
    @Order(1)
    void testApply() throws Exception {

        applicationService.apply(testOffer, TEST_USER_ID);

        testApplication = applicationService.getAll().stream()
                .filter(a -> a.getOfferId() == testOffer.getId())
                .findFirst()
                .orElse(null);

        assertNotNull(testApplication);
        assertEquals(ApplicationStatus.PENDING, testApplication.getStatus());
    }

    // ===============================
    // TEST ACCEPT
    // ===============================
    @Test
    @Order(2)
    void testAccept() throws Exception {

        applicationService.accept(testApplication);

        Application updated = applicationService.getAll().stream()
                .filter(a -> a.getId() == testApplication.getId())
                .findFirst()
                .orElse(null);

        assertNotNull(updated);
        assertEquals(ApplicationStatus.ACCEPTED, updated.getStatus());

        Offer updatedOffer = offerService.getAllOffers().stream()
                .filter(o -> o.getId() == testOffer.getId())
                .findFirst()
                .orElse(null);

        assertNotNull(updatedOffer);
        assertEquals(OfferStatus.IN_PROGRESS, updatedOffer.getStatus());
    }

    // ===============================
    // TEST REJECT
    // ===============================
    @Test
    @Order(3)
    void testReject() throws Exception {

        applicationService.apply(testOffer, SECOND_USER_ID);

        Application newApp = applicationService.getAll().stream()
                .filter(a -> a.getApplicantId() == SECOND_USER_ID)
                .findFirst()
                .orElse(null);

        assertNotNull(newApp);

        applicationService.reject(newApp);

        Application rejected = applicationService.getAll().stream()
                .filter(a -> a.getId() == newApp.getId())
                .findFirst()
                .orElse(null);

        assertNotNull(rejected);
        assertEquals(ApplicationStatus.REJECTED, rejected.getStatus());
    }

}
