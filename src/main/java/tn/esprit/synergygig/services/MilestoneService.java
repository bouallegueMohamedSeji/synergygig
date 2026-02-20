package tn.esprit.synergygig.services;

import tn.esprit.synergygig.dao.ContractDAO;
import tn.esprit.synergygig.dao.MilestoneDAO;
import tn.esprit.synergygig.entities.Contract;
import tn.esprit.synergygig.entities.Milestone;
import tn.esprit.synergygig.entities.enums.ContractStatus;

public class MilestoneService {

    private final MilestoneDAO dao = new MilestoneDAO();
    private final ContractDAO contractDAO = new ContractDAO();

    public void payMilestone(int milestoneId, int contractId) {

        try {

            // 1️⃣ Update milestone
            dao.updateStatus(milestoneId, "PAID");

            System.out.println("💰 Milestone paid");

            // 2️⃣ Vérifier si toutes payées
            if (dao.allMilestonesPaid(contractId)) {

                System.out.println("🎉 All milestones paid");

                // 3️⃣ Charger contrat
                Contract contract = contractDAO.selectAll()
                        .stream()
                        .filter(c -> c.getId() == contractId)
                        .findFirst()
                        .orElse(null);

                if (contract != null) {

                    // 4️⃣ COMPLETED
                    contract.setStatus(ContractStatus.COMPLETED);

                    // 5️⃣ Générer Blockchain hash
                    String hash = BlockchainService.generateHash(contract);
                    contract.setBlockchainHash(hash);

                    contractDAO.update(contract);

                    System.out.println("🔐 Contract completed + Blockchain hash generated");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}