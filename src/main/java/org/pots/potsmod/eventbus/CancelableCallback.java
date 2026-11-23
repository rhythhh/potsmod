package org.pots.potsmod.eventbus;

@FunctionalInterface
public interface CancelableCallback {
    void canceled(Object event);
}
