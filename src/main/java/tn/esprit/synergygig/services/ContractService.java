package tn.esprit.synergygig.services;

import com.stripe.model.PaymentIntent;
import tn.esprit.synergygig.dao.ContractDAO;
import tn.esprit.synergygig.entities.Contract;
import tn.esprit.synergygig.entities.enums.ContractStatus;
import tn.esprit.synergygig.entities.enums.PaymentStatus;

public class ContractService {

    private final ContractDAO contractDAO = new ContractDAO();
    private final PaymentService paymentService = new PaymentService();

    // ================= GENERATE CONTRACT + ESCROW =================
    public void generateContractWithPayment(Contract contract) {

        try {

            // 1️⃣ Création Escrow Stripe
            PaymentIntent intent =
                    paymentService.createPaymentIntent(contract.getAmount());

            // 2️⃣ Mise à jour données contrat
            contract.setPaymentIntentId(intent.getId());
            contract.setPaymentStatus(PaymentStatus.AUTHORIZED);
            contract.setStatus(ContractStatus.GENERATED);

            // 3️⃣ Sauvegarde DB
            contractDAO.insert(contract);

            // 4️⃣ Génération automatique des milestones
            MilestoneService milestoneService = new MilestoneService();
            milestoneService.generateDefaultMilestones(
                    contract.getId(),
                    contract.getAmount()
            );

            System.out.println("✅ Contract generated + Escrow + Milestones");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // ================= RELEASE PAYMENT =================
    public void releasePayment(Contract contract) {

        try {

            paymentService.capturePayment(contract.getPaymentIntentId());

            contract.setPaymentStatus(PaymentStatus.CAPTURED);
            contract.setStatus(ContractStatus.IN_PROGRESS);

            contractDAO.update(contract);

            System.out.println("💰 Payment captured successfully");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= REFUND =================
    public void refundPayment(Contract contract) {

        try {

            paymentService.refundPayment(contract.getPaymentIntentId());

            contract.setPaymentStatus(PaymentStatus.REFUNDED);

            contractDAO.update(contract);

            System.out.println("↩ Payment refunded");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}