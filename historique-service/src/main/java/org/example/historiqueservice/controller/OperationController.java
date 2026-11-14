package org.example.historiqueservice.controller;

import feign.FeignException;
import org.example.historiqueservice.client.CalculClient;
import org.example.historiqueservice.model.Operation;
import org.example.historiqueservice.service.OperationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/operations")
public class OperationController {

    private static final List<String> OPERATEURS =
            List.of("somme", "soustraction", "produit", "division");

    private final OperationService operationService;
    private final CalculClient calculClient;

    public OperationController(OperationService operationService,
                               CalculClient calculClient) {
        this.operationService = operationService;
        this.calculClient = calculClient;
    }

    // Demande le résultat à CALCUL-SERVICE (instance choisie par LoadBalancer)
    // et le range dans l'opération. Retourne une réponse d'erreur si le calcul
    // est impossible, null sinon.
    private ResponseEntity<Object> calculer(Operation op) {
        if (op.getOperateur() == null || !OPERATEURS.contains(op.getOperateur())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erreur", "Operateur inconnu : " + op.getOperateur()));
        }
        try {
            String corps = calculClient.calculer(op.getOperateur(), op.getA(), op.getB());
            op.setResultat(Double.parseDouble(corps));
            return null;
        } catch (FeignException.BadRequest e) {
            // calcul-service a répondu 400 (division par 0) : on transmet son message
            return ResponseEntity.badRequest()
                    .body(Map.of("erreur", e.contentUTF8()));
        } catch (FeignException e) {
            // Aucune instance disponible ou service injoignable
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("erreur", "calcul-service injoignable"));
        }
    }

    @PostMapping
    public ResponseEntity<Object> creer(@RequestBody Operation op) {
        ResponseEntity<Object> erreur = calculer(op);
        if (erreur != null) return erreur;
        Operation cree = operationService.ajouter(op);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(cree.getId()).toUri();
        return ResponseEntity.created(location).body(cree);
    }

    @GetMapping
    public List<Operation> lister() {
        return operationService.lister();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Operation> lire(@PathVariable("id") int id) {
        Operation op = operationService.lire(id);
        return op == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(op);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> modifier(@PathVariable("id") int id,
                                           @RequestBody Operation op) {
        if (!operationService.existe(id)) return ResponseEntity.notFound().build();
        ResponseEntity<Object> erreur = calculer(op);
        if (erreur != null) return erreur;
        operationService.remplacer(id, op);
        return ResponseEntity.ok(op);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable("id") int id) {
        return operationService.supprimer(id) == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.noContent().build();
    }

}