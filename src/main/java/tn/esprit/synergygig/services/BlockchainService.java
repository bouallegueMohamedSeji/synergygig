package tn.esprit.synergygig.services;

import tn.esprit.synergygig.entities.Contract;   // 🔥 AJOUTER ÇA
import org.apache.commons.codec.digest.DigestUtils;

public class BlockchainService {

    public static String generateHash(Contract contract) {

        String data =
                contract.getId() +
                        contract.getApplicationId() +
                        contract.getAmount() +
                        contract.getStartDate() +
                        contract.getEndDate() +
                        contract.getStatus() +
                        contract.getPaymentStatus();

        return org.apache.commons.codec.digest.DigestUtils.sha256Hex(data);
    }
}