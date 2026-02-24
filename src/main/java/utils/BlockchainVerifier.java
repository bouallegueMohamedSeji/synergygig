package utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Lightweight blockchain-style verification for contracts.
 * Creates a SHA-256 hash chain: each contract's hash includes the previous contract's hash,
 * making tampering detectable. This is NOT a real blockchain — it's a verifiable hash chain
 * suitable for academic demonstration.
 */
public class BlockchainVerifier {

    private static String lastHash = "0000000000000000000000000000000000000000000000000000000000000000";

    /**
     * Generate a blockchain hash for a contract.
     * Hash = SHA-256(previousHash + contractId + terms + amount + timestamp)
     *
     * @param contractId The contract ID
     * @param terms      Contract terms text
     * @param amount     Contract amount
     * @return 64-char hex SHA-256 hash
     */
    public static synchronized String generateHash(int contractId, String terms, double amount) {
        try {
            String data = lastHash + "|" + contractId + "|" + (terms != null ? terms : "") + "|" + amount + "|" + Instant.now().toEpochMilli();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            String hashHex = bytesToHex(hashBytes);
            lastHash = hashHex; // chain to next contract
            return hashHex;
        } catch (Exception e) {
            System.err.println("Hash generation failed: " + e.getMessage());
            return "ERROR_" + System.currentTimeMillis();
        }
    }

    /**
     * Verify that a hash matches the expected input data.
     * Note: Since we include a timestamp in generation, exact verification requires
     * storing the original timestamp. This method checks hash format validity.
     *
     * @param hash The hash to verify
     * @return true if the hash is a valid 64-char hex string
     */
    public static boolean isValidHash(String hash) {
        if (hash == null || hash.length() != 64) return false;
        return hash.matches("[0-9a-f]{64}");
    }

    /**
     * Get a shortened display version of the hash.
     */
    public static String shortHash(String hash) {
        if (hash == null || hash.length() < 16) return hash != null ? hash : "N/A";
        return hash.substring(0, 8) + "..." + hash.substring(hash.length() - 8);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
