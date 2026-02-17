package tn.esprit.synergygig.services;

import tn.esprit.synergygig.dao.OfferDAO;
import tn.esprit.synergygig.entities.enums.OfferStatus;

import java.sql.SQLException;

public class DashboardService {

    private final OfferDAO offerDAO = new OfferDAO();

    public int countAllOffers() throws SQLException {
        return offerDAO.countAll();
    }

    public int countByStatus(OfferStatus status) throws SQLException {
        return offerDAO.countByStatus(status);
    }
}
