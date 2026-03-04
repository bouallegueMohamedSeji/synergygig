package utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Blockchain-style verification for contracts.
 * Creates a SHA-256 hash chain: each contract's hash includes the previous contract's hash,
 * making tampering detectable. Supports DB-backed verification for exact hash matching
 * and full chain integrity checks.
 */
public class BlockchainVerifier {

    private static String lastHash = "0000000000000000000000000000000000000000000000000000000000000000";

    // ════════════════════════════════════════════════════════════
    //  HASH GENERATION
    // ════════════════════════════════════════════════════════════

    /**
     * Generate a blockchain hash for a contract.
     * Hash = SHA-256(previousHash + contractId + terms + amount + timestamp)
     * The timestamp is stored alongside the hash for later re-verification.
     *
     * @param contractId The contract ID
     * @param terms      Contract terms text
     * @param amount     Contract amount
     * @return 64-char hex SHA-256 hash
     */
    public static synchronized String generateHash(int contractId, String terms, double amount) {
        try {
            long timestamp = Instant.now().toEpochMilli();
            String data = lastHash + "|" + contractId + "|" + (terms != null ? terms : "") + "|" + amount + "|" + timestamp;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            String hashHex = bytesToHex(hashBytes);

            // Store the generation record for later re-verification (skip in API mode)
            if (!AppConfig.isApiMode()) {
                storeHashRecord(contractId, hashHex, lastHash, timestamp);
            }

            lastHash = hashHex; // chain to next contract
            return hashHex;
        } catch (Exception e) {
            System.err.println("Hash generation failed: " + e.getMessage());
            return "ERROR_" + System.currentTimeMillis();
        }
    }

    // ════════════════════════════════════════════════════════════
    //  VERIFICATION
    // ════════════════════════════════════════════════════════════

    /**
     * Verify that a hash is a valid 64-char hex SHA-256 string.
     */
    public static boolean isValidHash(String hash) {
        if (hash == null || hash.length() != 64) return false;
        return hash.matches("[0-9a-f]{64}");
    }

    /**
     * DB-backed verification: check if a hash exists in the blockchain ledger
     * and matches a specific contract. Falls back to direct hash comparison
     * when DB is unavailable (e.g. API mode).
     *
     * @param contractId  The contract to verify
     * @param hash        The hash to check
     * @return VerificationResult with match status and details
     */
    public static VerificationResult verifyContract(int contractId, String hash) {
        return verifyContract(contractId, hash, null);
    }

    /**
     * Verify a contract hash with optional stored hash fallback for API mode.
     *
     * @param contractId  The contract to verify
     * @param hash        The hash to check
     * @param storedHash  The contract's stored blockchain hash (used when DB is unavailable)
     * @return VerificationResult with match status and details
     */
    public static VerificationResult verifyContract(int contractId, String hash, String storedHash) {
        if (!isValidHash(hash)) {
            return new VerificationResult(false, false, "Invalid SHA-256 hash format.");
        }

        // In API mode, skip DB verification — use storedHash fallback directly
        if (AppConfig.isApiMode()) {
            if (storedHash != null && !storedHash.isEmpty()) {
                if (hash.equalsIgnoreCase(storedHash)) {
                    return new VerificationResult(true, true,
                            "✅ VERIFIED — Hash matches contract record (API mode).");
                } else {
                    return new VerificationResult(true, false,
                            "❌ MISMATCH — Hash does not match contract #" + contractId + ".");
                }
            }
            return new VerificationResult(isValidHash(hash), false,
                    "⚠️ DB verification unavailable in API mode. Hash format is " + (isValidHash(hash) ? "valid" : "invalid") + ".");
        }

        try (Connection conn = MyDatabase.getInstance().getConnection()) {
            // Check if hash exists in the ledger for this contract
            String sql = "SELECT contract_id, previous_hash, generated_at FROM blockchain_ledger WHERE hash = ? AND contract_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, hash);
                ps.setInt(2, contractId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long generatedAt = rs.getLong("generated_at");
                        String prevHash = rs.getString("previous_hash");
                        return new VerificationResult(true, true,
                                "✅ VERIFIED — Hash matches contract #" + contractId +
                                        ".\nGenerated: " + Instant.ofEpochMilli(generatedAt) +
                                        "\nPrevious hash: " + shortHash(prevHash));
                    }
                }
            }

            // Check if hash exists for a DIFFERENT contract (possible copy/forgery)
            String sqlAny = "SELECT contract_id FROM blockchain_ledger WHERE hash = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlAny)) {
                ps.setString(1, hash);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int otherContractId = rs.getInt("contract_id");
                        return new VerificationResult(true, false,
                                "⚠️ MISMATCH — Hash exists but belongs to contract #" + otherContractId +
                                        ", not contract #" + contractId + ". Possible forgery!");
                    }
                }
            }

            // Hash not found in ledger at all — fall back to comparing against contract's stored hash
            String sqlDirect = "SELECT blockchain_hash FROM contracts WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlDirect)) {
                ps.setInt(1, contractId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String dbHash = rs.getString("blockchain_hash");
                        if (hash.equalsIgnoreCase(dbHash)) {
                            return new VerificationResult(true, true,
                                    "✅ VERIFIED — Hash matches contract record (legacy record, no ledger entry).");
                        } else {
                            return new VerificationResult(true, false,
                                    "❌ MISMATCH — Hash does not match contract #" + contractId + ". Possible tampering!");
                        }
                    }
                }
            }

            return new VerificationResult(false, false, "❌ Contract #" + contractId + " not found.");

        } catch (Exception e) {
            // If DB is unavailable (e.g. API mode), fall back to direct hash comparison
            if (storedHash != null && !storedHash.isEmpty()) {
                if (hash.equalsIgnoreCase(storedHash)) {
                    return new VerificationResult(true, true,
                            "✅ VERIFIED — Hash matches contract record (offline verification).\nDB unavailable, compared against stored hash.");
                } else {
                    return new VerificationResult(true, false,
                            "❌ MISMATCH — Hash does not match contract #" + contractId + ".\nExpected: " + shortHash(storedHash) + "\nReceived: " + shortHash(hash));
                }
            }
            return new VerificationResult(isValidHash(hash), false,
                    "⚠️ DB verification unavailable. Hash format is " + (isValidHash(hash) ? "valid" : "invalid") + ".");
        }
    }

    /**
     * Verify the integrity of the entire hash chain.
     * Checks that each record's previous_hash matches the hash of the prior record.
     *
     * @return ChainIntegrityResult with details
     */
    public static ChainIntegrityResult verifyChainIntegrity() {
        if (AppConfig.isApiMode()) {
            return new ChainIntegrityResult(false, 0, 0, "Chain verification not available in API mode (no direct DB access).");
        }
        try (Connection conn = MyDatabase.getInstance().getConnection()) {
            String sql = "SELECT id, contract_id, hash, previous_hash, generated_at FROM blockchain_ledger ORDER BY id ASC";
            List<String[]> records = new ArrayList<>();
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    records.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            String.valueOf(rs.getInt("contract_id")),
                            rs.getString("hash"),
                            rs.getString("previous_hash"),
                            String.valueOf(rs.getLong("generated_at"))
                    });
                }
            }

            if (records.isEmpty()) {
                return new ChainIntegrityResult(true, 0, 0, "No records in ledger.");
            }

            int valid = 0;
            int broken = 0;
            String expectedPrevHash = "0000000000000000000000000000000000000000000000000000000000000000";

            for (String[] rec : records) {
                String prevHash = rec[3];
                if (expectedPrevHash.equals(prevHash)) {
                    valid++;
                } else {
                    broken++;
                }
                expectedPrevHash = rec[2]; // current hash becomes next expected previous
            }

            boolean intact = broken == 0;
            String msg = intact
                    ? "✅ Chain intact. " + valid + " blocks verified."
                    : "❌ Chain broken! " + broken + " of " + records.size() + " blocks have mismatched links.";

            return new ChainIntegrityResult(intact, valid, broken, msg);

        } catch (Exception e) {
            return new ChainIntegrityResult(false, 0, 0, "Chain verification failed: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    //  LEDGER STORAGE
    // ════════════════════════════════════════════════════════════

    /**
     * Store a hash generation record in the blockchain ledger table.
     */
    private static void storeHashRecord(int contractId, String hash, String previousHash, long timestamp) {
        if (AppConfig.isApiMode()) return; // no JDBC in API mode
        try (Connection conn = MyDatabase.getInstance().getConnection()) {
            // Create table if not exists (idempotent)
            conn.createStatement().executeUpdate(
                "CREATE TABLE IF NOT EXISTS blockchain_ledger (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "contract_id INT NOT NULL, " +
                "hash VARCHAR(64) NOT NULL, " +
                "previous_hash VARCHAR(64) NOT NULL, " +
                "generated_at BIGINT NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "INDEX idx_hash (hash), " +
                "INDEX idx_contract (contract_id)" +
                ")"
            );

            String sql = "INSERT INTO blockchain_ledger (contract_id, hash, previous_hash, generated_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, contractId);
                ps.setString(2, hash);
                ps.setString(3, previousHash);
                ps.setLong(4, timestamp);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            // Non-fatal: hash generation still works, ledger is optional enhancement
            System.err.println("[BlockchainVerifier] Ledger storage failed (non-fatal): " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    //  DISPLAY HELPERS
    // ════════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════════
    //  RESULT TYPES
    // ════════════════════════════════════════════════════════════

    /** Result of verifying a single contract hash */
    public static class VerificationResult {
        public final boolean hashValid;    // format is valid SHA-256
        public final boolean matches;      // matches the contract in DB
        public final String message;       // human-readable result

        public VerificationResult(boolean hashValid, boolean matches, String message) {
            this.hashValid = hashValid;
            this.matches = matches;
            this.message = message;
        }
    }

    /** Result of verifying the full hash chain integrity */
    public static class ChainIntegrityResult {
        public final boolean intact;
        public final int validBlocks;
        public final int brokenBlocks;
        public final String message;

        public ChainIntegrityResult(boolean intact, int validBlocks, int brokenBlocks, String message) {
            this.intact = intact;
            this.validBlocks = validBlocks;
            this.brokenBlocks = brokenBlocks;
            this.message = message;
        }
    }
}
