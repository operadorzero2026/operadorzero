package br.com.operadorzero.media;

import java.util.UUID;

/** Publicado na mesma transacao que marca a midia como pronta ou com falha (posts e Recons reagem a ele). */
public record MediaProcessedEvent(long assetId, UUID assetPublicId, long ownerUserId, String purpose, boolean ready, String errorCode) {}
