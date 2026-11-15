<h1>TP4 – Microservices avec Spring Cloud : Eureka, OpenFeign, Gateway, Swagger UI et Lombok</h1>
<p><strong>Filière :</strong> MPSRCC1
<strong>Enseignantes :</strong> Corinne Amel ZAYANI, Amal ABID</p>
<hr />
<h2>Présentation</h2>
<p>Dans le TP3, la calculatrice a été découpée en deux microservices Spring Boot, <code>calcul-service</code> (port 8081) et <code>historique-service</code> (port 8082), qui communiquent par HTTP. Cette architecture fonctionne, mais présente trois faiblesses :</p>
<ol>
<li>L'adresse de <code>calcul-service</code> est écrite en dur dans <code>application.properties</code> d'<code>historique-service</code> (<code>calcul.service.url=http://localhost:8081</code>). Si <code>calcul-service</code> change de port ou de machine, il faut modifier la configuration et relancer <code>historique-service</code>.</li>
<li>Le client doit connaître l'adresse et le port de chaque microservice (8081 pour les calculs, 8082 pour l'historique). Avec dix microservices, il devrait connaître dix adresses.</li>
<li>Il est impossible de répartir la charge : si l'on lance trois exemplaires de <code>calcul-service</code>, <code>historique-service</code> n'en appelle toujours qu'un seul.</li>
</ol>
<p>Dans ce TP, ces trois problèmes sont résolus avec Spring Cloud : un annuaire de services (<strong>Eureka Server</strong>), une passerelle unique pour les clients (<strong>Spring Cloud Gateway</strong>) et un client REST déclaratif qui trouve lui-même les instances disponibles (<strong>OpenFeign</strong> avec <strong>Spring Cloud LoadBalancer</strong>).</p>
<h2>Objectifs</h2>
<ul>
<li>Expliquer le rôle d'un annuaire de services (<em>service registry</em>) et le principe de la découverte de services (<em>service discovery</em>).</li>
<li>Créer un serveur Eureka et y enregistrer des microservices Spring Boot.</li>
<li>Lancer plusieurs instances d'un même microservice et les observer dans l'annuaire.</li>
<li>Remplacer une adresse écrite en dur par un appel déclaratif avec OpenFeign, réparti entre les instances par Spring Cloud LoadBalancer.</li>
<li>Créer une passerelle (API Gateway) avec Spring Cloud Gateway, en configuration statique (YAML et Java) puis dynamique (routes créées à partir de l'annuaire).</li>
<li>Mettre en évidence la répartition de charge entre deux instances d'un même microservice (étapes D.4 et E.3).</li>
<li>Organiser une application de microservices en modules d'un même projet IntelliJ, regroupés par un <code>pom.xml</code> parent.</li>
<li>Documenter et tester chaque microservice, puis toute l'application à travers la passerelle, avec Swagger UI.</li>
<li>Générer les constructeurs, getters et setters des classes de données avec Lombok.</li>
</ul>
<h2>Prérequis</h2>
<ul>
<li>JDK 21+, Maven 3.9+ (intégré à IntelliJ). Postman et curl ne sont plus utilisés : toutes les requêtes sont envoyées depuis Swagger UI.</li>
<li>Les deux projets du TP3, <code>calcul-service</code> et <code>historique-service</code>, complets et fonctionnels (sauvegardés avant de commencer).</li>
</ul>
<p><strong>Versions.</strong> Spring Boot 4.0.9 (imposée au TP3) et Spring Cloud 2025.1.x (nom de code Oakwood). Spring Initializr choisit automatiquement la version de Spring Cloud compatible. Swagger UI est fourni par springdoc-openapi 3.0.x (ligne de Spring Boot 4). La version de Lombok est fixée par Spring Boot.</p>
<p><strong>Ports.</strong></p>
<table>
<thead>
<tr>
<th>Programme</th>
<th>Port</th>
<th>Rôle</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>eureka-service</code></td>
<td>8761</td>
<td>Annuaire des services (Eureka Server)</td>
</tr>
<tr>
<td><code>gateway-service</code></td>
<td>8888</td>
<td>Passerelle : point d'entrée unique des clients</td>
</tr>
<tr>
<td><code>calcul-service</code></td>
<td>8083</td>
<td>Effectue les quatre calculs (projet du TP3) ; une seule instance dans ce rendu</td>
</tr>
<tr>
<td><code>historique-service</code></td>
<td>8082</td>
<td>Enregistre les opérations et appelle <code>calcul-service</code></td>
</tr>
</tbody>
</table>
<p><strong>Ordre de lancement :</strong> <code>eureka-service</code> → <code>calcul-service</code> → <code>historique-service</code> → <code>gateway-service</code>. Chaque programme tourne dans sa propre console.</p>
<p><strong>Swagger UI.</strong> Chaque microservice (sauf l'annuaire) affiche sa page de test à <code>http://localhost:&lt;port&gt;/swagger-ui.html</code> : 8083 pour <code>calcul-service</code>, 8082 pour <code>historique-service</code>, et 8888 pour la passerelle, dont la page regroupe les API des deux microservices (partie E).</p>
<hr />
<h2>Partie A : Du TP3 à Spring Cloud</h2>
<h3>A.1 Le vocabulaire de Spring Cloud</h3>
<table>
<thead>
<tr>
<th>Composant</th>
<th>Définition</th>
<th>Utilité dans ce TP</th>
</tr>
</thead>
<tbody>
<tr>
<td><strong>Eureka Server</strong> (annuaire)</td>
<td>Application Spring Boot qui tient à jour la liste des microservices en marche : pour chaque nom de service, les adresses (hôte et port) de toutes ses instances.</td>
<td><code>eureka-service</code> : chaque microservice s'y inscrit au démarrage et y signale régulièrement qu'il est vivant (<em>heartbeat</em>).</td>
</tr>
<tr>
<td><strong>Eureka Client</strong> (<em>service discovery</em>)</td>
<td>Dépendance qui enregistre le microservice sous le nom <code>spring.application.name</code> et lui permet d'obtenir la liste des instances des autres services.</td>
<td>Ajoutée dans <code>calcul-service</code>, <code>historique-service</code> et <code>gateway-service</code>.</td>
</tr>
<tr>
<td><strong>Spring Cloud LoadBalancer</strong></td>
<td>À partir d'un nom de service, choisit une instance parmi celles de l'annuaire, à tour de rôle (<em>round robin</em>). Inclus avec le client Eureka.</td>
<td>Permet d'écrire <code>lb://CALCUL-SERVICE</code> au lieu de <code>http://localhost:8081</code> (<code>lb</code> = <em>load balanced</em>).</td>
</tr>
<tr>
<td><strong>OpenFeign</strong> (client REST déclaratif)</td>
<td>Crée automatiquement l'implémentation d'une interface Java annotée (<code>@GetMapping</code>, <code>@RequestParam</code>…) : chaque méthode devient une requête HTTP.</td>
<td>Remplace, dans <code>historique-service</code>, la classe <code>CalculClient</code> du TP3 fondée sur <code>RestClient</code>.</td>
</tr>
<tr>
<td><strong>Spring Cloud Gateway</strong> (passerelle)</td>
<td>Application qui reçoit toutes les requêtes des clients et les transmet au bon microservice selon des routes (prédicat + URI + filtres). Fondée sur WebFlux (Netty, non bloquant).</td>
<td><code>gateway-service</code> : le client n'utilise plus que le port 8888.</td>
</tr>
<tr>
<td><strong>springdoc-openapi</strong> (Swagger UI)</td>
<td>Lit les contrôleurs Spring et produit la description OpenAPI (<code>/v3/api-docs</code>) et une page web pour l'essayer (<code>/swagger-ui.html</code>).</td>
<td>Remplace curl et Postman.</td>
</tr>
<tr>
<td><strong>Lombok</strong></td>
<td>Génère à la compilation le code répétitif d'une classe à partir d'annotations (<code>@Getter</code>, <code>@Setter</code>, <code>@NoArgsConstructor</code>, <code>@AllArgsConstructor</code>, <code>@ToString</code>, <code>@Data</code>).</td>
<td>Supprime constructeurs, getters et setters écrits à la main.</td>
</tr>
</tbody>
</table>
<h3>A.2 L'architecture visée</h3>
<p>Le client est désormais Swagger UI, ouvert dans le navigateur. Cheminement d'une requête de création d'opération :</p>
<ol>
<li>Le client envoie <code>POST http://localhost:8888/operations</code> à la passerelle.</li>
<li>La passerelle trouve la route dont le prédicat <code>Path=/operations/**</code> correspond, demande à l'annuaire les instances de <code>HISTORIQUE-SERVICE</code> et transmet la requête à l'une d'elles.</li>
<li><code>historique-service</code> appelle <code>calculClient.calculer(...)</code> : OpenFeign construit la requête <code>GET /calcul/somme?a=8&amp;b=2</code>, LoadBalancer choisit une instance de <code>CALCUL-SERVICE</code> (ici l'instance 8083 ; à tour de rôle s'il y en avait plusieurs) et la requête est envoyée.</li>
<li>La réponse remonte : <code>calcul-service</code> → <code>historique-service</code> → passerelle → client.</li>
</ol>
<table>
<thead>
<tr>
<th>Verbe</th>
<th>URI vue du client (via la passerelle)</th>
<th>Microservice</th>
<th>Rôle</th>
</tr>
</thead>
<tbody>
<tr>
<td>GET</td>
<td><code>:8888/calcul/somme?a=2&amp;b=3</code></td>
<td>calcul-service</td>
<td>Retourne a + b</td>
</tr>
<tr>
<td>GET</td>
<td><code>:8888/calcul/soustraction?a=2&amp;b=3</code></td>
<td>calcul-service</td>
<td>Retourne a – b</td>
</tr>
<tr>
<td>GET</td>
<td><code>:8888/calcul/produit?a=2&amp;b=3</code></td>
<td>calcul-service</td>
<td>Retourne a × b</td>
</tr>
<tr>
<td>GET</td>
<td><code>:8888/calcul/division?a=6&amp;b=3</code></td>
<td>calcul-service</td>
<td>Retourne a ÷ b</td>
</tr>
<tr>
<td>POST</td>
<td><code>:8888/operations</code></td>
<td>historique-service</td>
<td>Crée une opération</td>
</tr>
<tr>
<td>GET</td>
<td><code>:8888/operations</code></td>
<td>historique-service</td>
<td>Retourne toutes les opérations</td>
</tr>
<tr>
<td>GET</td>
<td><code>:8888/operations/{id}</code></td>
<td>historique-service</td>
<td>Retourne l'opération numéro id</td>
</tr>
<tr>
<td>PUT</td>
<td><code>:8888/operations/{id}</code></td>
<td>historique-service</td>
<td>Modifie l'opération numéro id</td>
</tr>
<tr>
<td>DELETE</td>
<td><code>:8888/operations/{id}</code></td>
<td>historique-service</td>
<td>Supprime l'opération numéro id</td>
</tr>
</tbody>
</table>
<h3>A.3 Microservices du TP3 et architecture Spring Cloud : ce qui change</h3>
<table>
<thead>
<tr>
<th>Aspect</th>
<th>TP3</th>
<th>TP4 (Spring Cloud)</th>
</tr>
</thead>
<tbody>
<tr>
<td>Nombre de programmes</td>
<td>Deux microservices</td>
<td>Deux microservices + un annuaire + une passerelle (+ une seconde instance de <code>calcul-service</code>)</td>
</tr>
<tr>
<td>Dépendances Maven</td>
<td><code>spring-boot-starter-webmvc</code></td>
<td>+ <code>eureka-client</code>, <code>openfeign</code>, <code>gateway-server-webflux</code>, <code>eureka-server</code> (versions fixées par le BOM <code>spring-cloud-dependencies</code>) ; + springdoc et lombok</td>
</tr>
<tr>
<td>Adresse d'un microservice</td>
<td>Écrite en dur : <code>calcul.service.url=http://localhost:8081</code></td>
<td>Obtenue de l'annuaire à partir du nom : <code>CALCUL-SERVICE</code></td>
</tr>
<tr>
<td>Appel entre microservices</td>
<td>Classe <code>CalculClient</code> avec <code>RestClient.create(url)</code></td>
<td>Interface <code>CalculClient</code> annotée <code>@FeignClient(name = "CALCUL-SERVICE")</code></td>
</tr>
<tr>
<td>Erreurs d'appel</td>
<td><code>HttpClientErrorException</code>, <code>ResourceAccessException</code></td>
<td><code>FeignException.BadRequest</code>, <code>FeignException</code></td>
</tr>
<tr>
<td>Point d'entrée du client</td>
<td>Un port par microservice (8081, 8082)</td>
<td>Un seul port : la passerelle (8888)</td>
</tr>
<tr>
<td>Plusieurs instances d'un service</td>
<td>Possible, mais inutilisées</td>
<td>Enregistrées dans Eureka, sollicitées à tour de rôle</td>
</tr>
<tr>
<td>Serveur embarqué</td>
<td>Tomcat partout</td>
<td>Tomcat pour les microservices, Netty pour la passerelle</td>
</tr>
<tr>
<td>Arrêt d'un microservice</td>
<td>Le client qui en dépend échoue (503)</td>
<td>Les autres instances prennent le relais ; 503 seulement s'il n'en reste aucune</td>
</tr>
<tr>
<td>Organisation du code</td>
<td>Deux projets IntelliJ séparés</td>
<td>Un seul projet (<code>calculatrice-spring-cloud</code>), un module Maven par microservice</td>
</tr>
<tr>
<td>Outil de test</td>
<td>curl, Postman</td>
<td>Swagger UI sur chaque microservice, puis sur la passerelle</td>
</tr>
<tr>
<td>Classes de données</td>
<td>Constructeurs, getters et setters écrits à la main</td>
<td>Générés à la compilation par Lombok</td>
</tr>
</tbody>
</table>
<h3>A.4 Questions de conception</h3>
<p><strong>1. Quel composant fait la correspondance entre le nom et les adresses, et à quel moment est-elle mise à jour ?</strong>
C'est <strong>Eureka Server</strong> (l'annuaire). La correspondance est mise à jour à chaque inscription ou désinscription d'une instance, quand un battement de cœur manque (éviction), et les clients rafraîchissent leur copie locale toutes les 5 s.</p>
<p><strong>2. Un avantage et un inconvénient du point d'entrée unique ?</strong>
<em>Avantage :</em> le client ne connaît qu'une seule adresse (8888), quel que soit le nombre de microservices. <em>Inconvénient :</em> la passerelle est un point unique de défaillance et ajoute un saut réseau.</p>
<p><strong>3. Six requêtes <code>GET :8888/calcul/somme?a=1&amp;b=1</code> avec deux instances : combien chaque instance en reçoit-elle ? Le client peut-il s'en apercevoir ?</strong>
Avec un tourniquet, chaque instance reçoit <strong>3 requêtes</strong>. Le client ne peut pas s'en apercevoir : les réponses sont identiques ; seules les traces des consoles le montrent.</p>
<hr />
<h2>Partie B : L'annuaire : <code>eureka-service</code></h2>
<h3>B.0 Créer le projet parent <code>calculatrice-spring-cloud</code></h3>
<p>Tous les microservices sont rangés dans un seul dossier, ouvert comme un seul projet IntelliJ. Chaque microservice y est un module (sous-dossier avec son propre <code>pom.xml</code>, sa propre classe principale et sa propre configuration), lancé dans sa propre JVM, sur son propre port.</p>
<pre><code>calculatrice-spring-cloud/       &lt;- projet IntelliJ (dossier racine)
|-- pom.xml                      &lt;- pom parent : regroupe les modules
|-- eureka-service/              &lt;- module, partie B (nouveau)
|-- calcul-service/              &lt;- module, partie C (copie du TP3)
|-- historique-service/          &lt;- module, partie C (copie du TP3)
`-- gateway-service/             &lt;- module, partie E (nouveau)
</code></pre>
<ol>
<li>IntelliJ : <em>File &gt; New &gt; Project... &gt; Empty Project</em>, nom <code>calculatrice-spring-cloud</code>.</li>
<li>Créer à la racine le fichier <code>pom.xml</code> :</li>
</ol>
<pre><code class="language-xml">&lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot;?&gt;
&lt;project xmlns=&quot;http://maven.apache.org/POM/4.0.0&quot;
         xmlns:xsi=&quot;http://www.w3.org/2001/XMLSchema-instance&quot;
         xsi:schemaLocation=&quot;http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd&quot;&gt;
    &lt;modelVersion&gt;4.0.0&lt;/modelVersion&gt;
    &lt;groupId&gt;com.exemple&lt;/groupId&gt;
    &lt;artifactId&gt;calculatrice-spring-cloud&lt;/artifactId&gt;
    &lt;version&gt;0.0.1-SNAPSHOT&lt;/version&gt;
    &lt;!-- pom : ce projet ne contient pas de code, il regroupe les modules --&gt;
    &lt;packaging&gt;pom&lt;/packaging&gt;
    &lt;modules&gt;
        &lt;module&gt;eureka-service&lt;/module&gt;      &lt;!-- étape B.1 --&gt;
        &lt;module&gt;calcul-service&lt;/module&gt;      &lt;!-- étape C.0 --&gt;
        &lt;module&gt;historique-service&lt;/module&gt;  &lt;!-- étape C.0 --&gt;
        &lt;module&gt;gateway-service&lt;/module&gt;     &lt;!-- étape E.1 --&gt;
    &lt;/modules&gt;
&lt;/project&gt;
</code></pre>
<ol>
<li>Clic droit sur ce <code>pom.xml</code> &gt; <em>Add as Maven Project</em>.</li>
</ol>
<p><strong>Points à comprendre :</strong> <code>&lt;packaging&gt;pom&lt;/packaging&gt;</code> : le parent ne produit pas de <code>.jar</code>, il agrège les modules. Chaque module garde le <code>pom.xml</code> généré par Spring Initializr, avec <code>spring-boot-starter-parent</code> comme parent : le pom racine regroupe sans être leur parent Maven.</p>
<h3>B.1 Générer le module <code>eureka-service</code></h3>
<p><em>New &gt; Module... &gt; Spring Boot</em> avec : Maven, Java, Spring Boot <strong>4.0.9</strong>, Group <code>com.exemple</code>, Artifact/Name <code>eureka-service</code>, Package <code>com.exemple.eureka</code>, Jar. Dépendance unique : <strong>Eureka Server</strong>. Puis décommenter <code>&lt;module&gt;eureka-service&lt;/module&gt;</code> dans le pom racine et recharger Maven.</p>
<h3>B.2 Lire le <code>pom.xml</code></h3>
<pre><code class="language-xml">&lt;properties&gt;
    &lt;java.version&gt;17&lt;/java.version&gt;
    &lt;spring-cloud.version&gt;2025.1.2&lt;/spring-cloud.version&gt;
&lt;/properties&gt;

&lt;dependencies&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.springframework.cloud&lt;/groupId&gt;
        &lt;artifactId&gt;spring-cloud-starter-netflix-eureka-server&lt;/artifactId&gt;
    &lt;/dependency&gt;
&lt;/dependencies&gt;

&lt;dependencyManagement&gt;
    &lt;dependencies&gt;
        &lt;dependency&gt;
            &lt;groupId&gt;org.springframework.cloud&lt;/groupId&gt;
            &lt;artifactId&gt;spring-cloud-dependencies&lt;/artifactId&gt;
            &lt;version&gt;${spring-cloud.version}&lt;/version&gt;
            &lt;type&gt;pom&lt;/type&gt;
            &lt;scope&gt;import&lt;/scope&gt;
        &lt;/dependency&gt;
    &lt;/dependencies&gt;
&lt;/dependencyManagement&gt;
</code></pre>
<p><code>spring-boot-starter-parent</code> fixe les versions Spring Boot ; le bloc <code>&lt;dependencyManagement&gt;</code> importe le BOM <code>spring-cloud-dependencies</code> qui joue le même rôle pour Spring Cloud. C'est pourquoi le starter n'a pas de balise <code>&lt;version&gt;</code>.</p>
<h3>B.3 La classe principale</h3>
<pre><code class="language-java">package com.exemple.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServiceApplication.class, args);
    }
}
</code></pre>
<h3>B.4 Configurer le serveur : <code>application.properties</code></h3>
<code class="language-properties">spring.application.name=eureka-service
server.port=8761

<h1>Le serveur EST l'annuaire : il ne s'enregistre pas lui-meme</h1>
eureka.client.register-with-eureka=false
<h1>... et n'a pas besoin de telecharger la liste des services</h1>
eureka.client.fetch-registry=false

<h1> Reglages pour le TP : retirer rapidement les instances arretees.</h1>
eureka.server.eviction-interval-timer-in-ms=5000
eureka.server.enable-self-preservation=false
</code></pre>
<p>Sans <code>register-with-eureka=false</code> et <code>fetch-registry=false</code>, le serveur tenterait de s'inscrire chez lui-même. Les deux derniers réglages rendent les expériences observables en quelques secondes ; en production on garde les valeurs par défaut.</p>
<h3>B.5 Lancer l'annuaire et ouvrir son tableau de bord</h3>
<p>Lancer <code>EurekaServiceApplication</code> puis ouvrir <code>http://localhost:8761</code>. Au départ : <em>No instances available</em>, et le message rouge <em>THE SELF PRESERVATION MODE IS TURNED OFF...</em> (conséquence attendue de <code>enable-self-preservation=false</code>).</p>
<p>Tableau de bord : <code>CALCUL-SERVICE</code>, <code>GATEWAY-SERVICE</code> et <code>HISTORIQUE-SERVICE</code> sont <code>UP</code>. <strong>Sur cette capture, <code>CALCUL-SERVICE</code> n'a qu'une seule instance (<code>calcul-service:8083</code>, <code>UP (1)</code>)</strong>.</p>
<p><img alt="Tableau de bord Eureka" 
            <img width="1917" height="931" alt="Capture d&#39;écran 2026-11-14 115251" src="https://github.com/user-attachments/assets/4de1c022-4fdf-4a09-afd8-2d33991d68dd" />

<p><strong>Question B.1. Pourquoi l'annuaire peut-il tourner sur le même poste que <code>calcul-service</code> et <code>historique-service</code> sans conflit ?</strong>
Chaque programme écoute sur <strong>son propre port</strong> : ici l'annuaire sur 8761, <code>calcul-service</code> (une seule instance, sur 8083), <code>historique-service</code> sur 8082 et la passerelle sur 8888. Deux serveurs n'entrent en conflit que s'ils veulent le même port.</p>
<hr />
<h2>Partie C : Enregistrer les microservices du TP3 dans l'annuaire</h2>
<h3>C.0 Intégrer les projets du TP3 comme modules</h3>
<ol>
<li>Copier les dossiers <code>calcul-service</code> et <code>historique-service</code> du TP3 dans <code>calculatrice-spring-cloud</code>, à côté de <code>eureka-service</code>.</li>
<li>Supprimer dans chaque copie <code>.idea</code>, les <code>*.iml</code> et <code>target</code>.</li>
<li>Décommenter les lignes <code>&lt;module&gt;</code> correspondantes dans le pom racine et recharger Maven.</li>
</ol>
<h3>C.1 Ajouter le client Eureka dans <code>calcul-service</code></h3>
<p>Cinq ajouts dans le <code>pom.xml</code> de <code>calcul-service</code> :</p>
<pre><code class="language-xml">&lt;properties&gt;
    &lt;java.version&gt;17&lt;/java.version&gt;
    &lt;spring-cloud.version&gt;2025.1.2&lt;/spring-cloud.version&gt;
&lt;/properties&gt;

&lt;dependencies&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.springframework.boot&lt;/groupId&gt;
        &lt;artifactId&gt;spring-boot-starter-webmvc&lt;/artifactId&gt;
    &lt;/dependency&gt;
    &lt;!-- Client Eureka : inscription dans l'annuaire + LoadBalancer --&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.springframework.cloud&lt;/groupId&gt;
        &lt;artifactId&gt;spring-cloud-starter-netflix-eureka-client&lt;/artifactId&gt;
    &lt;/dependency&gt;
    &lt;!-- Swagger UI : documentation et test de l'API (springdoc 3.x) --&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.springdoc&lt;/groupId&gt;
        &lt;artifactId&gt;springdoc-openapi-starter-webmvc-ui&lt;/artifactId&gt;
        &lt;version&gt;3.0.3&lt;/version&gt;
    &lt;/dependency&gt;
    &lt;!-- Lombok : constructeurs, getters et setters generes a la compilation --&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.projectlombok&lt;/groupId&gt;
        &lt;artifactId&gt;lombok&lt;/artifactId&gt;
        &lt;optional&gt;true&lt;/optional&gt;
    &lt;/dependency&gt;
    &lt;!-- ... dependances de test inchangees ... --&gt;
&lt;/dependencies&gt;

&lt;dependencyManagement&gt;
    &lt;dependencies&gt;
        &lt;dependency&gt;
            &lt;groupId&gt;org.springframework.cloud&lt;/groupId&gt;
            &lt;artifactId&gt;spring-cloud-dependencies&lt;/artifactId&gt;
            &lt;version&gt;${spring-cloud.version}&lt;/version&gt;
            &lt;type&gt;pom&lt;/type&gt;
            &lt;scope&gt;import&lt;/scope&gt;
        &lt;/dependency&gt;
    &lt;/dependencies&gt;
&lt;/dependencyManagement&gt;

&lt;build&gt;
    &lt;plugins&gt;
        &lt;!-- Lombok doit etre declare comme processeur d'annotations --&gt;
        &lt;plugin&gt;
            &lt;groupId&gt;org.apache.maven.plugins&lt;/groupId&gt;
            &lt;artifactId&gt;maven-compiler-plugin&lt;/artifactId&gt;
            &lt;configuration&gt;
                &lt;annotationProcessorPaths&gt;
                    &lt;path&gt;
                        &lt;groupId&gt;org.projectlombok&lt;/groupId&gt;
                        &lt;artifactId&gt;lombok&lt;/artifactId&gt;
                    &lt;/path&gt;
                &lt;/annotationProcessorPaths&gt;
            &lt;/configuration&gt;
        &lt;/plugin&gt;
        &lt;plugin&gt;
            &lt;groupId&gt;org.springframework.boot&lt;/groupId&gt;
            &lt;artifactId&gt;spring-boot-maven-plugin&lt;/artifactId&gt;
            &lt;!-- Lombok ne sert qu'a la compilation : on l'exclut du .jar --&gt;
            &lt;configuration&gt;
                &lt;excludes&gt;
                    &lt;exclude&gt;
                        &lt;groupId&gt;org.projectlombok&lt;/groupId&gt;
                        &lt;artifactId&gt;lombok&lt;/artifactId&gt;
                    &lt;/exclude&gt;
                &lt;/excludes&gt;
            &lt;/configuration&gt;
        &lt;/plugin&gt;
    &lt;/plugins&gt;
&lt;/build&gt;
</code></pre>
<p><strong>Points à comprendre :</strong>
- <code>springdoc-openapi-starter-webmvc-ui</code> produit sans aucune ligne de Java la description OpenAPI (<code>/v3/api-docs</code>) et la page Swagger UI. Sa version (3.0.3) doit être écrite (non gérée par Spring Boot) ; pas de version 2.x, réservée à Spring Boot 3.
- <code>&lt;optional&gt;true&lt;/optional&gt;</code> : Lombok ne sert qu'à compiler. <code>&lt;annotationProcessorPaths&gt;</code> indique au compilateur d'exécuter Lombok, sans quoi les getters et setters « n'existent pas » à la compilation.
- Activer l'annotation processing dans IntelliJ si une notification le propose.</p>
<h3>C.1 bis Générer constructeurs, getters et setters avec Lombok</h3>
<p>Classe <code>Resultat</code> du paquetage <code>model</code> de <code>calcul-service</code> :</p>
<pre><code class="language-java">package com.exemple.calcul.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class Resultat {
    private String operation;
    private int a;
    private int b;
    private double resultat;
}
</code></pre>
<p><code>@Getter</code>/<code>@Setter</code> créent <code>getOperation()</code>, <code>setOperation(...)</code>… (Jackson les utilise : la réponse JSON de <code>/calcul/somme/detail</code> est inchangée). <code>@AllArgsConstructor</code> suit l'ordre de déclaration des attributs. <code>@Data</code> regroupe <code>@Getter</code>, <code>@Setter</code>, <code>@ToString</code>, <code>@EqualsAndHashCode</code> et <code>@RequiredArgsConstructor</code>.</p>
<h3>C.2 Indiquer l'adresse de l'annuaire : <code>application.properties</code> de <code>calcul-service</code></h3>
<pre><code class="language-properties">spring.application.name=calcul-service
server.port=8083

# Adresse de l'annuaire Eureka
eureka.client.service-url.defaultZone=http://localhost:8761/eureka

# Identifiant lisible de l'instance
eureka.instance.instance-id=${spring.application.name}:${server.port}
eureka.instance.prefer-ip-address=true

# Reglages pour le TP : battement de coeur toutes les 5 s
# et instance retiree apres 10 s sans nouvelle
eureka.instance.lease-renewal-interval-in-seconds=5
eureka.instance.lease-expiration-duration-in-seconds=10

# Swagger derriere la passerelle (partie E) : en-tetes X-Forwarded-*
server.forward-headers-strategy=framework
</code></pre>
<ul>
<li><code>spring.application.name</code> devient le nom du service dans l'annuaire, en majuscules : <code>CALCUL-SERVICE</code>.</li>
<li><code>eureka.client.service-url.defaultZone</code> est la seule adresse que les microservices ont encore besoin de connaître.</li>
<li><code>prefer-ip-address=true</code> : l'instance s'inscrit avec son IP plutôt qu'avec son nom de machine.</li>
<li>Aucune annotation à ajouter dans <code>CalculServiceApplication</code> (<code>@EnableDiscoveryClient</code> est facultative).</li>
<li><code>server.forward-headers-strategy=framework</code> : le microservice reconstruit l'adresse vue par le client (<code>http://localhost:8888...</code>) à partir des en-têtes <code>X-Forwarded-*</code> ajoutés par la passerelle.</li>
</ul>
<h3>C.3 Tracer l'instance qui répond</h3>
<p>Dans <code>CalculController</code> :</p>
<pre><code class="language-java">// Port de cette instance, lu dans la configuration
@Value(&quot;${server.port}&quot;)
private int port;

@GetMapping(value = &quot;/somme&quot;, produces = MediaType.TEXT_PLAIN_VALUE)
public ResponseEntity&lt;String&gt; somme(@RequestParam(&quot;a&quot;) int a,
                                    @RequestParam(&quot;b&quot;) int b) {
    System.out.println(&quot;[calcul-service:&quot; + port + &quot;] somme &quot; + a + &quot;+&quot; + b);
    int result = calculService.somme(a, b);
    return ResponseEntity.ok(String.valueOf(result));
}
// Dans les trois autres methodes :
// System.out.println(&quot;[calcul-service:&quot; + port + &quot;] soustraction &quot; + a + &quot;-&quot; + b);
// System.out.println(&quot;[calcul-service:&quot; + port + &quot;] produit &quot; + a + &quot;*&quot; + b);
// System.out.println(&quot;[calcul-service:&quot; + port + &quot;] division &quot; + a + &quot;/&quot; + b);
</code></pre>
<h3>C.4 Lancer <code>calcul-service</code> et vérifier l'inscription</h3>
<p>Lancer <code>CalculServiceApplication</code> (l'annuaire doit tourner). La console montre :</p>
<pre><code>... DiscoveryClient_CALCUL-SERVICE/calcul-service:8083: registering service...
... registration status: 204
... Tomcat started on port 8083 (http) with context path '/'
... Started CalculServiceApplication in 3.456 seconds
</code></pre>
<p><code>http://localhost:8761</code> affiche <code>CALCUL-SERVICE</code> en <code>UP (1)</code>. Test avec Swagger UI à <code>http://localhost:8083/swagger-ui.html</code> : <code>GET /calcul/somme</code> avec <code>a = 2</code>, <code>b = 3</code> donne <code>5</code>, et la console affiche <code>[calcul-service:8083] somme 2+3</code>.</p>
<p><em>Lire la page Swagger UI :</em> <strong>Servers</strong> (adresse à laquelle les requêtes sont envoyées) ; une ligne par opération, regroupées par contrôleur ; <em>Try it out</em> rend les paramètres modifiables ; après <em>Execute</em>, la zone <em>Responses</em> affiche la <em>Request URL</em>, le <em>Code</em>, le <em>Response body</em> et les <em>Response headers</em>.</p>
<h3>C.5 Instance de <code>calcul-service</code></h3>
<p>Dans ce rendu, <code>calcul-service</code> tourne en <strong>une seule instance, sur le port 8083</strong> (<code>CALCUL-SERVICE</code> affiche <code>UP (1)</code>). Pour lancer un second exemplaire, on utiliserait le <code>.jar</code> avec une option de ligne de commande : <code>java -jar target/calcul-service-0.0.1-SNAPSHOT.jar --server.port=8084</code>.</p>
<p>Test sur <code>http://localhost:8083/swagger-ui.html</code> : <code>GET /calcul/produit</code> avec <code>a = 6</code>, <code>b = 7</code> donne <code>42</code>, et la trace <code>[calcul-service:8083] produit 6*7</code> s'affiche dans la console.</p>
<p><img alt="Swagger calcul-service 8083" 
            <img width="1865" height="1005" alt="Capture d&#39;écran 2026-11-15 150045" src="https://github.com/user-attachments/assets/145b7755-ee83-4372-b615-9fc9f5b23b4b" />

<p><strong>Question C.1. À quoi servent le nom et l'identifiant d'instance ?</strong>
Le <strong>nom</strong> (<code>CALCUL-SERVICE</code>) identifie le service logique : c'est lui que l'appelant utilise (<code>lb://CALCUL-SERVICE</code>, <code>@FeignClient</code>). L'<strong>identifiant</strong> (par exemple <code>calcul-service:8083</code>) distingue chaque instance : Eureka l'utilise pour les inscrire, renouveler et retirer séparément, et LoadBalancer pour en choisir une.</p>
<p><strong>Question C.2. Et si l'on lançait une seconde instance sans changer le port ?</strong>
Elle utiliserait 8083 (déjà pris) : Tomcat ne peut pas démarrer (<em>Port 8083 was already in use</em>) et l'application s'arrête.</p>
<h3>C.6 Expérience : arrêter une instance</h3>
<p>Arrêter l'instance 8083 : au bout de quelques secondes (15 s au plus), <code>CALCUL-SERVICE</code> disparaît du tableau de bord (ou repasse à une instance de moins s'il y en a plusieurs). Quand l'arrêt est propre (Ctrl+C, bouton Stop), le client Eureka envoie une désinscription et l'instance disparaît immédiatement. Quand le processus est tué brutalement, l'annuaire retire l'instance après <code>lease-expiration-duration-in-seconds</code> (10 s), lors de sa vérification suivante (<code>eviction-interval-timer-in-ms</code>, 5 s). Relancée, l'instance réapparaît dès son démarrage.</p>
<h3>C.7 Enregistrer <code>historique-service</code></h3>
<p>Mêmes ajouts que C.1 dans le <code>pom.xml</code> (propriété <code>spring-cloud.version</code>, dépendances <code>eureka-client</code>, <code>springdoc-openapi-starter-webmvc-ui</code>, <code>lombok</code>, blocs <code>&lt;dependencyManagement&gt;</code> et <code>&lt;build&gt;</code>), plus OpenFeign :</p>
<pre><code class="language-xml">&lt;dependency&gt;
    &lt;groupId&gt;org.springframework.cloud&lt;/groupId&gt;
    &lt;artifactId&gt;spring-cloud-starter-openfeign&lt;/artifactId&gt;
&lt;/dependency&gt;
</code></pre>
<p><code>application.properties</code> d'<code>historique-service</code> :</p>
<pre><code class="language-properties">spring.application.name=historique-service
server.port=8082

# Adresse de l'annuaire Eureka
eureka.client.service-url.defaultZone=http://localhost:8761/eureka
eureka.instance.instance-id=${spring.application.name}:${server.port}
eureka.instance.prefer-ip-address=true
eureka.instance.lease-renewal-interval-in-seconds=5
eureka.instance.lease-expiration-duration-in-seconds=10

# Copie locale de l'annuaire renouvelee toutes les 5 s (defaut : 30 s)
eureka.client.registry-fetch-interval-seconds=5
# Cache du repartiteur de charge conserve 5 s (defaut : 35 s)
spring.cloud.loadbalancer.cache.ttl=5s

# Swagger derriere la passerelle : en-tetes X-Forwarded-* (voir C.2)
server.forward-headers-strategy=framework
</code></pre>
<p>La propriété <code>calcul.service.url</code> du TP3 a disparu. Ne pas lancer <code>historique-service</code> avant la partie D.</p>
<p>Lombok sur <code>Operation</code> :</p>
<pre><code class="language-java">package com.exemple.historique.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class Operation {
    private int id;
    private String operateur; // somme, soustraction, produit ou division
    private int a;
    private int b;
    private double resultat;
}
</code></pre>
<p><em>Pour aller plus loin :</em> <code>@RequiredArgsConstructor</code> sur les contrôleurs remplace le constructeur d'injection (attributs <code>private final</code>).</p>
<hr />
<h2>Partie D : <code>historique-service</code> appelle <code>calcul-service</code> par son nom : OpenFeign</h2>
<h3>D.1 Activer OpenFeign</h3>
<pre><code class="language-java">package com.exemple.historique;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class HistoriqueServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HistoriqueServiceApplication.class, args);
    }
}
</code></pre>
<h3>D.2 L'interface <code>CalculClient</code></h3>
<p>Supprimer la classe <code>CalculClient</code> du TP3 et créer à la place :</p>
<pre><code class="language-java">package com.exemple.historique.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

// name = nom du service dans l'annuaire (spring.application.name de calcul-service)
@FeignClient(name = &quot;CALCUL-SERVICE&quot;)
public interface CalculClient {

    // Chaque appel envoie GET http://&lt;instance&gt;/calcul/{operateur}?a=..&amp;b=..
    @GetMapping(&quot;/calcul/{operateur}&quot;)
    String calculer(@PathVariable(&quot;operateur&quot;) String operateur,
                    @RequestParam(&quot;a&quot;) int a,
                    @RequestParam(&quot;b&quot;) int b);
}
</code></pre>
<p>Le nom remplace l'adresse ; LoadBalancer choisit une instance à tour de rôle ; aucune implémentation à écrire (Spring génère l'objet et l'injecte dans <code>OperationController</code>).</p>
<h3>D.3 Adapter <code>OperationController</code></h3>
<p>Remplacer les imports <code>HttpClientErrorException</code> et <code>ResourceAccessException</code> par <code>import feign.FeignException;</code> puis la méthode privée <code>calculer</code> :</p>
<pre><code class="language-java">private ResponseEntity&lt;Object&gt; calculer(Operation op) {
    if (!OPERATEURS.contains(op.getOperateur())) {
        return ResponseEntity.badRequest()
                .body(Map.of(&quot;erreur&quot;, &quot;Operateur inconnu : &quot; + op.getOperateur()));
    }
    try {
        String corps = calculClient.calculer(op.getOperateur(),
                                             op.getA(), op.getB());
        op.setResultat(Double.parseDouble(corps));
        return null;
    } catch (FeignException.BadRequest e) {
        // calcul-service a repondu 400 (division par 0) : on transmet le message
        return ResponseEntity.badRequest()
                .body(Map.of(&quot;erreur&quot;, e.contentUTF8()));
    } catch (FeignException e) {
        // Aucune instance de CALCUL-SERVICE disponible ou injoignable
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(&quot;erreur&quot;, &quot;calcul-service injoignable&quot;));
    }
}
</code></pre>
<p>L'ordre des <code>catch</code> est important : la sous-classe d'abord.</p>
<table>
<thead>
<tr>
<th>Rôle</th>
<th>TP3 : RestClient</th>
<th>TP4 : OpenFeign</th>
</tr>
</thead>
<tbody>
<tr>
<td>Déclaration</td>
<td>Classe <code>@Service</code> avec un attribut <code>RestClient</code></td>
<td>Interface <code>@FeignClient(name = ...)</code></td>
</tr>
<tr>
<td>Adresse du service</td>
<td><code>@Value("${calcul.service.url}")</code> → <code>RestClient.create(url)</code></td>
<td><code>name = "CALCUL-SERVICE"</code> résolu par LoadBalancer via Eureka</td>
</tr>
<tr>
<td>Requête</td>
<td><code>client.get().uri(...).retrieve().body(String.class)</code></td>
<td><code>@GetMapping</code> + <code>@PathVariable</code>, <code>@RequestParam</code></td>
</tr>
<tr>
<td>Service répond 400</td>
<td><code>HttpClientErrorException</code></td>
<td><code>FeignException.BadRequest</code></td>
</tr>
<tr>
<td>Service injoignable</td>
<td><code>ResourceAccessException</code></td>
<td><code>FeignException</code></td>
</tr>
<tr>
<td>Plusieurs instances</td>
<td>Non géré</td>
<td>Tour de rôle automatique</td>
</tr>
</tbody>
</table>
<h3>D.4 Lancer et tester <code>historique-service</code></h3>
<p>Lancer <code>HistoriqueServiceApplication</code> avec l'annuaire et <code>calcul-service</code> (8083) en marche. Le tableau de bord affiche <code>CALCUL-SERVICE UP (1)</code> et <code>HISTORIQUE-SERVICE UP (1)</code>. Requêtes à envoyer depuis <code>http://localhost:8082/swagger-ui.html</code> :</p>
<table>
<thead>
<tr>
<th>N°</th>
<th>Requête</th>
<th>Corps envoyé</th>
<th>Statut</th>
<th>Réponse attendue</th>
</tr>
</thead>
<tbody>
<tr>
<td>1</td>
<td>POST /operations</td>
<td><code>{"operateur":"somme","a":8,"b":2}</code></td>
<td>201</td>
<td><code>{"id":1,"operateur":"somme","a":8,"b":2,"resultat":10.0}</code> + <code>Location: http://localhost:8082/operations/1</code></td>
</tr>
<tr>
<td>2</td>
<td>POST /operations</td>
<td><code>{"operateur":"produit","a":6,"b":7}</code></td>
<td>201</td>
<td><code>{"id":2,...,"resultat":42.0}</code></td>
</tr>
<tr>
<td>3</td>
<td>POST /operations</td>
<td><code>{"operateur":"division","a":7,"b":2}</code></td>
<td>201</td>
<td><code>{"id":3,...,"resultat":3.5}</code></td>
</tr>
<tr>
<td>4</td>
<td>POST /operations</td>
<td><code>{"operateur":"division","a":7,"b":0}</code></td>
<td>400</td>
<td><code>{"erreur":"Erreur : division par 0 impossible"}</code></td>
</tr>
<tr>
<td>5</td>
<td>POST /operations</td>
<td><code>{"operateur":"racine","a":9,"b":0}</code></td>
<td>400</td>
<td><code>{"erreur":"Operateur inconnu : racine"}</code></td>
</tr>
<tr>
<td>6</td>
<td>GET /operations</td>
<td>Aucun</td>
<td>200</td>
<td>Tableau JSON des opérations 1, 2 et 3</td>
</tr>
<tr>
<td>7</td>
<td>GET /operations/2</td>
<td>Aucun</td>
<td>200</td>
<td><code>{"id":2,"operateur":"produit","a":6,"b":7,"resultat":42.0}</code></td>
</tr>
</tbody>
</table>
<p>Les traces des requêtes 1, 2 et 3 s'affichent dans la console <code>[calcul-service:8083]</code> : <code>historique-service</code> ne connaît que le nom <code>CALCUL-SERVICE</code> et trouve l'adresse dans l'annuaire. Avec plusieurs instances, elles se répartiraient en alternance (répartition de charge).</p>
<p><img alt="Swagger historique-service 8082"
            <img width="1831" height="972" alt="Capture d&#39;écran 2026-11-15 145602" src="https://github.com/user-attachments/assets/4577fb40-6d29-4b2c-8e6b-ee8fb4da0c66" />

            
<p><strong>Question D.1. Six fois la requête 1 : quelle est la règle de choix ? Comment s'appelle cette stratégie ?</strong>
Avec une seule instance (8083), les six traces s'affichent sur 8083. Avec plusieurs instances, elles alterneraient : la règle est « à tour de rôle » et la stratégie s'appelle <strong>round robin</strong> (tourniquet).</p>
<p><strong>Question D.2. Avec <code>name = "CALCUL-SERVIC"</code> (faute volontaire), quel statut et quel bloc <code>catch</code> ?</strong>
LoadBalancer ne trouve aucune instance : la réponse est un <strong>503</strong> <code>{"erreur":"calcul-service injoignable"}</code>, produite par le bloc <code>catch (FeignException e)</code> (le bloc <code>FeignException.BadRequest</code> n'est pas concerné).</p>
<hr />
<h2>Partie E : La passerelle : <code>gateway-service</code></h2>
<h3>E.1 Générer le module <code>gateway-service</code></h3>
<p>Nouveau module Spring Boot : Artifact <code>gateway-service</code>, Package <code>com.exemple.gateway</code>, Spring Boot 4.0.9, dépendances <strong>Gateway</strong> (variante réactive, <code>spring-cloud-starter-gateway-server-webflux</code>), <strong>Eureka Discovery Client</strong> et <strong>Spring Boot Actuator</strong>. Ajouter à la main springdoc :</p>
<pre><code class="language-xml">&lt;dependencies&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.springframework.boot&lt;/groupId&gt;
        &lt;artifactId&gt;spring-boot-starter-actuator&lt;/artifactId&gt;
    &lt;/dependency&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.springframework.cloud&lt;/groupId&gt;
        &lt;artifactId&gt;spring-cloud-starter-gateway-server-webflux&lt;/artifactId&gt;
    &lt;/dependency&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.springframework.cloud&lt;/groupId&gt;
        &lt;artifactId&gt;spring-cloud-starter-netflix-eureka-client&lt;/artifactId&gt;
    &lt;/dependency&gt;
    &lt;!-- Swagger UI de la passerelle : variante reactive (WebFlux) de springdoc --&gt;
    &lt;dependency&gt;
        &lt;groupId&gt;org.springdoc&lt;/groupId&gt;
        &lt;artifactId&gt;springdoc-openapi-starter-webflux-ui&lt;/artifactId&gt;
        &lt;version&gt;3.0.3&lt;/version&gt;
    &lt;/dependency&gt;
&lt;/dependencies&gt;
</code></pre>
<p>Pas de <code>spring-boot-starter-webmvc</code> : Spring Cloud Gateway est construit sur WebFlux (non bloquant, serveur Netty). Pas de Lombok (aucune classe de données).</p>
<h3>E.2 Routes statiques en YAML</h3>
<p><code>src/main/resources/application.yml</code> (remplace <code>application.properties</code>) :</p>
<pre><code class="language-yaml">server:
  port: 8888

spring:
  application:
    name: gateway-service
  cloud:
    gateway:
      server:
        webflux:
          routes:
            # Route 1 : tout ce qui commence par /calcul/ va vers calcul-service
            - id: route-calcul
              uri: http://localhost:8083
              predicates:
                - Path=/calcul/**
            # Route 2 : tout ce qui commence par /operations/ va vers historique
            - id: route-historique
              uri: http://localhost:8082
              predicates:
                - Path=/operations/**
            # Routes 3 et 4 : description OpenAPI de chaque microservice
            - id: doc-calcul
              uri: http://localhost:8083
              predicates:
                - Path=/docs/calcul
              filters:
                - SetPath=/v3/api-docs
            - id: doc-historique
              uri: http://localhost:8082
              predicates:
                - Path=/docs/historique
              filters:
                - SetPath=/v3/api-docs
    loadbalancer:
      cache:
        ttl: 5s

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka
    registry-fetch-interval-seconds: 5
  instance:
    instance-id: ${spring.application.name}:${server.port}
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 5
    lease-expiration-duration-in-seconds: 10

management:
  endpoints:
    web:
      exposure:
        include: gateway

# Swagger UI de la passerelle : http://localhost:8888/swagger-ui.html
springdoc:
  swagger-ui:
    # Liste &quot;Select a definition&quot; : une entree par microservice
    urls:
      - name: calcul-service
        url: /docs/calcul
      - name: historique-service
        url: /docs/historique
</code></pre>
<p><strong>Points à comprendre :</strong>
- Une route = un <code>id</code>, une destination <code>uri</code>, des prédicats <code>predicates</code>. <code>Path=/calcul/**</code> est vrai pour tout chemin commençant par <code>/calcul/</code>. La requête est transmise telle quelle : <code>GET :8888/calcul/somme?a=2&amp;b=3</code> devient <code>GET :8083/calcul/somme?a=2&amp;b=3</code>.
- Le préfixe <code>spring.cloud.gateway.server.webflux</code> est celui de Spring Cloud Gateway 5 (Spring Cloud 2025.x) ; les tutoriels anciens utilisent <code>spring.cloud.gateway.routes</code>.
- Les routes 3 et 4 servent à Swagger UI : la page de la passerelle télécharge la description OpenAPI de chaque microservice via la passerelle elle-même ; le filtre <code>SetPath=/v3/api-docs</code> remplace le chemin avant transmission.
- Grâce à <code>server.forward-headers-strategy=framework</code>, chaque microservice indique dans sa description l'adresse vue par le client, <code>http://localhost:8888</code> (zone <em>Servers</em>) : chaque <em>Execute</em> passe par la passerelle.</p>
<p>Page Swagger UI de la passerelle (<code>http://localhost:8888/swagger-ui/index.html</code>) avec la liste <strong>Select a definition</strong> :</p>
<p><img alt="Gateway – définition calcul-service" 

<img width="1917" height="982" alt="Capture d&#39;écran 2026-11-14 114706" src="https://github.com/user-attachments/assets/250c4357-4acc-433f-b641-49cdd1a2e5a0" />


<p><img alt="Gateway – définition historique-service" 
<img width="1917" height="1001" alt="Capture d&#39;écran 2026-11-14 114650" src="https://github.com/user-attachments/assets/9564ee04-9e00-4d79-8c35-c4a410c90079" />


<p>Requêtes de test (préfixe <code>http://localhost:8888</code>) :</p>
<table>
<thead>
<tr>
<th>N°</th>
<th>Requête</th>
<th>Statut</th>
<th>Réponse attendue</th>
<th>Qui a répondu</th>
</tr>
</thead>
<tbody>
<tr>
<td>1</td>
<td>GET /calcul/somme?a=2&amp;b=3</td>
<td>200</td>
<td><code>5</code></td>
<td>calcul-service 8083</td>
</tr>
<tr>
<td>2</td>
<td>GET /calcul/division?a=7&amp;b=0</td>
<td>400</td>
<td>Erreur : division par 0 impossible</td>
<td>calcul-service 8083</td>
</tr>
<tr>
<td>3</td>
<td>GET /operations</td>
<td>200</td>
<td>Tableau JSON des opérations créées en D.4</td>
<td>historique-service</td>
</tr>
<tr>
<td>4</td>
<td>POST /operations avec <code>{"operateur":"soustraction","a":10,"b":4}</code></td>
<td>201</td>
<td><code>{"id":4,...,"resultat":6.0}</code> ; <code>Location: http://localhost:8888/operations/4</code></td>
<td>historique-service, puis une instance de calcul-service</td>
</tr>
<tr>
<td>5</td>
<td>GET /calcul/somme/detail?a=2&amp;b=3</td>
<td>200</td>
<td><code>{"operation":"somme","a":2,"b":3,"resultat":5.0}</code></td>
<td>calcul-service 8083</td>
</tr>
<tr>
<td>6</td>
<td>GET /bonjour (navigateur)</td>
<td>404</td>
<td>Corps JSON d'erreur de la passerelle</td>
<td>La passerelle elle-même : aucune route ne correspond</td>
</tr>
</tbody>
</table>
<p>Avec des adresses fixes, la trace apparaît toujours dans la console de l'instance 8083, quelles que soient les instances inscrites dans l'annuaire, comme au TP3.</p>
<p><strong>Question E.1. Pourquoi l'en-tête <code>Location</code> de la requête 4 indique-t-il le port 8888 et non 8082 ?</strong>
La passerelle ajoute les en-têtes <code>X-Forwarded-Host</code>, <code>X-Forwarded-Port</code> (et <code>X-Forwarded-Prefix</code>). Avec <code>server.forward-headers-strategy=framework</code>, <code>historique-service</code> les lit et <code>ServletUriComponentsBuilder.fromCurrentRequest()</code> construit <code>Location</code> avec <code>localhost:8888</code>.</p>
<h3>E.3 Routes statiques vers l'annuaire : <code>lb://</code></h3>
<pre><code class="language-yaml">routes:
  - id: route-calcul
    uri: lb://CALCUL-SERVICE
    predicates:
      - Path=/calcul/**
  - id: route-historique
    uri: lb://HISTORIQUE-SERVICE
    predicates:
      - Path=/operations/**
  - id: doc-calcul
    uri: lb://CALCUL-SERVICE
    predicates:
      - Path=/docs/calcul
    filters:
      - SetPath=/v3/api-docs
  - id: doc-historique
    uri: lb://HISTORIQUE-SERVICE
    predicates:
      - Path=/docs/historique
    filters:
      - SetPath=/v3/api-docs
</code></pre>
<p>Six clics sur <em>Execute</em> pour <code>GET :8888/calcul/somme?a=2&amp;b=3</code> : la passerelle demande à l'annuaire les instances de <code>CALCUL-SERVICE</code> et les sollicite à tour de rôle, comme OpenFeign (la trace s'affiche sur 8083, seule instance ; avec plusieurs instances, elles alterneraient). Le nom après <code>lb://</code> doit être le nom enregistré dans Eureka.</p>
<h3>E.4 Routes statiques en Java</h3>
<p>Après avoir commenté les routes YAML (sans toucher au bloc <code>springdoc</code>), ajouter dans <code>GatewayServiceApplication</code> :</p>
<pre><code class="language-java">package com.exemple.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }

    // Equivalent Java des routes de application.yml
    @Bean
    RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(&quot;route-calcul&quot;,
                        r -&gt; r.path(&quot;/calcul/**&quot;)
                              .uri(&quot;lb://CALCUL-SERVICE&quot;))
                .route(&quot;route-historique&quot;,
                        r -&gt; r.path(&quot;/operations/**&quot;)
                              .uri(&quot;lb://HISTORIQUE-SERVICE&quot;))
                // Routes de Swagger UI
                .route(&quot;doc-calcul&quot;,
                        r -&gt; r.path(&quot;/docs/calcul&quot;)
                              .filters(f -&gt; f.setPath(&quot;/v3/api-docs&quot;))
                              .uri(&quot;lb://CALCUL-SERVICE&quot;))
                .route(&quot;doc-historique&quot;,
                        r -&gt; r.path(&quot;/docs/historique&quot;)
                              .filters(f -&gt; f.setPath(&quot;/v3/api-docs&quot;))
                              .uri(&quot;lb://HISTORIQUE-SERVICE&quot;))
                .build();
    }
}
</code></pre>
<p>Les deux formes (YAML et Java) sont équivalentes ; si les deux sont présentes, leurs routes s'ajoutent.</p>
<h3>E.5 Routes dynamiques : créées à partir de l'annuaire</h3>
<p>Commenter <code>@Bean</code> de la méthode <code>routes</code>, puis dans <code>application.yml</code> :</p>
<pre><code class="language-yaml">spring:
  cloud:
    gateway:
      server:
        webflux:
          # routes: ... (en commentaire depuis l'etape E.4)
          discovery:
            locator:
              # Une route par service present dans l'annuaire
              enabled: true
              # Noms Eureka en majuscules ; acceptes en minuscules dans l'URI
              lower-case-service-id: true

springdoc:
  swagger-ui:
    # Routes dynamiques : chaque description est lue par la route du service
    urls:
      - name: calcul-service
        url: /calcul-service/v3/api-docs
      - name: historique-service
        url: /historique-service/v3/api-docs
</code></pre>
<p>La route dynamique retire le préfixe <code>/calcul-service</code> et ajoute l'en-tête <code>X-Forwarded-Prefix: /calcul-service</code> : la zone <em>Servers</em> indique donc <code>http://localhost:8888/calcul-service</code>.</p>
<table>
<thead>
<tr>
<th>N°</th>
<th>Requête (préfixe <code>http://localhost:8888</code>)</th>
<th>Requête transmise au microservice</th>
<th>Statut</th>
<th>Réponse attendue</th>
</tr>
</thead>
<tbody>
<tr>
<td>1</td>
<td>GET /calcul-service/calcul/somme?a=2&amp;b=3</td>
<td>GET /calcul/somme?a=2&amp;b=3 vers CALCUL-SERVICE</td>
<td>200</td>
<td><code>5</code></td>
</tr>
<tr>
<td>2</td>
<td>GET /calcul-service/calcul/produit/6/7</td>
<td>GET /calcul/produit/6/7</td>
<td>200</td>
<td><code>42</code></td>
</tr>
<tr>
<td>3</td>
<td>GET /historique-service/operations</td>
<td>GET /operations vers HISTORIQUE-SERVICE</td>
<td>200</td>
<td>Tableau JSON des opérations</td>
</tr>
<tr>
<td>4</td>
<td>POST /historique-service/operations avec <code>{"operateur":"somme","a":1,"b":1}</code></td>
<td>POST /operations</td>
<td>201</td>
<td><code>{"id":5,...,"resultat":2.0}</code></td>
</tr>
<tr>
<td>5</td>
<td>GET /calcul/somme?a=2&amp;b=3 (navigateur)</td>
<td>Aucune : plus de route <code>/calcul/**</code></td>
<td>404</td>
<td>Corps JSON d'erreur de la passerelle</td>
</tr>
<tr>
<td>6</td>
<td>GET /gateway-service/actuator/gateway/routes (navigateur)</td>
<td>GET /actuator/gateway/routes vers la passerelle elle-même</td>
<td>200</td>
<td>Liste JSON des routes</td>
</tr>
</tbody>
</table>
<p>Captures (appels via la passerelle) :</p>
<p><code>GET http://localhost:8888/calcul-service/calcul/somme?a=2&amp;b=3</code> → <code>5</code></p>
<p><img alt="Somme 2+3" 

            <img width="1487" height="496" alt="Capture d&#39;écran 2026-11-14 115032" src="https://github.com/user-attachments/assets/49cf602a-52a9-44fc-b546-dd4427e9cd3d" />

<p><code>GET http://localhost:8888/calcul-service/calcul/produit?a=6&amp;b=7</code> → <code>42</code></p>
<p><img alt="Produit 6x7" 

            <img width="1423" height="577" alt="Capture d&#39;écran 2026-11-14 115202" src="https://github.com/user-attachments/assets/30b7a344-4f47-4336-ade9-36d4b6c99db1" />

<p><code>GET http://localhost:8888/historique-service/operations</code> → <code>[]</code> (aucune opération enregistrée pour le moment)</p>
<p><img alt="Liste des opérations" 

            <img width="1917" height="1001" alt="Capture d&#39;écran 2026-11-14 114650" src="https://github.com/user-attachments/assets/a71eb67a-9d3b-4863-ae6c-7d7afe11db38" />

<p>Exécution depuis Swagger UI de la passerelle : <code>/calcul/somme</code> avec <code>a=4</code> et <code>b=6</code>.</p>
<p><img alt="Swagger somme 4+6" 

            <img width="1733" height="678" alt="Capture d&#39;écran 2026-11-14 122813" src="https://github.com/user-attachments/assets/bd418ab7-bd81-448f-af5c-0ee2f249173d" />

<p>La <em>Request URL</em> est <code>http://localhost:8888/calcul-service/calcul/somme?a=4&amp;b=6</code> : la requête passe par la passerelle, code <strong>200</strong>, corps <code>10</code>.</p>
<p><img alt="Responses somme 4+6" 

<img width="1740" height="807" alt="Capture d&#39;écran 2026-11-14 122821" src="https://github.com/user-attachments/assets/0c7b6344-f6f0-497b-a6d3-f5476fc6684d" />

         
<p><strong>Actuator de la passerelle.</strong> Endpoints exposés par <code>http://localhost:8888/actuator/gateway</code> :</p>
<p><img alt="Actuator gateway" 

            <img width="1917" height="481" alt="Capture d&#39;écran 2026-11-14 120953" src="https://github.com/user-attachments/assets/72f830b9-d026-4e46-a0ed-a1a9e0d86a23" />

<p>Routes en service au démarrage : seules <code>CALCUL-SERVICE</code> et <code>HISTORIQUE-SERVICE</code> sont connues (la passerelle ne s'était pas encore inscrite / rafraîchie dans l'annuaire).</p>


<p><img alt="Routes (avant)" 
            <img width="1917" height="481" alt="Capture d&#39;écran 2026-11-14 120953" src="https://github.com/user-attachments/assets/49d66248-8f90-447d-a0a2-9a326c0378af" />
<p>Routes en configuration dynamique (<code>http://localhost:8888/actuator/gateway/routes</code>) : une route par service de l'annuaire (<code>CALCUL-SERVICE</code>, <code>GATEWAY-SERVICE</code>, <code>HISTORIQUE-SERVICE</code>), chacune avec son prédicat <code>Path</code> et son filtre <code>RewritePath</code>. Quand <code>historique-service</code> est arrêté, sa route disparaît ; relancé, elle réapparaît : les routes suivent l'annuaire.</p>
<p><img alt="Routes dynamiques" 

            <img width="1917" height="467" alt="Capture d&#39;écran 2026-11-14 122927" src="https://github.com/user-attachments/assets/a5974d65-8f8c-4114-acc3-6ba6e3eaf618" />

<p><strong>Question E.2. Comparez les URI des étapes E.3 et E.5. Quel est l'avantage de chaque forme ? Laquelle oblige à modifier la passerelle quand on ajoute un microservice ?</strong>
- E.3 (routes statiques) : <code>GET /calcul/somme?a=2&amp;b=3</code>. URI courtes, indépendantes du nom du service, mais <strong>il faut modifier la passerelle</strong> à chaque nouveau microservice.
- E.5 (routes dynamiques) : <code>GET /calcul-service/calcul/somme?a=2&amp;b=3</code>. Aucune modification de la passerelle quand on ajoute un service, mais le nom interne du service apparaît dans l'URI.</p>
<p><strong>Question E.3. Pourquoi la route 6 montre-t-elle une route vers la passerelle elle-même ? Est-ce gênant ?</strong>
<code>gateway-service</code> est lui-même inscrit dans Eureka : le locator crée donc une route pour <strong>chaque</strong> service de l'annuaire, y compris lui-même. Ce n'est pas bloquant pour le TP, mais en production cela expose par exemple <code>/gateway-service/actuator/...</code> ; on filtre ou on sécurise ces routes.</p>
<hr />
<h2>Questions de synthèse</h2>
<p><strong>1. Reprenez le tableau A.3 et, pour chaque ligne, indiquez l'étape où vous avez observé la différence.</strong></p>
<table>
<thead>
<tr>
<th>Aspect</th>
<th>Étape</th>
</tr>
</thead>
<tbody>
<tr>
<td>Nombre de programmes</td>
<td>B, C, E</td>
</tr>
<tr>
<td>Dépendances Maven</td>
<td>B.2, C.1, C.7, E.1</td>
</tr>
<tr>
<td>Adresse d'un microservice</td>
<td>C.2, C.7, D.2</td>
</tr>
<tr>
<td>Appel entre microservices</td>
<td>D.2, D.3</td>
</tr>
<tr>
<td>Erreurs d'appel</td>
<td>D.3, D.4 (requêtes 4 et 5), D.2</td>
</tr>
<tr>
<td>Point d'entrée du client</td>
<td>E.2</td>
</tr>
<tr>
<td>Plusieurs instances</td>
<td>C.5, C.6, D.4, E.3</td>
</tr>
<tr>
<td>Serveur embarqué</td>
<td>E.1 (Netty), consoles</td>
</tr>
<tr>
<td>Arrêt d'un microservice</td>
<td>C.6, E.5</td>
</tr>
<tr>
<td>Organisation du code</td>
<td>B.0, C.0</td>
</tr>
<tr>
<td>Outil de test</td>
<td>C.4, D.4, E.2</td>
</tr>
<tr>
<td>Classes de données</td>
<td>C.1 bis, C.7</td>
</tr>
</tbody>
</table>
<p><strong>2. Trajet complet de <code>POST :8888/historique-service/operations</code> (<code>{"operateur":"somme","a":1,"b":2}</code>).</strong>
1. Swagger UI / navigateur → <strong>gateway-service</strong> (8888).
2. La passerelle (route dynamique <code>/historique-service/**</code>) consulte sa copie de l'annuaire, choisit une instance de <code>HISTORIQUE-SERVICE</code>, retire le préfixe et transmet <code>POST /operations</code> (avec <code>X-Forwarded-*</code>).
3. <strong>historique-service</strong> (8082) appelle <code>calculClient.calculer("somme",1,2)</code> : OpenFeign / LoadBalancer choisit une instance de <code>CALCUL-SERVICE</code> dans sa copie de l'annuaire.
4. <code>GET /calcul/somme?a=1&amp;b=2</code> → <strong>calcul-service</strong> (8083), qui répond <code>3</code>.
5. <code>historique-service</code> enregistre l'opération et répond <code>201</code> + <code>Location</code>.
6. La réponse remonte : historique-service → passerelle → client.</p>
<p><strong>3. Trois programmes contiennent un répartiteur de charge : lesquels, et quel nom résout chacun ?</strong>
<strong>gateway-service</strong> (résout <code>CALCUL-SERVICE</code> et <code>HISTORIQUE-SERVICE</code>, et <code>GATEWAY-SERVICE</code> en dynamique), <strong>historique-service</strong> (résout <code>CALCUL-SERVICE</code> via Feign) et <strong>calcul-service</strong> (présent via le starter client Eureka, mais ne résout aucun nom : il n'appelle personne).</p>
<p><strong>4. Eureka client, OpenFeign et Spring Cloud Gateway : nécessaires où, et pourquoi ?</strong>
- <em>Client Eureka</em> : dans les trois (inscription ; découverte pour la passerelle et <code>historique-service</code>).
- <em>OpenFeign</em> : seulement dans <code>historique-service</code>, le seul à appeler un autre microservice.
- <em>Spring Cloud Gateway</em> : seulement dans <code>gateway-service</code>, le point d'entrée.</p>
<p><strong>5. Réponse complète à la question 5 de la synthèse du TP3 (adresse en dur et répartition de charge).</strong>
Une adresse écrite en dur désigne une seule instance : lancer d'autres exemplaires de <code>calcul-service</code> ne sert à rien, et un changement de port ou de machine oblige à modifier la configuration et à relancer. Dans ce TP, <strong>Eureka</strong> tient la liste à jour des instances, <strong>LoadBalancer</strong> les sollicite à tour de rôle, <strong>OpenFeign</strong> (<code>historique-service</code>) et <strong>Gateway</strong> (<code>lb://</code> ou routes dynamiques) appellent le service par son nom.</p>
<p><strong>6. Deux instances d'<code>historique-service</code> (8082 et 8084) derrière la passerelle : que renvoie <code>GET /historique-service/operations</code> après trois POST ? Quel principe est mis en défaut ?</strong>
Chaque instance a sa propre mémoire : le GET renvoie seulement les opérations traitées par l'instance interrogée (par exemple 2 sur 3, puis 1 sur 3 selon le tourniquet). Le principe mis en défaut est celui de <strong>service sans état (stateless)</strong>. Correction : une <strong>base de données partagée</strong> (MySQL, PostgreSQL via JPA) ou un cache distribué.</p>
<p><strong>7. Commandes pour construire et lancer sans IntelliJ les cinq programmes.</strong>
Depuis la racine : <code>mvn clean package -DskipTests</code>, puis, chacune dans son terminal et dans cet ordre :</p>
<pre><code>java -jar eureka-service/target/eureka-service-0.0.1-SNAPSHOT.jar
java -jar calcul-service/target/calcul-service-0.0.1-SNAPSHOT.jar
java -jar historique-service/target/historique-service-0.0.1-SNAPSHOT.jar
java -jar gateway-service/target/gateway-service-0.0.1-SNAPSHOT.jar
</code></pre>
<p><strong>8. Rôle de <code>@GetMapping("/calcul/{operateur}")</code> dans <code>CalculController</code> et dans <code>CalculClient</code>.</strong>
Dans <code>CalculController</code>, elle <strong>déclare</strong> une requête reçue (côté serveur). Dans <code>CalculClient</code>, elle <strong>décrit</strong> une requête envoyée (côté client) : OpenFeign génère l'implémentation qui l'émet.</p>
<p><strong>9. Le regroupement en modules remet-il en cause l'indépendance des microservices ?</strong>
Non : chaque module a <strong>sa propre JVM</strong>, <strong>son port</strong>, <strong>son .jar</strong> et <strong>son cycle de vie</strong> (lancement, arrêt, redéploiement indépendants). Le pom racine ne regroupe que la construction.</p>
<p><strong>10. D'où vient l'adresse de la zone <em>Servers</em> de Swagger UI de la passerelle ? Que se passerait-il sans <code>server.forward-headers-strategy=framework</code> ?</strong>
Elle vient des en-têtes <code>X-Forwarded-Host/Port/Prefix</code> ajoutés par la passerelle, lus par le microservice puis publiés par springdoc. Sans cette propriété, <em>Servers</em> contient l'adresse de l'instance (par exemple <code>http://192.168.115.1:8083</code>) : le navigateur contourne la passerelle et la requête échoue avec une erreur CORS.</p>
<p><strong>11. Où se trouvent les constructeurs, getters et setters générés par Lombok ? Pourquoi Lombok peut-il être exclu du <code>.jar</code> ?</strong>
Dans le <strong>bytecode</strong> <code>Operation.class</code> (visibles dans la fenêtre Structure ou le décompilateur), pas dans le code source. Lombok est inutile à l'exécution (le code est déjà généré) : il peut donc être exclu du <code>.jar</code>.</p></body></html>
