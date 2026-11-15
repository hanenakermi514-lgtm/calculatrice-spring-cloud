# TP4 : Calculatrice en microservices avec Spring Cloud

**Filière :** MPSRCC1 · **Enseignantes :** Corinne Amel ZAYANI, Amal ABID
**Projet :** `calculatrice-spring-cloud` (Spring Boot 4.0.9, Spring Cloud 2025.1.x, springdoc-openapi 3.0.3, Lombok)

---

## 1. Objectif

Faire évoluer l'architecture du TP3 (deux microservices reliés par des adresses écrites en dur) vers une architecture Spring Cloud qui corrige trois faiblesses :

1. L'adresse de `calcul-service` est écrite en dur → **annuaire Eureka** (découverte de services).
2. Le client doit connaître le port de chaque microservice → **Spring Cloud Gateway** (point d'entrée unique).
3. Aucune répartition de charge → **OpenFeign + Spring Cloud LoadBalancer** (round robin).

## 2. Architecture réalisée

| Programme | Port | Rôle |
|---|---|---|
| `eureka-service` | 8761 | Annuaire des services (Eureka Server) |
| `gateway-service` | 8888 | Passerelle, point d'entrée unique (Spring Cloud Gateway) |
| `calcul-service` (instance 1) | 8081 | Les quatre calculs |
| `calcul-service` (instance 2) | 8083 | Second exemplaire du même programme |
| `historique-service` | 8082 | Historique des opérations, appelle `calcul-service` via OpenFeign |

```
calculatrice-spring-cloud/
├── pom.xml                  <- pom parent (packaging pom, regroupe les modules)
├── eureka-service/          <- partie B
├── calcul-service/          <- partie C (copie du TP3 + client Eureka + Swagger + Lombok)
├── historique-service/      <- parties C et D (+ OpenFeign)
└── gateway-service/         <- partie E
```

**Ordre de lancement :** `eureka-service` → `calcul-service` → `historique-service` → `gateway-service`.

Chemin d'une requête `POST :8888/operations` :
client → passerelle (route + annuaire) → `historique-service` → OpenFeign + LoadBalancer → `calcul-service` (8081 ou 8083) → réponse qui remonte le même chemin.

---

## 3. Déroulement et captures

### Partie B : L'annuaire `eureka-service`

- Module généré avec la dépendance **Eureka Server**, annotation `@EnableEurekaServer` sur la classe principale.
- `application.properties` : port 8761, `register-with-eureka=false`, `fetch-registry=false`, éviction rapide et auto-préservation désactivée (réglages de TP).

### Partie C : Enregistrement des microservices

- Ajout de `spring-cloud-starter-netflix-eureka-client`, du BOM `spring-cloud-dependencies`, de `springdoc-openapi-starter-webmvc-ui` et de Lombok dans `calcul-service` et `historique-service`.
- Classes `Resultat` et `Operation` simplifiées avec Lombok (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString`).
- Deux instances de `calcul-service` lancées (8081 et 8083, la seconde avec `--server.port=8083`).

**Tableau de bord Eureka : trois services enregistrés** (`CALCUL-SERVICE`, `GATEWAY-SERVICE`, `HISTORIQUE-SERVICE`, tous `UP`). Le message rouge sur le mode d'auto-préservation est attendu (`enable-self-preservation=false`).

![Tableau de bord Eureka]
<img width="1917" height="931" alt="Capture d&#39;écran 2026-11-14 115251" src="https://github.com/user-attachments/assets/0023423b-43e8-4f38-98ad-8e620af2716a" />


**Swagger UI de `calcul-service`, instance 8083, testé directement :**
<img width="1865" height="1005" alt="Capture d&#39;écran 2026-11-15 150045" src="https://github.com/user-attachments/assets/d8a42759-4bc8-41ff-9f59-2b0b4cb3f140" />



**Swagger UI des deux microservices avec `server.forward-headers-strategy=framework` :**

![Swagger calcul-service]
<img width="1865" height="1005" alt="Capture d&#39;écran 2026-11-15 150045" src="https://github.com/user-attachments/assets/d7a419f6-55ec-41b9-86b0-cef92de668f7" />


![Swagger historique-service]
<img width="1831" height="972" alt="Capture d&#39;écran 2026-11-15 145602" src="https://github.com/user-attachments/assets/0ccfea72-e8c2-412f-bf67-bc1c0b13d867" />


### Partie D : OpenFeign

- `@EnableFeignClients` sur `HistoriqueServiceApplication`.
- `CalculClient` devient une interface `@FeignClient(name = "CALCUL-SERVICE")` : plus aucune adresse en dur (`calcul.service.url` supprimée).
- `OperationController` adapté : `FeignException.BadRequest` (400 du service appelé) puis `FeignException` (service injoignable → 503).
- Chaque instance de `calcul-service` affiche son port dans la console, ce qui permet d'observer l'alternance entre 8081 et 8083.

### Partie E : La passerelle `gateway-service`

- Dépendances : Gateway (WebFlux), Eureka Discovery Client, Actuator, `springdoc-openapi-starter-webflux-ui`.
- Trois façons de définir les routes : **YAML statique** (E.2), **`lb://` vers l'annuaire** (E.3), **Java `RouteLocator`** (E.4), puis **routes dynamiques** créées à partir de l'annuaire (E.5).

**Swagger UI de la passerelle.** Sur ces deux captures, la zone *Servers* indique l'adresse de l'instance (`192.168.115.1:8082` / `:8083`), c'est-à-dire l'adresse vue **sans** les en-têtes `X-Forwarded-*` (voir la réponse à la question 10 de synthèse) :

![Passerelle, définition historique-service]
<img width="1917" height="1001" alt="Capture d&#39;écran 2026-11-14 114650" src="https://github.com/user-attachments/assets/4a79d377-69b3-47db-a39b-0367da07e37b" />


![Passerelle, définition calcul-service]
<img width="1917" height="982" alt="Capture d&#39;écran 2026-11-14 114706" src="https://github.com/user-attachments/assets/13c1a8fb-1adc-4019-a3ba-e34d5364bc02" />

**Liste « Select a definition » de la passerelle (une entrée par microservice) :**

![Select a definition]
<img width="1907" height="1047" alt="Capture d&#39;écran 2026-11-15 154419" src="https://github.com/user-attachments/assets/e60807f8-4d0c-4d8c-9122-dac7f51e8344" />


**Configuration dynamique (E.5).** Les URI vues du client contiennent le nom du service en premier segment :

`GET /calcul-service/calcul/somme?a=2&b=3` → `5`

![Dynamique : somme]
<img width="1487" height="496" alt="Capture d&#39;écran 2026-11-14 115032" src="https://github.com/user-attachments/assets/598c2249-f13c-492f-bd04-b55ec4c299dd" />


`GET /calcul-service/calcul/produit?a=6&b=7` → `42`

![Dynamique : produit]

<img width="1423" height="577" alt="Capture d&#39;écran 2026-11-14 115202" src="https://github.com/user-attachments/assets/dca5ea0c-3c37-4903-a7e8-fd1e93b8e84e" />


`GET /historique-service/operations` → `[]` (historique vide, rien n'a encore été créé)

![Dynamique : historique]

<img width="1303" height="477" alt="Capture d&#39;écran 2026-11-14 115737" src="https://github.com/user-attachments/assets/bef50609-148c-4296-bfea-4e39e83e4efc" />


**Actuator de la passerelle** : `/actuator/gateway` liste les endpoints disponibles, `/actuator/gateway/routes` liste les routes créées dynamiquement (une par service de l'annuaire, avec son filtre `RewritePath`) :

![Actuator gateway]
<img width="1917" height="481" alt="Capture d&#39;écran 2026-11-14 120953" src="https://github.com/user-attachments/assets/6f23b0ac-2ffb-4960-92da-73e164401299" />


![Actuator gateway routes]
<img width="1917" height="467" alt="Capture d&#39;écran 2026-11-14 122927" src="https://github.com/user-attachments/assets/f11fbbbd-1c11-425f-9c83-62031b44f9d5" />


**Test depuis le Swagger UI de la passerelle** : `GET /calcul/somme` avec `a = 4` et `b = 6`. La *Request URL* passe bien par `localhost:8888/calcul-service/...` et la réponse est `10` (statut 200) :

![Swagger passerelle : paramètres]
<img width="1733" height="678" alt="Capture d&#39;écran 2026-11-14 122813" src="https://github.com/user-attachments/assets/90e66bc9-14d7-48d7-a842-ae41edbdf143" />


![Swagger passerelle : réponse]
<img width="1740" height="807" alt="Capture d&#39;écran 2026-11-14 122821" src="https://github.com/user-attachments/assets/33ee472e-25dd-4315-8adc-bcb57eb4db7f" />


**Les programmes en cours d'exécution (fenêtre Services d'IntelliJ)** : passerelle 8888, `calcul-service` 8083, Eureka 8761, historique 8082 :

![Services IntelliJ]
<img width="522" height="317" alt="Capture d&#39;écran 2026-11-15 152400" src="https://github.com/user-attachments/assets/a4c24985-a5d6-462e-8591-730eb2d6ba9c" />


---

## 4. Réponses aux questions

### Partie A

**A.4.1.** C'est **Eureka Server** (l'annuaire) qui fait la correspondance entre un nom de service et les adresses de ses instances. Elle est mise à jour à chaque inscription ou désinscription d'une instance, à chaque battement de cœur manqué (éviction), et les clients rafraîchissent leur copie locale à intervalle régulier (5 s dans ce TP).

**A.4.2.** *Avantage :* le client ne connaît qu'une seule adresse (8888) et la passerelle centralise des fonctions communes (sécurité, journalisation, routage). *Inconvénient :* c'est un point unique de défaillance et un goulot d'étranglement potentiel : si la passerelle tombe, tout est inaccessible.

**A.4.3.** Six requêtes en round robin : **3 requêtes par instance**. Le client ne peut pas s'en apercevoir : la réponse est identique, seule la console de chaque instance montre la répartition.

### Partie B

**B.1.** Chaque programme est une JVM distincte avec son propre Tomcat, qui écoute sur un **port différent** (8761, 8081, 8082...). Il n'y a pas de conflit tant que deux programmes n'utilisent pas le même port.

### Partie C

**C.1.** Le **nom** (`CALCUL-SERVICE`) est commun à toutes les instances : c'est ce que l'appelant utilise pour demander « une instance de ce service ». L'**identifiant** (`calcul-service:8081`, `calcul-service:8083`) distingue chaque instance précise dans l'annuaire (adresse, état, désinscription, choix par le répartiteur).

**C.2.** Sans `--server.port=8083`, la seconde instance utiliserait le port 8081 déjà occupé : le démarrage échouerait (`Port 8081 was already in use`).

### Partie D

**D.1.** Les traces alternent : 8081, 8083, 8081, 8083... La règle est de choisir les instances **à tour de rôle**. Cette stratégie s'appelle le **round robin**.

**D.2.** Avec un nom erroné (`CALCUL-SERVIC`), LoadBalancer ne trouve aucune instance et répond 503. On obtient un statut **503** `{"erreur":"calcul-service injoignable"}`, produit par le bloc `catch (FeignException e)` (le bloc `FeignException.BadRequest` ne correspond pas).

### Partie E

**E.1.** La passerelle ajoute à la requête transmise les en-têtes `X-Forwarded-Host` et `X-Forwarded-Port` (et `X-Forwarded-Prefix` en mode dynamique). Grâce à `server.forward-headers-strategy=framework`, Spring les utilise pour reconstruire l'adresse vue par le client : `ServletUriComponentsBuilder.fromCurrentRequest()` produit donc `http://localhost:8888/...` au lieu de `:8082`.

**E.2.** *E.3 (routes statiques `lb://`)* : URI courtes (`/calcul/somme`), indépendantes du nom du service, mais il faut **modifier la passerelle à chaque nouveau microservice**. *E.5 (routes dynamiques)* : aucune modification de la passerelle quand un service s'ajoute (la route est créée automatiquement), mais le **nom du service apparaît dans l'URI** (`/calcul-service/calcul/somme`).

**E.3.** La passerelle est elle-même enregistrée dans Eureka, et le locator crée une route pour **chaque** service de l'annuaire, donc aussi pour elle-même (`GATEWAY-SERVICE`, visible sur la capture 12). En TP ce n'est pas gênant. En production, cela peut exposer des endpoints non voulus (par exemple Actuator) : on filtre donc les services concernés.

### Questions de synthèse

**1. Où observe-t-on chaque différence du tableau A.3 ?**

| Aspect | Étape |
|---|---|
| Nombre de programmes | B (Eureka), E (passerelle), C.5 (2e instance) |
| Dépendances Maven | B.2, C.1, C.7, E.1 |
| Adresse d'un microservice | C.2, C.7, D.2 |
| Appel entre microservices | D.2 |
| Erreurs d'appel | D.3 |
| Point d'entrée du client | E.2 |
| Plusieurs instances | C.5, D.4, E.3 |
| Serveur embarqué | E.1 (Netty) et logs de démarrage |
| Arrêt d'un microservice | C.6 |
| Organisation du code | B.0, C.0 |
| Outil de test | C.4 (Swagger UI) |
| Classes de données | C.1 bis, C.7 (Lombok) |

**2. Trajet de `POST :8888/historique-service/operations` avec `{"operateur":"somme","a":1,"b":2}` :**

1. Le client (Swagger UI) envoie la requête à la **passerelle** (8888).
2. La passerelle trouve la route dynamique `Path=/historique-service/**`, consulte sa copie de l'annuaire (instances de `HISTORIQUE-SERVICE`), retire le préfixe (`RewritePath`) et transmet `POST /operations` à `historique-service` (8082) avec les en-têtes `X-Forwarded-*`.
3. `historique-service` appelle `calculClient.calculer("somme", 1, 2)` : OpenFeign construit `GET /calcul/somme?a=1&b=2`, LoadBalancer consulte la liste des instances de `CALCUL-SERVICE` et en choisit une (8081 ou 8083).
4. `calcul-service` répond `3` (texte brut).
5. `historique-service` enregistre l'opération (id, résultat 3.0) et répond `201` avec l'en-tête `Location`.
6. La réponse remonte : `calcul-service` → `historique-service` → passerelle → client.

**3. Répartiteurs de charge Spring Cloud LoadBalancer :** dans `historique-service` (résout `CALCUL-SERVICE`) et dans `gateway-service` (résout `CALCUL-SERVICE`, `HISTORIQUE-SERVICE`, et `GATEWAY-SERVICE` via les routes dynamiques). `calcul-service` embarque aussi la bibliothèque (incluse avec le client Eureka) mais n'appelle aucun autre service, donc ne résout aucun nom.

**4. Dépendances nécessaires par programme :**

| Dépendance | `calcul-service` | `historique-service` | `gateway-service` |
|---|---|---|---|
| Client Eureka | oui (s'inscrire dans l'annuaire) | oui (s'inscrire + trouver `CALCUL-SERVICE`) | oui (s'inscrire + connaître les instances à router) |
| OpenFeign | non (n'appelle personne) | **oui** (appelle `calcul-service`) | non (la passerelle route seule) |
| Spring Cloud Gateway | non | non | **oui** (c'est la passerelle) |

**5. Réponse complète (adresse en dur et répartition de charge).** Avec `calcul.service.url=http://localhost:8081`, `historique-service` n'appelle toujours que l'instance dont l'adresse est dans sa configuration : lancer d'autres exemplaires ne sert à rien, et changer de port ou de machine impose de modifier la configuration et de relancer. Dans ce TP : **Eureka Server** conserve les adresses de toutes les instances (par nom) ; le **client Eureka** inscrit chaque instance et fournit la liste à ses appelants ; **OpenFeign** appelle par nom (`@FeignClient(name = "CALCUL-SERVICE")`) ; **Spring Cloud LoadBalancer** choisit une instance à tour de rôle ; **Spring Cloud Gateway** (`lb://` ou routes dynamiques) fait de même pour les clients externes.

**6. Deux instances d'`historique-service` en mémoire.** Chaque instance garde sa propre liste : après trois POST répartis en round robin, `GET /historique-service/operations` ne renvoie qu'une **partie des opérations** (celles de l'instance interrogée), et le résultat change d'un appel à l'autre. Le principe mis en défaut est l'**absence d'état local** (*stateless*) : l'état doit être sorti du service. On le corrige en stockant les opérations dans une **base de données partagée** (ou un cache distribué) commune aux instances.

**7. Construire et lancer sans IntelliJ :**

```bash
# À la racine de calculatrice-spring-cloud : construit les quatre modules
mvn clean package -DskipTests

# Un terminal par programme, dans cet ordre
java -jar eureka-service/target/eureka-service-0.0.1-SNAPSHOT.jar
java -jar calcul-service/target/calcul-service-0.0.1-SNAPSHOT.jar
java -jar calcul-service/target/calcul-service-0.0.1-SNAPSHOT.jar --server.port=8083
java -jar historique-service/target/historique-service-0.0.1-SNAPSHOT.jar
java -jar gateway-service/target/gateway-service-0.0.1-SNAPSHOT.jar
```

**8. `@GetMapping("/calcul/{operateur}")`.** Dans `CalculController` (TP3), l'annotation **décrit la requête reçue** : elle déclare une route que le serveur expose. Dans `CalculClient` (TP4), elle **décrit la requête envoyée** : OpenFeign s'en sert pour fabriquer un appel HTTP vers le service distant.

**9. Les modules d'un même projet IntelliJ restent indépendants.** Chacun a sa **JVM** propre, son **port**, son propre **.jar** (son propre `pom.xml`), et un **cycle de vie** séparé (lancement, arrêt, redéploiement sans toucher aux autres). Le pom racine ne fait que regrouper le code pour faciliter le travail : il n'est même pas le parent Maven des modules.

**10. Origine de l'adresse dans la zone *Servers*.** Elle vient des en-têtes `X-Forwarded-Host`, `X-Forwarded-Port` et `X-Forwarded-Prefix` ajoutés par la passerelle, que le microservice utilise pour construire son document OpenAPI (`http://localhost:8888` à l'étape E.2, `http://localhost:8888/calcul-service` à l'étape E.5). **Sans `server.forward-headers-strategy=framework`**, le microservice ignore ces en-têtes et indique sa propre adresse (ex. `http://192.168.115.1:8083`, comme sur les captures 05 et 06) : chaque *Execute* part alors directement du navigateur vers l'instance, **en contournant la passerelle**.

**11. Où se trouvent les méthodes générées par Lombok.** Dans le **bytecode** (`Operation.class` dans `target/classes`), ajoutées à la compilation : le fichier `.java` ne contient que les attributs. Lombok peut être exclu du `.jar` exécutable parce qu'il ne sert **qu'à la compilation** : à l'exécution, les méthodes sont déjà dans les `.class`.

---

## 5. Lancer le projet

1. Ouvrir `calculatrice-spring-cloud` dans IntelliJ (ou construire avec `mvn clean package -DskipTests`).
2. Lancer dans l'ordre : Eureka (8761), `calcul-service` (8081), `calcul-service` (8083, `--server.port=8083`), `historique-service` (8082), `gateway-service` (8888).
3. Ouvrir :
   - Eureka : <http://localhost:8761>
   - Swagger UI de la passerelle : <http://localhost:8888/swagger-ui.html>
   - Routes de la passerelle : <http://localhost:8888/actuator/gateway/routes>

## 6. Conclusion

L'architecture Spring Cloud supprime les trois faiblesses du TP3 : les adresses ne sont plus écrites en dur (Eureka), le client n'utilise plus qu'un point d'entrée (Spring Cloud Gateway sur le port 8888) et les requêtes sont réparties entre plusieurs instances d'un même service (LoadBalancer, avec OpenFeign ou la passerelle). Swagger UI sert d'outil de test unique, et Lombok allège le code des classes de données.
