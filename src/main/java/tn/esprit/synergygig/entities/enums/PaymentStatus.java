package tn.esprit.synergygig.entities.enums;

public enum PaymentStatus {
    UNPAID,
    AUTHORIZED,   // Argent bloqué (escrow)
    CAPTURED,     // Paiement libéré
    REFUNDED      // Remboursement
}