package tn.esprit.synergygig.services;

import tn.esprit.synergygig.dao.ApplicationDAO;
import tn.esprit.synergygig.entities.Application;
import tn.esprit.synergygig.entities.Offer;
import tn.esprit.synergygig.entities.enums.OfferStatus;
import tn.esprit.synergygig.entities.enums.ApplicationStatus;
import java.util.List;
import tn.esprit.synergygig.dao.OfferDAO;
import tn.esprit.synergygig.entities.enums.ApplicationStatus;
import tn.esprit.synergygig.entities.enums.OfferStatus;



import java.sql.SQLException;

public class ApplicationService {

    private final ApplicationDAO dao = new ApplicationDAO();
    private final OfferDAO offerDao = new OfferDAO(); // ✅ ICI

    public void apply(Offer offer, int userId) throws Exception {

        if (offer.getStatus() != OfferStatus.PUBLISHED) {
            throw new IllegalStateException("Offer is not published");
        }

        if (dao.exists(offer.getId(), userId)) {
            throw new IllegalStateException("You already applied to this offer");
        }

        Application app = new Application(offer.getId(), userId);
        dao.insert(app);
    }
    public void accept(Application app) throws SQLException {

        Application fresh = dao.findById(app.getId());

        if (fresh.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalStateException("Application déjà traitée");
        }

        dao.updateStatus(app.getId(), ApplicationStatus.ACCEPTED);
        offerDao.updateStatus(app.getOfferId(), OfferStatus.IN_PROGRESS);
    }

    public void reject(Application app) throws SQLException {

        if (app.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalStateException("Application déjà traitée");
        }

        dao.updateStatus(app.getId(), ApplicationStatus.REJECTED);
    }
    public List<Application> getAll() throws SQLException {
        return dao.selectAll();
    }
}
