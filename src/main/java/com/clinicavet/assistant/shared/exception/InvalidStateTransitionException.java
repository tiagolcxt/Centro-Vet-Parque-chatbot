package com.clinicavet.assistant.shared.exception;

/** Transição de estado não permitida pelo ciclo de vida do agregado. Mapeada para HTTP 409. */
public class InvalidStateTransitionException extends DomainException {

    private final String aggregate;
    private final String from;
    private final String action;

    public InvalidStateTransitionException(String aggregate, Enum<?> from, String action) {
        super(humanMessage(aggregate, from.name(), action));
        this.aggregate = aggregate;
        this.from = from.name();
        this.action = action;
    }

    /**
     * Ex.: "Não é possível fazer isso agora: a conversa está encerrada." Se a ação traz uma orientação entre parênteses
     * ("responder (assuma a conversa antes)"), ela vira a segunda frase. O estado interno fica em {@link #from()}.
     */
    private static String humanMessage(String aggregate, String from, String action) {
        String subject = aggregate == null || aggregate.isBlank() ? "o registro" : article(aggregate) + " "
                + aggregate.toLowerCase(java.util.Locale.ROOT);
        String message = "Não é possível fazer isso agora: " + subject + " está " + UserText.state(from) + ".";
        if (action != null) {
            int open = action.indexOf('(');
            int close = action.lastIndexOf(')');
            if (open >= 0 && close > open + 1) {
                String hint = action.substring(open + 1, close).strip();
                message += " " + Character.toUpperCase(hint.charAt(0)) + hint.substring(1) + ".";
            }
        }
        return message;
    }

    private static String article(String aggregate) {
        String lower = aggregate.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith("a") || lower.endsWith("ção") || lower.endsWith("ão") && !lower.endsWith("rio") ? "a" : "o";
    }

    public String aggregate() {
        return aggregate;
    }

    public String from() {
        return from;
    }

    public String action() {
        return action;
    }
}
