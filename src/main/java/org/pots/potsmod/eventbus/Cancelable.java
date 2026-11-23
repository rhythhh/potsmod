package org.pots.potsmod.eventbus;

public interface Cancelable {
    default void cancel() {
        CancelableState.cancel(this);
    }
}
