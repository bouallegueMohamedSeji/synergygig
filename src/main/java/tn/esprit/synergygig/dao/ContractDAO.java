package tn.esprit.synergygig.dao;

import tn.esprit.synergygig.entities.Contract;
import tn.esprit.synergygig.entities.enums.ContractStatus;
import tn.esprit.synergygig.entities.enums.PaymentStatus;
import tn.esprit.synergygig.utils.MyDBConnexion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ContractDAO {

    private final Connection cnx;

    public ContractDAO() {
        cnx = MyDBConnexion.getInstance().getCnx();
    }

    // ================= INSERT =================
    public void insert(Contract contract) throws SQLException {

        String sql = """
        INSERT INTO contracts 
        (application_id, start_date, end_date, amount, terms, status,
         payment_intent_id, payment_status, blockchain_hash)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        PreparedStatement ps = cnx.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS   // 🔥 IMPORTANT
        );

        ps.setInt(1, contract.getApplicationId());
        ps.setDate(2, Date.valueOf(contract.getStartDate()));
        ps.setDate(3, Date.valueOf(contract.getEndDate()));
        ps.setDouble(4, contract.getAmount());
        ps.setString(5, contract.getTerms());
        ps.setString(6, contract.getStatus().name());
        ps.setString(7, contract.getPaymentIntentId());
        ps.setString(8, contract.getPaymentStatus().name());
        ps.setString(9, contract.getBlockchainHash());

        ps.executeUpdate();

        // 🔥 Récupérer ID généré
        ResultSet rs = ps.getGeneratedKeys();
        if (rs.next()) {
            contract.setId(rs.getInt(1));
        }

        System.out.println("✅ Contract inserted successfully");
    }
    // ================= UPDATE =================
    public void update(Contract contract) throws SQLException {

        String sql = """
        UPDATE contracts 
        SET status = ?, 
            payment_intent_id = ?, 
            payment_status = ?,
            blockchain_hash = ?
        WHERE id = ?
        """;

        PreparedStatement ps = cnx.prepareStatement(sql);

        ps.setString(1, contract.getStatus().name());
        ps.setString(2, contract.getPaymentIntentId());
        ps.setString(3, contract.getPaymentStatus().name());
        ps.setString(4, contract.getBlockchainHash());
        ps.setInt(5, contract.getId());

        ps.executeUpdate();

        System.out.println("✏ Contract updated successfully");
    }

    // ================= SELECT ALL =================
    public List<Contract> selectAll() throws SQLException {

        List<Contract> list = new ArrayList<>();

        String sql = "SELECT * FROM contracts";

        Statement st = cnx.createStatement();
        ResultSet rs = st.executeQuery(sql);

        while (rs.next()) {

            Contract contract = new Contract(
                    rs.getInt("id"),
                    rs.getInt("application_id"),
                    rs.getDate("start_date").toLocalDate(),
                    rs.getDate("end_date").toLocalDate(),
                    rs.getDouble("amount"),
                    rs.getString("terms"),
                    ContractStatus.valueOf(rs.getString("status")),
                    rs.getString("payment_intent_id"),
                    PaymentStatus.valueOf(rs.getString("payment_status")),
                    rs.getString("blockchain_hash"),
                    rs.getTimestamp("created_at").toLocalDateTime()
            );

            list.add(contract);
        }

        return list;
    }
}