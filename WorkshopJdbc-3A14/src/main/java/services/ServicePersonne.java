package services;

import entities.Personne;
import utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ServicePersonne implements IService<Personne> {

    private Connection connection;
    public ServicePersonne() {
        connection= MyDatabase.getInstance().getConnection();
    }

    @Override
    public void ajouter(Personne personne) throws SQLException {

        String req = "INSERT INTO personne(nom, prenom, age)"+
                "VALUES ('"+personne.getNom()+"','"+personne.getPrenom()+"',"+personne.getAge()+")";
        Statement st = connection.createStatement();
        st.executeUpdate(req);

    }

    @Override
    public void modifier(Personne personne) throws SQLException {
        String req = "update personne set nom=?, prenom=?, age=? where id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, personne.getNom());
        ps.setString(2, personne.getPrenom());
        ps.setInt(3, personne.getAge());
        ps.setInt(4, personne.getId());
        ps.executeUpdate();
        System.out.println("personne modifie");


    }

    @Override
    public void supprimer(Personne personne) throws SQLException {

    }

    @Override
    public List<Personne> recuperer() throws SQLException {
        List<Personne> personnes = new ArrayList<>();
        String req = "select * from personne ";
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery(req);
        while (rs.next()) {
            int idP = rs.getInt("id");
            String nomP = rs.getString(2);
            String prenomP = rs.getString(3);
            int ageP = rs.getInt(4);
            Personne personne = new Personne(idP, nomP, prenomP, ageP);
            personnes.add(personne);

        }

        return personnes;
    }
}
