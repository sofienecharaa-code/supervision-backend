# Supervision Backend

API REST développée avec **Spring Boot** pour la supervision centralisée d'infrastructures virtualisées **Proxmox VE** et **VMware ESXi**.

Ce backend fait partie d'un projet de stage : une application web de supervision permettant de regrouper et visualiser en temps réel l'état des infrastructures virtualisées (serveurs hôtes, VMs, containers).

## Stack technique

- **Java 17** / Spring Boot 4.1
- **MongoDB** (base de données)
- **Proxmox VE API** (REST) pour l'intégration Proxmox
- **YAVIJAVA** (API VIM/SOAP) pour l'intégration VMware ESXi
- **Spring Security + JWT** pour l'authentification
- **Spring Scheduling** pour la synchronisation automatique

## Fonctionnalités

- Connexion aux API Proxmox et VMware (réelles, testées en conditions réelles)
- Synchronisation automatique périodique (intervalle configurable)
- Centralisation des hosts, VMs et containers dans MongoDB
- Historique des métriques (CPU/RAM/Stockage) dans le temps
- Détection de panne : passage automatique en statut "hors ligne" si un serveur devient injoignable
- Actions Start/Stop sur les VMs/containers
- Authentification JWT pour sécuriser l'accès à l'API

## Architecture

- `config/` — Configuration Proxmox, VMware, RestTemplate
- `controller/` — Endpoints REST
- `model/` — Entités MongoDB (Host, VirtualMachine, MetricHistory)
- `repository/` — Interfaces Spring Data MongoDB
- `security/` — JWT, filtres, configuration de sécurité
- `service/` — Logique métier (synchronisation, historique)

Tous ces packages se trouvent sous `src/main/java/com/supervision/supervisionbackend/`
## Configuration

1. Copie `application-example.properties` vers `application.properties`
2. Renseigne tes propres valeurs (identifiants MongoDB, Proxmox, VMware, JWT)
3. `application.properties` n'est jamais versionné (voir `.gitignore`)

## Lancer le projet

```bash
mvn clean install
mvn spring-boot:run
```

L'API démarre sur `http://localhost:8080`.

## Projet associé

Le frontend Angular correspondant : [supervision-frontend](https://github.com/sofienecharaa-code/supervision-frontend)

## Contexte

Projet réalisé dans le cadre d'un stage, avec connexion réelle et testée à des infrastructures Proxmox VE (Hyper-V) et VMware ESXi 8.0 (VMware Workstation).