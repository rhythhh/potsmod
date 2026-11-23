package org.pots.potsmod.models.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.pots.potsmod.eventbus.Cancelable;

@Getter
@Setter
@AllArgsConstructor
public class CommandSentEvent implements Cancelable {
    private String command;

    public String command() {
        return command;
    }
}
