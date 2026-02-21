package tn.esprit.synergygig.services;

import tn.esprit.synergygig.entities.Contract;

import java.security.MessageDigest;

public class BlockchainService {

    public static String generateHash(Contract contract) {

        try {

            String data =
                    String.valueOf(contract.getId()) +
                            contract.getStartDate().toString() +
                            contract.getEndDate().toString() +
                            contract.getTerms();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes("UTF-8"));

            StringBuilder hexString = new StringBuilder();

            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
