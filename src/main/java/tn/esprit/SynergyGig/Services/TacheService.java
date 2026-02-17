package tn.esprit.SynergyGig.Services;

import tn.esprit.SynergyGig.DAO.TacheDao;
import tn.esprit.SynergyGig.models.Tache;

import java.util.List;

public class TacheService {

    private final TacheDao tacheDAO = new TacheDao();

    public void ajouterTache(Tache t) {
        tacheDAO.ajouterTache(t);
    }

    public List<Tache> afficherTaches() {
        return tacheDAO.afficherTaches();
    }

    public List<Tache> afficherTachesParProjet(int projetId) {
        return tacheDAO.afficherTachesParProjet(projetId);
    }

    public void modifierTache(Tache t) {
        tacheDAO.modifierTache(t);
    }

    public void supprimerTache(int id) {
        tacheDAO.supprimerTache(id);
    }
}
