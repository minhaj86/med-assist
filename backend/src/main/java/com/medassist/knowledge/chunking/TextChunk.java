package com.medassist.knowledge.chunking;

public record TextChunk(int index, String section, Integer pageNumber, String content) {

    public int tokenEstimate() {
        return Math.max(1, content.length() / 4);
    }
}
