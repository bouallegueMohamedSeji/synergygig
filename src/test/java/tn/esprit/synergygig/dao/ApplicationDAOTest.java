package tn.esprit.synergygig.dao;

import org.junit.jupiter.api.*;
import tn.esprit.synergygig.entities.Application;
import tn.esprit.synergygig.entities.enums.ApplicationStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApplicationDAOTest {

    static ApplicationDAO dao;
    static Application testApp;

    static final int EXISTING_OFFER_ID = 1; // ⚠ mettre un id réel
    static final int EXISTING_USER_ID = 4;  // ⚠ user existant

    @BeforeAll
    static void setup() {
        dao = new ApplicationDAO();
    }

    // ===============================
    // TEST INSERT
    // ===============================
    @Test
    @Order(1)
    void testInsert() throws Exception {

        testApp = new Application(
                EXISTING_OFFER_ID,
                EXISTING_USER_ID
        );


        dao.insert(testApp);

        List<Application> apps = dao.selectAll();

        boolean exists = apps.stream()
                .anyMatch(a ->
                        a.getOfferId() == EXISTING_OFFER_ID &&
                                a.getApplicantId() == EXISTING_USER_ID
                );

        assertTrue(exists);
    }

    // ===============================
    // TEST EXISTS
    // ===============================
    @Test
    @Order(2)
    void testExists() throws Exception {

        boolean exists = dao.exists(EXISTING_OFFER_ID, EXISTING_USER_ID);
        assertTrue(exists);
    }

    // ===============================
    // TEST UPDATE STATUS
    // ===============================
    @Test
    @Order(3)
    void testUpdateStatus() throws Exception {

        List<Application> apps = dao.selectAll();

        Application app = apps.stream()
                .filter(a ->
                        a.getOfferId() == EXISTING_OFFER_ID &&
                                a.getApplicantId() == EXISTING_USER_ID
                )
                .findFirst()
                .orElse(null);

        assertNotNull(app);

        dao.updateStatus(app.getId(), ApplicationStatus.ACCEPTED);

        List<Application> updated = dao.selectAll();

        Application afterUpdate = updated.stream()
                .filter(a -> a.getId() == app.getId())
                .findFirst()
                .orElse(null);

        assertEquals(ApplicationStatus.ACCEPTED, afterUpdate.getStatus());
    }
}
