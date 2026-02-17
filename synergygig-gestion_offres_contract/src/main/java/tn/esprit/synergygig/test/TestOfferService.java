package tn.esprit.synergygig.test;

import tn.esprit.synergygig.entities.Offer;
import tn.esprit.synergygig.entities.enums.OfferStatus;
import tn.esprit.synergygig.entities.enums.OfferType;
import tn.esprit.synergygig.services.OfferService;

public class TestOfferService {

    public static void main(String[] args) throws Exception {

        OfferService service = new OfferService();




        service.getAllOffers().forEach(System.out::println);
    }
}
