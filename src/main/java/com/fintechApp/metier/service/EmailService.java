package com.fintechApp.metier.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    // Expéditeur explicite : sans "from", certains serveurs SMTP (dont Gmail)
    // rejettent le message ou l'affichent avec une adresse vide côté
    // destinataire. On réutilise le compte SMTP configuré (spring.mail.username).
    @Value("${spring.mail.username}")
    private String expediteur;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // Envoie le code de validation par email.
     
    public void envoyerEmail(String destinataire, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(expediteur);
            message.setTo(destinataire);
            message.setSubject("Vérification de votre compte");
            message.setText("Votre code de validation est : " + code
                    + "\n\nCe code expire dans 24 heures.");

            mailSender.send(message);
            log.info("Email de validation envoyé avec succès à {}", destinataire);
        } catch (MailException e) {
            log.error("Échec de l'envoi de l'email de validation à {} : {}", destinataire, e.getMessage(), e);
        }
    }

}
