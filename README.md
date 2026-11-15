Compte rendu du TP4 : microservices avec Spring Cloud
Filière MPSRCC1 · Projet calculatrice-spring-cloud · Spring Boot 4.0.9, Spring Cloud 2025.1.x
1. Introduction et objectifs
Au TP3, la calculatrice a été découpée en deux microservices Spring Boot, calcul-service et historique-service, qui communiquent par HTTP. Trois faiblesses avaient été relevées : l'adresse de calcul-service est écrite en dur, le client doit connaître l'adresse de chaque microservice, et aucune répartition de charge n'est possible. Ce TP les corrige avec Spring Cloud.
Problème
Solution
Composant
Adresse écrite en dur
Découverte de services par nom
Eureka Server + Eureka Client
Le client connaît chaque adresse
Point d'entrée unique
Spring Cloud Gateway (port 8888)
Pas de répartition de charge
Appel par nom, à tour de rôle
OpenFeign + Spring Cloud LoadBalancer
S'y ajoutent Swagger UI (springdoc-openapi 3.0.3) pour documenter et tester les API, et Lombok pour supprimer le code répétitif des classes de données.
2. Architecture réalisée
Programme
Port
Rôle
eureka-service
8761
Annuaire des services (Eureka Server)
calcul-service
8083
Les quatre calculs (une seule instance dans ce rendu)
historique-service
8082
Historique des opérations, appelle calcul-service
gateway-service
8888
Point d'entrée unique des clients
Ordre de lancement : eureka-service, calcul-service, historique-service, gateway-service.
Le code est organisé en un seul projet IntelliJ, calculatrice-spring-cloud, dont le pom.xml racine (packaging pom) agrège quatre modules Maven. Chaque module reste une application indépendante : sa propre JVM, son port, son .jar et son cycle de vie.
Trajet d'une requête de création d'opération : le client (Swagger UI) envoie POST :8888/operations à la passerelle, qui trouve la route Path=/operations/** et transmet la requête à une instance d'historique-service. Celui-ci appelle calculClient.calculer(...) : OpenFeign construit GET /calcul/somme?a=8&b=2, LoadBalancer choisit une instance de CALCUL-SERVICE, et la réponse remonte le même chemin jusqu'au client.
3. Réalisation et résultats
3.1 Annuaire Eureka (partie B)
Le module eureka-service est généré avec la dépendance Eureka Server et l'annotation @EnableEurekaServer. Sa configuration (port 8761, register-with-eureka=false, fetch-registry=false) évite qu'il s'inscrive chez lui-même ; l'éviction rapide (5 s) et la désactivation de l'auto-préservation rendent les expériences observables.
Le tableau de bord http://localhost:8761 montre les trois services enregistrés et en état UP : CALCUL-SERVICE (instance calcul-service:8083), GATEWAY-SERVICE et HISTORIQUE-SERVICE.
Figure 1 : tableau de bord Eureka, trois services UP.
3.2 Enregistrement des microservices (partie C)
Dans calcul-service et historique-service, on a ajouté la dépendance spring-cloud-starter-netflix-eureka-client, le BOM spring-cloud-dependencies (propriété spring-cloud.version), springdoc-openapi et Lombok, avec la configuration du compilateur et l'exclusion de Lombok du .jar. Les propriétés spring.application.name, eureka.client.service-url.defaultZone et server.forward-headers-strategy=framework complètent application.properties. Aucune annotation Java n'est nécessaire : l'inscription est automatique.
Lombok remplace les constructeurs, getters et setters par @Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString sur les classes Resultat et Operation ; le JSON produit est inchangé. Une trace console [calcul-service:<port>] est ajoutée dans les quatre méthodes de CalculController pour identifier l'instance qui répond.
Dans ce rendu, calcul-service tourne en une seule instance (port 8083). Sa page Swagger UI (http://localhost:8083/swagger-ui.html) liste les opérations du contrôleur et permet de les tester.
Figure 2 : Swagger UI de calcul-service (port 8083).
3.3 OpenFeign dans historique-service (partie D)
@EnableFeignClients est ajoutée à la classe principale. La classe CalculClient du TP3 est remplacée par une interface annotée @FeignClient(name = «CALCUL-SERVICE») dont la méthode calculer(operateur, a, b) porte @GetMapping(«/calcul/{operateur}») : Spring génère l'implémentation et LoadBalancer choisit l'instance à chaque appel. Dans OperationController, HttpClientErrorException devient FeignException.BadRequest (400, message lu avec e.contentUTF8()) et ResourceAccessException devient FeignException (503). L'ordre des catch compte : la sous-classe d'abord.
La page Swagger UI d'historique-service (http://localhost:8082/swagger-ui.html) liste les cinq routes du contrôleur.
Figure 3 : Swagger UI d'historique-service (port 8082).
Requêtes de test D.4, avec les résultats attendus par l'énoncé (la capture de la zone Responses de chacune est à ajouter au rendu) :
N°
Requête
Corps
Statut attendu
1
POST /operations
{«operateur»:«somme»,«a»:8,«b»:2}
201, résultat 10.0, en-tête Location
2
POST /operations
{«operateur»:«produit»,«a»:6,«b»:7}
201, résultat 42.0
3
POST /operations
{«operateur»:«division»,«a»:7,«b»:2}
201, résultat 3.5
4
POST /operations
{«operateur»:«division»,«a»:7,«b»:0}
400, division par 0 impossible
5
POST /operations
{«operateur»:«racine»,«a»:9,«b»:0}
400, opérateur inconnu
6
GET /operations
aucun
200, tableau des opérations
7
GET /operations/2
aucun
200, opération produit
3.4 Passerelle gateway-service (partie E)
Le module utilise spring-cloud-starter-gateway-server-webflux (Netty, non bloquant), le client Eureka, Actuator et springdoc-openapi-starter-webflux-ui. Les routes ont été configurées de trois façons.
E.2, routes statiques en YAML. /calcul/** vers http://localhost:8083, /operations/** vers http://localhost:8082, plus deux routes /docs/... avec le filtre SetPath=/v3/api-docs qui alimentent Swagger UI. Avec des adresses fixes, la passerelle ne consulte pas l'annuaire.
E.3, routes lb://. Les adresses fixes deviennent lb://CALCUL-SERVICE et lb://HISTORIQUE-SERVICE : la passerelle demande à l'annuaire les instances et les sollicite à tour de rôle. Avec une seule instance de calcul-service, l'alternance n'est pas observable dans ce rendu.
E.4, routes statiques en Java. Un bean RouteLocator déclare les mêmes quatre routes.
E.5, routes dynamiques. spring.cloud.gateway.server.webflux.discovery.locator.enabled=true et lower-case-service-id=true créent une route /nom-du-service/** par service de l'annuaire, avec retrait du préfixe.
La page Swagger UI de la passerelle (http://localhost:8888/swagger-ui.html) propose la liste « Select a definition » avec une entrée par microservice.
Figure 4 : Swagger UI de la passerelle, définition calcul-service.
Figure 5 : Swagger UI de la passerelle, définition historique-service.
Sur ces deux captures, la zone Servers affiche encore l'adresse directe 192.168.115.1 : ce défaut est décrit et corrigé à la section 4.
Requêtes envoyées à travers la passerelle en routes dynamiques :
N°
Requête (préfixe http://localhost:8888)
Statut
Réponse
Constat
1
GET /calcul-service/calcul/somme?a=2&b=3
200
5
Observé (Figure 6)
2
GET /calcul-service/calcul/produit?a=6&b=7
200
42
Observé (Figure 7)
3
GET /historique-service/operations
200
[] (aucune opération créée)
Observé (Figure 8)
4
POST /historique-service/operations, somme de 1 et 1
201
résultat 2.0
Attendu, capture à ajouter
5
GET /calcul/somme?a=2&b=3
404
JSON d'erreur de la passerelle (plus de route /calcul/**)
Attendu, capture à ajouter
6
GET /gateway-service/actuator/gateway/routes
200
liste des routes
Attendu, capture à ajouter
L'énoncé écrit /calcul/produit/6/7 pour la requête 2, mais le contrôleur lit les opérandes en paramètres de requête : l'URL correcte est /calcul/produit?a=6&b=7.
Figure 6 : GET /calcul-service/calcul/somme?a=2&b=3 renvoie 5.
Figure 7 : GET /calcul-service/calcul/produit?a=6&b=7 renvoie 42.
Figure 8 : GET /historique-service/operations renvoie un tableau vide.
Depuis le Swagger UI de la passerelle, GET /calcul/somme a été exécuté avec a = 4 et b = 6.
Figure 9 : paramètres de la requête dans Swagger UI.
La Request URL est http://localhost:8888/calcul-service/calcul/somme?a=4&b=6 : la requête passe bien par la passerelle, le code est 200 et le corps vaut 10.
Figure 10 : réponse 200 reçue à travers la passerelle.
L'actuator de la passerelle expose les endpoints gateway (routes, filtres globaux, prédicats, rafraîchissement).
Figure 11 : endpoints de l'actuator gateway.
En configuration dynamique, /actuator/gateway/routes liste une route par service de l'annuaire (CALCUL-SERVICE, GATEWAY-SERVICE, HISTORIQUE-SERVICE), chacune avec son prédicat Path et son filtre RewritePath qui retire le préfixe. Les routes suivent l'annuaire : celle d'un service disparaît quand il s'arrête et réapparaît quand il redémarre.
Figure 12 : routes dynamiques de la passerelle.
4. Problèmes rencontrés et solutions
4.1 Endpoint /actuator/gateway/routes en 404
Avec Spring Cloud Gateway 5 (Spring Cloud 2025.1), l'endpoint gateway est désactivé par défaut : l'exposer avec management.endpoints.web.exposure.include=gateway ne suffit plus, ce que l'énoncé ne mentionne pas. Il faut aussi régler son accès avec management.endpoint.gateway.access: unrestricted. Après redémarrage de la passerelle, l'endpoint répond (Figures 11 et 12).
4.2 Swagger UI : adresse directe et erreur CORS
La zone Servers de Swagger UI affichait l'adresse directe de l'instance (http://192.168.115.1:8083) au lieu de la passerelle. Le navigateur bloquait alors le bouton Execute (message « Failed to fetch », causes possibles : CORS), car la requête partait vers un autre port que celui de la page (localhost:8888).
Figure 13 : Swagger UI directe de calcul-service avant correction (Servers = http://localhost:8083).
Figure 14 : Swagger UI de la passerelle avant correction (Servers = http://192.168.115.1:8083).
Causes : springdoc mémorise l'adresse du serveur calculée à la première requête reçue (accès direct ou via la passerelle), et les en-têtes X-Forwarded-* n'étaient pas pris en compte. Solution : ajouter springdoc.cache.disabled=true dans application.properties de calcul-service et d'historique-service, et fixer une adresse de serveur relative avec un bean OpenAPI (/calcul-service dans calcul-service, /historique-service dans historique-service). La zone Servers indique alors /calcul-service (Figure 2) et /historique-service (Figure 3), et Execute passe par la passerelle (Figure 10). Limite : avec une adresse relative, le bouton Execute des Swagger directs des microservices n'est plus utilisable ; les requêtes se testent via la passerelle.
4.3 Autres écarts avec l'énoncé
• http://localhost:8888/calcul-service sans chemin donne 404 : la passerelle retire le préfixe, et calcul-service reçoit GET /, qu'il ne connaît pas. Il faut toujours un chemin complet après le nom du service.
• Juste après un redémarrage, une route dynamique peut manquer quelques secondes : la copie locale de l'annuaire est rafraîchie toutes les 5 s.
• Dans le YAML de la passerelle, le bloc loadbalancer doit être sous spring.cloud, au même niveau que gateway.
5. Réponses aux questions
Partie A.4 : questions de conception
1.  Quel composant fait la correspondance entre le nom et les adresses ? Eureka Server. La correspondance est mise à jour à chaque inscription ou désinscription d'une instance, à chaque battement de cœur manquant (éviction), et les clients rafraîchissent leur copie locale toutes les 5 s.
2.  Point d'entrée unique. Avantage : le client ne connaît qu'une adresse, quel que soit le nombre de microservices. Inconvénient : la passerelle est un point unique de défaillance et ajoute un saut réseau.
3.  Six requêtes sur deux instances. Chaque instance en reçoit trois (tourniquet). Le client ne peut pas s'en apercevoir : les réponses sont identiques, seules les traces des consoles diffèrent.
Parties B, C, D et E
• B.1. Chaque programme est une JVM distincte avec son propre port (8761, 8083, 8082, 8888) : il n'y a conflit que si deux programmes veulent le même port.
• C.1. Le nom (CALCUL-SERVICE) identifie le service logique, commun à toutes les instances : c'est lui que l'appelant utilise. L'identifiant (par exemple calcul-service:8083) distingue chaque instance, pour l'inscrire, la renouveler, la retirer et la choisir séparément.
• C.2. Sans option de port, la seconde instance utiliserait le port déjà occupé : Tomcat ne démarrerait pas (Port already in use).
• D.1. Avec plusieurs instances, les traces alternent : la règle est de prendre l'instance suivante à tour de rôle, stratégie appelée round robin (tourniquet). Dans ce rendu, avec une seule instance, toutes les traces s'affichent sur 8083.
• D.2. Avec le nom CALCUL-SERVIC, LoadBalancer ne trouve aucune instance : la réponse est un 503 {«erreur»:«calcul-service injoignable»}, produite par le bloc catch (FeignException e).
• E.1. La passerelle ajoute les en-têtes X-Forwarded-Host, X-Forwarded-Port (et X-Forwarded-Prefix). Avec server.forward-headers-strategy=framework, historique-service les lit et ServletUriComponentsBuilder.fromCurrentRequest() construit l'en-tête Location avec le port 8888 et non 8082.
• E.2. Routes lb:// (E.3) : URI courtes, identiques à celles du TP3, mais la passerelle doit être modifiée à chaque nouveau microservice. Routes dynamiques (E.5) : aucune modification à l'ajout d'un service, mais le nom du service apparaît dans l'URI.
• E.3. La passerelle est elle-même inscrite dans Eureka : le locator crée donc une route pour chaque service, y compris GATEWAY-SERVICE (visible Figure 12). Ce n'est pas bloquant pour le TP, mais en production cela expose des points d'administration : on filtre ou on sécurise ces routes.
6. Questions de synthèse
1.  Étapes d'observation des différences du tableau A.3. Nombre de programmes : B, C, E. Dépendances Maven : B.2, C.1, C.7, E.1. Adresse d'un microservice : C.2, C.7, D.2. Appel entre microservices : D.2, D.3. Erreurs d'appel : D.3, D.4. Point d'entrée du client : E.2. Plusieurs instances : C.5, C.6, D.4, E.3. Serveur embarqué : E.1 (Netty). Arrêt d'un microservice : C.6, E.5. Organisation du code : B.0, C.0. Outil de test : C.4, D.4, E.2. Classes de données : C.1 bis, C.7.
2.  Trajet de POST :8888/historique-service/operations (somme de 1 et 2). Le client envoie la requête à la passerelle ; la route dynamique /historique-service/** consulte la copie locale de l'annuaire, choisit une instance d'HISTORIQUE-SERVICE, retire le préfixe, ajoute les en-têtes X-Forwarded-* et transmet POST /operations. Historique-service appelle calculClient.calculer(«somme»,1,2) : OpenFeign et LoadBalancer choisissent une instance de CALCUL-SERVICE et envoient GET /calcul/somme?a=1&b=2. Calcul-service répond 3 ; historique-service enregistre l'opération et répond 201 avec l'en-tête Location ; la réponse remonte vers la passerelle puis le client.
3.  Les trois répartiteurs de charge. gateway-service (résout CALCUL-SERVICE et HISTORIQUE-SERVICE, et GATEWAY-SERVICE en dynamique), historique-service (résout CALCUL-SERVICE via Feign) et calcul-service (présent avec le client Eureka mais inutilisé : il n'appelle personne).
4.  Dépendances nécessaires. Le client Eureka est nécessaire dans les trois programmes (inscription, découverte). OpenFeign seulement dans historique-service, le seul à appeler un autre microservice. Spring Cloud Gateway seulement dans gateway-service.
5.  Adresse en dur et répartition de charge. Une adresse fixe désigne une seule instance : lancer d'autres exemplaires ne sert à rien et tout changement d'adresse impose de modifier la configuration et de relancer. Ici, Eureka tient la liste des instances vivantes, LoadBalancer les sollicite à tour de rôle, OpenFeign et Gateway appellent le service par son nom.
6.  Deux instances d'historique-service. Les opérations étant en mémoire, chaque instance a sa propre liste : le GET renvoie un résultat différent selon l'instance interrogée. Le principe mis en défaut est celui du service sans état (stateless). Correction : une base de données partagée (MySQL ou PostgreSQL via JPA).
7.  Lancement sans IntelliJ. Un mvn clean package -DskipTests à la racine construit les quatre modules, puis un java -jar <module>/target/<module>-0.0.1-SNAPSHOT.jar par programme, dans l'ordre eureka, calcul, historique, gateway.
8.  Rôle de @GetMapping(«/calcul/{operateur}»). Dans CalculController, elle déclare une requête reçue (côté serveur). Dans CalculClient, elle décrit une requête envoyée (côté client) : OpenFeign génère l'implémentation qui l'émet.
9.  Indépendance des modules. Chaque module a sa propre JVM, son port, son .jar et son cycle de vie ; le pom racine ne regroupe que la construction.
10.  Origine de la zone Servers. Elle vient des en-têtes X-Forwarded-Host/Port/Prefix ajoutés par la passerelle, lus par le microservice puis publiés par springdoc. Sans server.forward-headers-strategy=framework, Servers contient l'adresse de l'instance (Figure 14) : Execute contourne la passerelle et le navigateur bloque la requête (CORS).
11.  Code généré par Lombok. Il se trouve dans le bytecode (Operation.class), pas dans le code source. Lombok n'étant utile qu'à la compilation, il peut être exclu du .jar exécutable.
7. Conclusion
L'application est passée d'adresses figées à une architecture dynamique : Eureka supprime les adresses en dur, OpenFeign et LoadBalancer appellent les services par leur nom, la passerelle offre un point d'entrée unique avec des routes statiques ou créées à partir de l'annuaire, et Swagger UI documente et teste chaque service puis l'ensemble. Limites : l'annuaire et la passerelle sont des points de défaillance uniques, l'état en mémoire d'historique-service empêche de le dupliquer sans incohérence, et le routage dynamique expose tous les services, y compris la passerelle.
Éléments à joindre pour compléter le rendu : captures des tests D.4 et des requêtes 4 à 6 de E.5, répartition de charge avec une seconde instance de calcul-service, et captures des consoles des programmes.
