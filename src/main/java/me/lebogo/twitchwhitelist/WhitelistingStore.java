package me.lebogo.twitchwhitelist;

import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class WhitelistingStore {
    private final Path path;
    private final YamlConfiguration config;

    public WhitelistingStore(Path path) {
        this.path = path;
        this.config = new YamlConfiguration();

        // check if path exists, if not create it
        if (!path.toFile().exists()) {
            try {
                path.toFile().createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // load the config
        try {
            config.load(path.toFile());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // set default values
        if (!config.contains("whitelistings")) {
            config.set("whitelistings", new ArrayList<>());
        }

        save();
    }


    /**
     * Gets a list of TwitchWhitelisting objects from the config
     *
     * @return List of TwitchWhitelisting objects
     */
    public List<TwitchWhitelisting> getWhitelistings() {
        List<?> whitelistings = config.getList("whitelistings");
        if (whitelistings == null) return new ArrayList<>();
        return (List<TwitchWhitelisting>) whitelistings;
    }


    /**
     * Adds a TwitchWhitelisting object to the config
     *
     * @param twitchWhitelisting TwitchWhitelisting object to add
     */
    public void addWhitelisting(TwitchWhitelisting twitchWhitelisting) {
        List<TwitchWhitelisting> whitelistings = getWhitelistings();
        whitelistings.add(twitchWhitelisting);
        config.set("whitelistings", whitelistings);
    }

    /**
     * Removes a TwitchWhitelisting object from the config
     *
     * @param twitchWhitelisting TwitchWhitelisting object to remove
     */
    public void removeWhitelisting(TwitchWhitelisting twitchWhitelisting) {
        List<TwitchWhitelisting> whitelistings = getWhitelistings();
        whitelistings.remove(twitchWhitelisting);
        config.set("whitelistings", whitelistings);
    }

    public void save() {
        try {
            config.save(path.toFile());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
