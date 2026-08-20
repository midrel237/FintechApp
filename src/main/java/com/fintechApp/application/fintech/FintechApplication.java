package com.fintechApp.application.fintech;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Classe principale de démarrage de l'application.
 *
 * IMPORTANT : cette classe se trouve dans le package
 * com.fintechApp.application.fintech, alors que tous les composants Spring
 * (@Service, @RestController, @Repository, @Configuration...) se trouvent
 * dans des packages FRERES : com.fintechApp.metier, .presentation,
 * .persistance, .infrastructure.
 *
 * Par défaut, @SpringBootApplication ne scanne que le package de la classe
 * qui le porte et ses sous-packages. Sans scanBasePackages explicite,
 * AUCUN des beans du projet n'est détecté et l'application ne fait
 * effectivement rien (aucun contrôleur, aucun service, aucun repository
 * disponible), sans forcément lever d'erreur bruyante au démarrage.
 *
 * On étend donc explicitement le scan à la racine com.fintechApp, ce qui
 * couvre également la découverte des entités JPA et des repositories
 * Spring Data (ils dérivent du même scanBasePackages).
 */
@SpringBootApplication(scanBasePackages = "com.fintechApp")
@EntityScan(basePackages = "com.fintechApp.persistance.entity")
@EnableJpaRepositories(basePackages = "com.fintechApp.persistance.repository")
public class FintechApplication {

	public static void main(String[] args) {
		SpringApplication.run(FintechApplication.class, args);
	}

}
