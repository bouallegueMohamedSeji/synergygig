package tn.esprit.synergygig.services;

import tn.esprit.synergygig.dao.OfferDAO;
import tn.esprit.synergygig.entities.Offer;
import tn.esprit.synergygig.entities.enums.OfferStatus;
import tn.esprit.synergygig.entities.enums.OfferType;

import java.sql.SQLException;
import java.util.List;

public class OfferService {

    private OfferDAO dao;

    public OfferService() {
        dao = new OfferDAO();
    }

    // 🔹 Ajouter une offre (règles métier fortes)
    public void addOffer(Offer o) throws SQLException {

        if (o.getTitle() == null || o.getTitle().isBlank()) {
            throw new IllegalArgumentException("Offer title is required");
        }

        if (o.getType() == null) {
            throw new IllegalArgumentException("Offer type is required");
        }

        // 🔐 règle métier : statut initial contrôlé par le service
        o.setStatus(OfferStatus.DRAFT);

        dao.insertOne(o);
    }

    // 🔹 Récupérer toutes les offres
    public List<Offer> getAllOffers() throws SQLException {
        return dao.selectAll();
    }

    // 🔹 Modifier une offre (règles métier)
    public void updateOffer(Offer o) throws SQLException {

        if (o.getStatus() == OfferStatus.COMPLETED) {
            throw new IllegalStateException("Cannot update a completed offer");
        }

        dao.updateOne(o);
    }

    // 🔹 Supprimer une offre (règles métier)
    public void deleteOffer(Offer o) throws SQLException {

        if (o.getStatus() == OfferStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot delete an offer in progress");
        }

        dao.deleteOne(o);
    }
    public void publishOffer(Offer o) throws SQLException {

        if (o.getStatus() != OfferStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT offers can be published");
        }

        o.setStatus(OfferStatus.PUBLISHED);
        dao.updateOne(o);
    }
    public void cancelOffer(Offer o) throws SQLException {
        if (o.getStatus() == OfferStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed offer");
        }
        o.setStatus(OfferStatus.CANCELLED);
        dao.updateOne(o);
    }

}
