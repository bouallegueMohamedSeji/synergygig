import entities.Personne;
import org.junit.jupiter.api.*;
import services.ServicePersonne;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PersonneServiceTest {
 static ServicePersonne sp;
    int idPersonne;
 @BeforeAll
   static void setUp(){
     sp=new ServicePersonne();

 }
 @Test
 @Order(1)
    void testAjouterPersonne() throws SQLException {
     Personne p = new Personne("testName","testPrenom",22);
     sp.ajouter(p);
     List<Personne> personnes = sp.recuperer();
     assertTrue(personnes.stream()
             .anyMatch(per->per.getNom().equals("testName")));
     idPersonne = personnes.get(personnes.size()-1).getId();
     System.out.println(idPersonne);

 }
 @Test
 @Order(2)
    void testModifierPersonne() throws SQLException {
     Personne p = new Personne();
     p.setId(37);
     p.setNom("modifieName");
     p.setPrenom("ModifiePrenom");
     sp.modifier(p);
     List<Personne> personnes = sp.recuperer();
     assertTrue(personnes.stream()
             .anyMatch(per->per.getNom().equals("modifieName")));
 }

}
