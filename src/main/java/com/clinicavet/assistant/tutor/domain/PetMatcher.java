package com.clinicavet.assistant.tutor.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Correspondência de um pet citado com os pets <b>do tutor da conversa</b> (DA-35). Quem chama passa só a lista
 * desse tutor; esta classe não consulta nada.
 * <ul>
 *   <li>nome normalizado igual ({@link NameKey}) a exatamente um pet: {@link Result.Kind#FOUND}, salvo espécie
 *       informada diferente da cadastrada ({@link Result.Kind#SPECIES_CONFLICT});</li>
 *   <li>dois ou mais com o mesmo nome: a espécie informada desempata; senão {@link Result.Kind#AMBIGUOUS};</li>
 *   <li>nenhum: {@link Result.Kind#NONE}.</li>
 * </ul>
 * Em conflito, ambiguidade ou ausência, o pet não é vinculado: o assistente pergunta.
 */
public final class PetMatcher {

    public record Result(Kind kind, Optional<Pet> pet) {

        public enum Kind { FOUND, NONE, AMBIGUOUS, SPECIES_CONFLICT }

        static Result found(Pet pet) {
            return new Result(Kind.FOUND, Optional.of(pet));
        }

        static Result of(Kind kind) {
            return new Result(kind, Optional.empty());
        }
    }

    private PetMatcher() {
    }

    /** @param species espécie citada junto, ou {@code null} se não foi citada */
    public static Result match(List<Pet> tutorPets, String name, Species species) {
        Objects.requireNonNull(tutorPets, "tutorPets");
        List<Pet> sameName = tutorPets.stream().filter(p -> NameKey.same(p.name(), name)).toList();
        if (sameName.isEmpty()) {
            return Result.of(Result.Kind.NONE);
        }
        if (species == null) {
            return sameName.size() == 1 ? Result.found(sameName.getFirst()) : Result.of(Result.Kind.AMBIGUOUS);
        }
        List<Pet> sameSpecies = sameName.stream()
                .filter(p -> p.species().map(s -> s == species).orElse(false)).toList();
        if (sameSpecies.size() == 1) {
            return Result.found(sameSpecies.getFirst());
        }
        if (sameName.size() == 1) {
            // Um só com esse nome: sem espécie cadastrada, aceita; com espécie diferente, conflito.
            return sameName.getFirst().species().isEmpty()
                    ? Result.found(sameName.getFirst()) : Result.of(Result.Kind.SPECIES_CONFLICT);
        }
        return Result.of(Result.Kind.AMBIGUOUS);
    }
}
