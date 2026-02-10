package tn.esprit.projet.services;

import tn.esprit.projet.entities.Person;
import tn.esprit.projet.utils.MyDBConnexion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PersonService implements CRUD<Person> {

    private Connection cnx;

    public PersonService(){
        cnx = MyDBConnexion.getInstance().getCnx();
    }

    @Override
    public void insertOne(Person person) throws SQLException {
        String req = "INSERT INTO `person`(`nom`, `prenom`, `salaire`) VALUES " +
                "( '"+person.getNom()+"' ,  '"+person.getPrenom()+"' , "+person.getSalaire()+")";
        //CREATION
        Statement st = cnx.createStatement();

        // PREPARATION => COMPILATION => EXECUTION
        st.executeUpdate(req);
    }

    public void insertOneUpdated(Person person) throws SQLException {
        String req = "INSERT INTO `person`(`nom`, `prenom`, `salaire`) VALUES " +
                "(?, ?, ?)";
        //CREATION => PREPARATION => COMPILATION
        PreparedStatement ps = cnx.prepareStatement(req);

        ps.setString(1, person.getNom());
        ps.setString(2, person.getPrenom());
        ps.setDouble(3, person.getSalaire());

        //EXECUTION
        ps.executeUpdate();
    }

    @Override
    public void updateOne(Person person) throws SQLException {

    }

    @Override
    public void deleteOne(Person person) throws SQLException {

    }

    @Override
    public List<Person> selectALL() throws SQLException {
        List<Person> userList = new ArrayList<>();

        String req = "SELECT * FROM `person`";
        Statement st = cnx.createStatement();

        ResultSet rs = st.executeQuery(req);

        while (rs.next()) {

            Person p = new Person(
                    rs.getInt(1),
                    rs.getString("nom"),
                    rs.getString(3),
                    rs.getDouble(4)
            );

            userList.add(p);
        }

        return userList;
    }
}
