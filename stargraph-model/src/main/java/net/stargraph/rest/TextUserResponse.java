package net.stargraph.rest;

import net.stargraph.query.InteractionMode;

public class TextUserResponse extends UserResponse {
    public String getTextAnswer() {
        return textAnswer;
    }

    private final String textAnswer;

    public TextUserResponse(String query, InteractionMode interactionMode, String textAnswer) {
        super(query, interactionMode);
        this.textAnswer = textAnswer;
    }
}
