package org.pots.potsmod.models.ignore;

public record IgnorePlayerEntry(String playerName, IgnorePlayerMode mode, boolean ignored, boolean modeEditable) {
}
