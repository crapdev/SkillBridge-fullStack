package com.riwi.skillbridge.domain.model;

/** Un mensaje de la conversación con el asistente de IA */
public record ChatMessage(Author author, String content) {

    public enum Author { USER, ASSISTANT }

    public static ChatMessage user(String content) {
        return new ChatMessage(Author.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Author.ASSISTANT, content);
    }
}
