package com.fintechApp.infrastructure.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.fintechApp.persistance.entity.StatutUtilisateur;
import com.fintechApp.persistance.entity.Utilisateur;
import com.fintechApp.persistance.repository.UtilisateurRepository;

@Service
public class UtilisateurDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    public UtilisateurDetailsService(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
                .orElseThrow(() ->
                    new UsernameNotFoundException(
                        "Utilisateur non trouvé avec l'email : " + email
                    )
                );

        // RG : "un utilisateur est inscrit avec un statut initial verrouillé ;
        // il ne peut interagir avec le système qu'après validation."
        // En marquant le compte "disabled" tant qu'il n'est pas ACTIF, Spring
        // Security refuse lui-même l'authentification (DisabledException)
        // sans qu'on ait à réécrire cette vérification manuellement ailleurs.
        boolean actif = utilisateur.getStatut() == StatutUtilisateur.actif;

        return org.springframework.security.core.userdetails.User
                .withUsername(utilisateur.getEmail())
                .password(utilisateur.getMotPasse())
                .authorities("USER")
                .disabled(!actif)
                .build();
    }
}
