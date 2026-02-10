package tn.esprit.projet.services;

import java.sql.SQLException;
import java.util.List;

public interface CRUD<T> {

    void insertOne(T t) throws SQLException;
    void updateOne(T t) throws SQLException;
    void deleteOne(T t) throws SQLException;
    List<T> selectALL() throws SQLException;
}
