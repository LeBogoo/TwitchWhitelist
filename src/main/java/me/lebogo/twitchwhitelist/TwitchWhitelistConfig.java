package me.lebogo.twitchwhitelist;

import org.bukkit.configuration.file.FileConfiguration;

public class TwitchWhitelistConfig {
    private final FileConfiguration config;

    public TwitchWhitelistConfig(FileConfiguration config) {
        this.config = config;
    }

    public String getChannelName() {
        return config.getString("channelName");
    }

    public String getAccessToken() {
        String accessToken = config.getString("accessToken");
        if (accessToken != null && accessToken.startsWith("oauth2:")) accessToken = accessToken.substring(7);
        if (accessToken.startsWith("oauth:")) accessToken = accessToken.substring(6);
        return accessToken;
    }

    public String getJavaRewardId() {
        return config.getString("javaRewardId");
    }

    public String getBedrockRewardId() {
        return config.getString("bedrockRewardId");
    }

    public void setJavaRewardId(String javaRewardId) {
        config.set("javaRewardId", javaRewardId);
    }

    public void setBedrockRewardId(String bedrockRewardId) {
        config.set("bedrockRewardId", bedrockRewardId);
    }

    public String getBedrockPrefix() {
        return config.getString("bedrockPrefix");
    }

    public boolean getJavaToggle() {
        return config.getBoolean("enableJava");
    }
    public boolean getBedrockToggle() {
        return config.getBoolean("enableBedrock");
    }

    public int getRewardCost() {
        return config.getInt("rewardCost");
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
