package tn.esprit.synergygig.test;

import tn.esprit.synergygig.dao.OfferDAO;
import tn.esprit.synergygig.entities.Offer;
import tn.esprit.synergygig.entities.enums.OfferStatus;
import tn.esprit.synergygig.entities.enums.OfferType;

import java.sql.SQLException;
import java.util.List;

public class TestOfferDAO {

    public static void main(String[] args) {

        OfferDAO dao = new OfferDAO();

        try {
            // 🔹 INSERT


            // 🔹 SELECT ALL
            List<Offer> offers = dao.selectAll();
            offers.forEach(System.out::println);

            // 🔹 UPDATE (modifier la dernière offre)
            Offer lastOffer = offers.get(offers.size() - 1);
            lastOffer.setTitle("UPDATED Java Mission");
            lastOffer.setStatus(OfferStatus.IN_PROGRESS);
            dao.updateOne(lastOffer);

            // 🔹 DELETE (optionnel)
            // dao.deleteOne(lastOffer);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
