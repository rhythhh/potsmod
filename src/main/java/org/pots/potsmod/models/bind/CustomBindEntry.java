package org.pots.potsmod.models.bind;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pots.potsmod.util.KeyUtil;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomBindEntry {
    private String id;
    private String name;
    private String command;
    private String key;

    public static CustomBindEntry createDefault(int index) {
        return new CustomBindEntry(
                UUID.randomUUID().toString(),
                "Custom Bind " + index,
                "guild attack",
                KeyUtil.UNBOUND
        );
    }
}
