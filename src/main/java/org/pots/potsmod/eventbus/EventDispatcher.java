package org.pots.potsmod.eventbus;

public interface EventDispatcher {
    void dispatch(Object event);

    void dispatch(Object event, CancelableCallback callback);
}
