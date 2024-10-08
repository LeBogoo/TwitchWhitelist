package me.lebogo.twitchwhitelist;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class TwitchWhitelistConfig {
    private final FileConfiguration config;

    public TwitchWhitelistConfig(FileConfiguration config) {
        this.config = config;
    }

    // ----------------- Twitch -----------------

    public String getAccessToken() {
        String accessToken = config.getString("accessToken");
        if (accessToken != null && accessToken.startsWith("oauth2:")) accessToken = accessToken.substring(7);
        if (accessToken.startsWith("oauth:")) accessToken = accessToken.substring(6);
        return accessToken;
    }

    public String getChannelName() {
        return config.getString("channelName");
    }

    public int getRewardCost() {
        return config.getInt("rewardCost");
    }

    // ----------------- Java -----------------

    public boolean getEnableJava() {
        return config.getBoolean("enableJava");
    }

    public boolean getCheckJavaUsernames() {
        return config.getBoolean("checkJavaUsernames");
    }

    public String getJavaRewardId() {
        return config.getString("javaRewardId");
    }

    public void setJavaRewardId(String javaRewardId) {
        config.set("javaRewardId", javaRewardId);
    }

    public String getJavaWhitelistCommand() {
        return config.getString("javaWhitelistCommand");
    }

    public String getJavaWhitelistSuccessfullMessage() {
        return config.getString("javaWhitelistSuccessfullMessage");
    }

    // ----------------- Bedrock -----------------

    public boolean getEnableBedrock() {
        return config.getBoolean("enableBedrock");
    }

    public String getBedrockRewardId() {
        return config.getString("bedrockRewardId");
    }

    public void setBedrockRewardId(String bedrockRewardId) {
        config.set("bedrockRewardId", bedrockRewardId);
    }

    public String getBedrockWhitelistCommand() {
        return config.getString("bedrockWhitelistCommand");
    }

    public String getBedrockWhitelistSuccessfullMessage() {
        return config.getString("bedrockWhitelistSuccessfullMessage");
    }


    /**
     * Gets a list of Twitch Usernames that already redeemed any whitelist request
     *
     * @return List of Twitch Usernames
     */
    public List<String> getRedemptions() {
        return config.getStringList("redemptions");
    }


    /**
     * Adds a Twitch Username to the list of redemptions
     *
     * @param username Twitch Username
     */
    public void addRedemption(String username) {
        List<String> redemptions = getRedemptions();
        redemptions.add(username);
        config.set("redemptions", redemptions);
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
