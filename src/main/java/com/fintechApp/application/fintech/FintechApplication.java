package com.fintechApp.application.fintech;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Classe principale de démarrage de l'application.
 *
 *
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
