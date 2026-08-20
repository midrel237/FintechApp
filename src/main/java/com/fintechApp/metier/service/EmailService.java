package com.fintechApp.metier.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Envoie le code de validation par email.
     *
     * L'envoi est volontairement non bloquant pour le flux d'inscription :
     * si le serveur SMTP configuré (application.yaml) est indisponible, mal
     * authentifié ou injoignable depuis l'environnement de test, on ne veut
     * pas faire échouer toute la création d'utilisateur (rollback de
     * @Transactional dans UtilisateurService) pour un simple problème
     * d'envoi de mail. L'échec est journalisé mais n'interrompt pas
     * l'inscription — utile en particulier pour tester via Postman sans
     * SMTP réel configuré. Le code reste consultable en base de données
     * (colonne code_validation) pour valider manuellement le compte pendant
     * les tests.
     */
    public void envoyerEmail(String destinataire, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(destinataire);
            message.setSubject("Verification de votre compte");
            message.setText("Votre code de validation est: " + code);

            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Échec de l'envoi de l'email de validation à {} : {}", destinataire, e.getMessage());
        }
    }

}
