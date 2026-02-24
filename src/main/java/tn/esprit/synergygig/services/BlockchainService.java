package tn.esprit.synergygig.services;

import tn.esprit.synergygig.entities.Contract;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

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
    public static String generateFingerprint(Contract contract) {

        try {

            String canonicalData =
                    contract.getId() +
                            "|" + contract.getApplicationId() +
                            "|" + contract.getStartDate() +
                            "|" + contract.getEndDate() +
                            "|" + contract.getAmount() +
                            "|" + safe(contract.getAiFullContract()) +
                            "|" + safe(contract.getAiSummary()) +
                            "|" + safe(contract.getAiImproved()) +
                            "|" + contract.getRiskScore() +
                            "|" + contract.getCreatedAt();

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hashBytes =
                    digest.digest(canonicalData.getBytes(StandardCharsets.UTF_8));

            return bytesToHex(hashBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ================= UTILITY =================
    private static String bytesToHex(byte[] hashBytes) {

        StringBuilder hexString = new StringBuilder();

        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }

        return hexString.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
    public static boolean verifyFingerprint(Contract contract) {

        String recalculated =
                generateFingerprint(contract);

        return recalculated != null &&
                recalculated.equals(contract.getFingerprint());
    }
}