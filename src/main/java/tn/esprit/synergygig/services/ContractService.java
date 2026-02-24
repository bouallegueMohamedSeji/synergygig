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
    private final OllamaService ollamaService = new OllamaService();



    // ================= GENERATE CONTRACT + AI + PDF + EMAIL =================
    // ================= GENERATE CONTRACT + AI + PDF + EMAIL =================
    public void generateContract(
            Contract contract,
            String clientEmail,
            String clientName
    ) {

        try {

            // 1️⃣ Status
            contract.setStatus(ContractStatus.GENERATED);

            // 2️⃣ 🔥 RISK ANALYSIS (OLLAMA)
            double riskScore =
                    ollamaService.analyzeRisk(contract.getTerms());

            contract.setRiskScore(riskScore);

            System.out.println("🔥 RISK GENERATED = " + riskScore);

            // 3️⃣ Insert initial
            contractDAO.insert(contract);

            // 4️⃣ Generate full legal contract
            String legalText =
                    ollamaService.generateLegalContract(contract);

            if (legalText == null || legalText.isBlank()) {
                legalText = contract.getTerms();
            }

            contract.setAiFullContract(legalText);

            // 5️⃣ Blockchain hash
            String hash = BlockchainService.generateHash(contract);
            contract.setBlockchainHash(hash);

            // 6️⃣ Update DB
            contractDAO.update(contract);

            // 7️⃣ Generate PDF
            String pdfPath = pdfService.generatePDF(contract);

            // 8️⃣ Send email
            if (pdfPath != null) {
                emailService.sendContractEmail(clientName, pdfPath);
            }

            System.out.println("🔥 Contract workflow completed PRO version");

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
    public void analyzeWithAI(Contract contract) {

        try {

            String summary =
                    ollamaService.summarize(contract.getTerms());

            contract.setAiSummary(summary);
            contractDAO.update(contract);


            System.out.println("🤖 AI analysis saved.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public Contract getByApplicationId(int appId) throws Exception {
        return contractDAO.findByApplicationId(appId);
    }
    public void improveWithAI(Contract contract) {

        try {

            String improved =
                    ollamaService.improve(contract.getTerms());

            contract.setAiImproved(improved);

            contractDAO.update(contract);

            System.out.println("✨ Improved version saved.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
