package org.pots.potsmod.models.api;

public record TerritoryResponse(
        TerritoryLocation location,
        GuildRef guild,
        String acquired,
        boolean hq,
        String treasury,
        String defences
) {
    public record GuildRef(String uuid, String name, String prefix, String hq) {}
}
