package org.pots.potsmod.models.records;

public record QueuedTerritoryEntry(Territory territory, int distanceSquared, long timerEnd) {
}
