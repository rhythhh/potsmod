package org.pots.potsmod.functions;

import com.wynntils.core.consumers.functions.Function;
import net.minecraft.client.resources.language.I18n;

import java.util.Locale;

public abstract class RFunction<T> extends Function<T> {

    @Override
    public String getTranslation(String keySuffix, Object... parameters) {
        return I18n.get(
                getTypeName().toLowerCase(Locale.ROOT) + ".rmod." + getTranslationKeyName() + "." + keySuffix,
                parameters);
    }
}
