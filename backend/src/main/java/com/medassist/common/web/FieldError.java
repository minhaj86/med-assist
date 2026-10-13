package com.medassist.common.web;

/** A single validation failure reported in a problem response. */
public record FieldError(String field, String message) {
}
