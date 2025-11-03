// src/main/java/cp/corona/config/WarnLevel.java
package cp.corona.config;

import cp.corona.menus.items.MenuItem.ClickActionData;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class WarnLevel {

    private final String expiration;
    private final List<ClickActionData> onWarnActions;
    private final List<ClickActionData> onExpireActions;

    public WarnLevel(String expiration, List<String> onWarnActionStrings, List<String> onExpireActionStrings) {
        this.expiration = expiration;
        this.onWarnActions = parseActions(onWarnActionStrings);
        this.onExpireActions = parseActions(onExpireActionStrings);
    }

    private List<ClickActionData> parseActions(List<String> actionStrings) {
        if (actionStrings == null || actionStrings.isEmpty()) {
            return Collections.emptyList();
        }
        return actionStrings.stream()
                .map(ClickActionData::fromConfigString)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public String getExpiration() {
        return expiration;
    }

    public List<ClickActionData> getOnWarnActions() {
        return onWarnActions;
    }

    public List<ClickActionData> getOnExpireActions() {
        return onExpireActions;
    }
}