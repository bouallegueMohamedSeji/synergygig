package tn.esprit.synergygig.dao;

import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.utils.MyDBConnexion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserDAO {

    private final Connection cnx;

    public UserDAO() {
        cnx = MyDBConnexion.getInstance().getCnx();
    }

    public User selectById(int id) throws Exception {

        String sql = "SELECT * FROM users WHERE id = ?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, id);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return new User(
                    rs.getInt("id"),
                    rs.getString("full_name"),
                    rs.getString("email"),
                    rs.getString("password"),
                    rs.getString("role"),
                    rs.getTimestamp("created_at").toLocalDateTime()
            );
        }

        return null;
    }
}
