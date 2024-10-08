package me.lebogo.twitchwhitelist;


import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@SerializableAs("TwitchWhitelisting")
public record TwitchWhitelisting(String twitchUsername, String minecraftUsername, Date whitelistDate,
                                 boolean isJava) implements ConfigurationSerializable {

    public static TwitchWhitelisting deserialize(Map<String, Object> args) {
        String twitchUsername = "";
        String minecraftUsername = "";
        Date whitelistDate = new Date();
        boolean isJava = true;

        if (args.containsKey("twitchUsername")) twitchUsername = (String) args.get("twitchUsername");
        if (args.containsKey("minecraftUsername")) minecraftUsername = (String) args.get("minecraftUsername");
        if (args.containsKey("whitelistDate")) whitelistDate = (Date) args.get("whitelistDate");
        if (args.containsKey("isJava")) isJava = (boolean) args.get("isJava");

        return new TwitchWhitelisting(twitchUsername, minecraftUsername, whitelistDate, isJava);
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("twitchUsername", twitchUsername);
        result.put("minecraftUsername", minecraftUsername);
        result.put("whitelistDate", whitelistDate);
        result.put("isJava", isJava);

        return result;
    }

}
