package org.example.calculservice.model;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
// Lombok genere a la compilation :
// - un getter et un setter par attribut (@Getter, @Setter)
// - le constructeur sans argument (@NoArgsConstructor)
// - le constructeur avec tous les attributs (@AllArgsConstructor)
// - la methode toString (@ToString)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class Resultat {
    private String operation;
    private int a;
    private int b;
    private double resultat;
}