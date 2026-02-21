package tn.esprit.synergygig.services;

import tn.esprit.synergygig.dao.ContractDAO;
import tn.esprit.synergygig.entities.Contract;
import tn.esprit.synergygig.entities.enums.ContractStatus;

import java.util.List;

public class ContractService {

    private final ContractDAO contractDAO = new ContractDAO();

    private final AiRiskService aiRiskService = new AiRiskService();
    private final ContractPDFService pdfService = new ContractPDFService();
    private final EmailService emailService = new EmailService();

    // ================= GENERATE CONTRACT + AI + PDF + EMAIL =================
    public void generateContract(
            Contract contract,
            String clientEmail,
            String clientName
    ) {

        try {

            // 1️⃣ Status initial
            contract.setStatus(ContractStatus.GENERATED);

            // 2️⃣ Analyse IA
            double riskScore =
                    aiRiskService.analyzeRisk(contract.getTerms());

            contract.setRiskScore(riskScore);

            // 3️⃣ Insert DB
            contractDAO.insert(contract);

            // 4️⃣ Génération PDF
            String pdfPath =
                    pdfService.generatePDF(contract);

            // 5️⃣ Envoi email
            emailService.sendContractEmail(
                    clientName,
                    pdfPath
            );


            System.out.println("✅ Contract + AI + PDF + Email ready");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= START WORK =================
    public void startWork(Contract contract) {

        try {

            contract.setStatus(ContractStatus.IN_PROGRESS);
            contractDAO.update(contract);

            System.out.println("🚀 Work started");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= COMPLETE =================
    public void completeContract(Contract contract) {

        try {

            contract.setStatus(ContractStatus.COMPLETED);

            // 🔥 Génération Hash blockchain
            String hash = BlockchainService.generateHash(contract);
            contract.setBlockchainHash(hash);

            contractDAO.update(contract);

            System.out.println("🏁 Contract completed + blockchain hash generated");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public List<Contract> getAllContracts() throws Exception {
        return contractDAO.selectAll();
    }
    public boolean verifyContract(String hash) throws Exception {
        return contractDAO.existsByHash(hash);
    }

}
