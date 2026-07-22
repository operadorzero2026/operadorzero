package br.com.operadorzero.identity;

public record AuthMailRequested(String recipient, String displayName, String kind, String token) {}
