package tn.esprit.synergygig.dao;

import tn.esprit.synergygig.entities.Milestone;
import tn.esprit.synergygig.utils.MyDBConnexion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MilestoneDAO {

    private final Connection cnx;

    public MilestoneDAO() {
        cnx = MyDBConnexion.getInstance().getCnx();
    }

    public void insert(Milestone milestone) throws SQLException {

        String sql = """
        INSERT INTO milestones (contract_id, title, amount, status)
        VALUES (?, ?, ?, ?)
        """;

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, milestone.getContractId());
        ps.setString(2, milestone.getTitle());
        ps.setDouble(3, milestone.getAmount());
        ps.setString(4, milestone.getStatus());

        ps.executeUpdate();
    }

    public List<Milestone> findByContract(int contractId) throws SQLException {

        List<Milestone> list = new ArrayList<>();

        String sql = "SELECT * FROM milestones WHERE contract_id = ?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, contractId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            Milestone m = new Milestone(
                    rs.getInt("contract_id"),
                    rs.getString("title"),
                    rs.getDouble("amount")
            );

            m.setId(rs.getInt("id"));
            m.setStatus(rs.getString("status"));

            list.add(m);
        }

        return list;
    }
    public void updateStatus(int milestoneId, String status) throws SQLException {

        String sql = "UPDATE milestones SET status = ? WHERE id = ?";

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setString(1, status);
        ps.setInt(2, milestoneId);

        ps.executeUpdate();
    }
    public boolean allMilestonesPaid(int contractId) throws SQLException {

        String sql = """
        SELECT COUNT(*) 
        FROM milestones 
        WHERE contract_id = ? 
        AND status = 'PENDING'
    """;

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, contractId);

        ResultSet rs = ps.executeQuery();
        rs.next();

        return rs.getInt(1) == 0; // si aucune PENDING
    }
}