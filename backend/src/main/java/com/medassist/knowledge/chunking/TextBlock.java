package com.medassist.knowledge.chunking;

/** A paragraph of cleaned text with the section heading and page it belongs to. */
public record TextBlock(String section, Integer pageNumber, String text) {
}
