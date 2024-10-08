package me.lebogo.twitchwhitelist;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.eventsub.domain.RedemptionStatus;
import com.github.twitch4j.helix.TwitchHelix;
import com.github.twitch4j.helix.domain.CustomReward;
import com.github.twitch4j.helix.domain.CustomRewardList;
import com.github.twitch4j.helix.domain.UserList;
import com.github.twitch4j.pubsub.TwitchPubSub;
import com.github.twitch4j.pubsub.events.ChannelPointsRedemptionEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TwitchWhitelist extends JavaPlugin {
    private final Logger logger = getLogger();
    private TwitchWhitelistConfig config;
    private OAuth2Credential credential;
    private TwitchClient twitchClient;
    private TwitchPubSub pubSub;
    private TwitchHelix helix;
    private TwitchChat chat;
    private String channelId;
    private List<CustomReward> rewards;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new TwitchWhitelistConfig(getConfig());

        if ("<oauth2:xxxxxxxxxxxxxxxx>".equals(config.getAccessToken())) {
            getLogger().log(Level.SEVERE, "Please set your access token in the plugins config.yml file and restart the server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        credential = new OAuth2Credential("twitch", config.getAccessToken());

        twitchClient = TwitchClientBuilder.builder()
                .withDefaultAuthToken(credential)
                .withEnablePubSub(true)
                .withChatAccount(credential)
                .withEnableChat(true)
                .withEnableHelix(true)
                .build();
        twitchClient.getPubSub().connect();

        helix = twitchClient.getHelix();
        chat = twitchClient.getChat();
        pubSub = twitchClient.getPubSub();

        String authToken = config.getAccessToken();
        channelId = getChannelId(helix, authToken, config.getChannelName());

        chat.joinChannel(config.getChannelName());

        cleanupCustomRewards();

        registerPubSubListeners();
    }

    private String getChannelId(TwitchHelix helix, String authToken, String channelName) {
        UserList userList = helix.getUsers(authToken, null, List.of(channelName)).execute();
        return userList.getUsers().get(0).getId();
    }


    private void cleanupCustomRewards() {
        CustomRewardList customRewards = helix.getCustomRewards(config.getAccessToken(), channelId, null, true).execute();

        rewards = customRewards.getRewards();

        boolean javaPresent = false;
        boolean bedrockPresent = false;

        for (CustomReward reward : rewards) {
            if (reward.getId().equals(config.getJavaRewardId())) {
                javaPresent = true;
            } else if (reward.getId().equals(config.getBedrockRewardId())) {
                bedrockPresent = true;
            }
        }

        if ((!javaPresent && config.getJavaToggle()) || (!bedrockPresent && config.getBedrockToggle())) {
            logger.log(Level.INFO, "Missing custom rewards, creating new ones.");
            for (CustomReward reward : rewards) {
                logger.log(Level.INFO, "Deleting old reward: " + reward.getTitle());
                helix.deleteCustomReward(config.getAccessToken(), channelId, reward.getId()).execute();
            }

            rewards = createCustomRewards();
        } else {
            logger.log(Level.INFO, "Custom rewards already exist, reusing them.");
        }
    }

    private List<CustomReward> createCustomRewards() {
        logger.log(Level.INFO, "Creating new reward for Java Edition...");

        List<CustomReward> rewards = new ArrayList<>();

        if (config.getJavaToggle()) {
            CustomReward javaReward = helix.createCustomReward(config.getAccessToken(), channelId, CustomReward.builder()
                    .title("Minecraft Java Edition")
                    .cost(config.getRewardCost())
                    .prompt("Please enter your Minecraft Java Edition username.")
                    .isUserInputRequired(true)
                    .build()
            ).execute().getRewards().get(0);
            config.setJavaRewardId(javaReward.getId());

            rewards.add(javaReward);
            logger.log(Level.INFO, "Created reward: " + javaReward.getTitle());


        }

        if (config.getBedrockToggle()) {
            logger.log(Level.INFO, "Creating new reward for Bedrock Edition...");
            CustomReward bedrockReward = helix.createCustomReward(config.getAccessToken(), channelId, CustomReward.builder()
                    .title("Minecraft Bedrock Edition")
                    .cost(config.getRewardCost())
                    .prompt("Please enter your Minecraft Bedrock Edition username.")
                    .isUserInputRequired(true)
                    .build()
            ).execute().getRewards().get(0);
            config.setBedrockRewardId(bedrockReward.getId());

            rewards.add(bedrockReward);
            logger.log(Level.INFO, "Created reward: " + bedrockReward.getTitle());
        }

        saveConfig();

        return rewards;
    }

    private void registerPubSubListeners() {
        logger.log(Level.INFO, "Registering PubSub listeners.");
        pubSub.listenForChannelPointsRedemptionEvents(credential, channelId);

        pubSub.getEventManager().onEvent(ChannelPointsRedemptionEvent.class, event -> {
            if (!Objects.equals(event.getRedemption().getStatus(), "UNFULFILLED")) {
                return;
            }

            String rewardId = event.getRedemption().getReward().getId();
            logger.log(Level.INFO, "Redemption event: " + event.getRedemption().getReward().getTitle());

            String username = null;
            if (rewardId.equals(config.getJavaRewardId())) {
                username = event.getRedemption().getUserInput();
            } else if (rewardId.equals(config.getBedrockRewardId())) {
                username = config.getBedrockPrefix() + event.getRedemption().getUserInput();
            }

            if (username == null) return;

            if (isPlayerWhitelisted(username)) {
                denyRedemption(event);
                twitchClient.getChat().sendMessage(config.getChannelName(), "@" + event.getRedemption().getUser().getDisplayName() + " You are already whitelisted.");
                return;
            }

            if (isPlayerBanned(username)) {
                denyRedemption(event);
                twitchClient.getChat().sendMessage(config.getChannelName(), "@" + event.getRedemption().getUser().getDisplayName() + " You are banned from the server.");
                return;
            }

            fulfillRedemption(event);
            whitelistPlayer(username);

            twitchClient.getChat().sendMessage(config.getChannelName(), "@" + event.getRedemption().getUser().getDisplayName() + " Your username \"" + event.getRedemption().getUserInput() + "\" has been whitelisted. You can now join the server!");
        });
    }

    private void denyRedemption(ChannelPointsRedemptionEvent event) {
        helix.updateRedemptionStatus(config.getAccessToken(), channelId, event.getRedemption().getReward().getId(), List.of(event.getRedemption().getId()), RedemptionStatus.CANCELED).execute();
    }

    private void fulfillRedemption(ChannelPointsRedemptionEvent event) {
        helix.updateRedemptionStatus(config.getAccessToken(), channelId, event.getRedemption().getReward().getId(), List.of(event.getRedemption().getId()), RedemptionStatus.FULFILLED).execute();
    }

    private boolean isPlayerWhitelisted(String username) {
        return getServer().getWhitelistedPlayers().stream().anyMatch(player -> player.getName().equals(username));
    }

    private boolean isPlayerBanned(String username) {
        return getServer().getBannedPlayers().stream().anyMatch(player -> player.getName().equals(username));
    }

    private void whitelistPlayer(String username) {
        getServer().getScheduler().runTask(this, () -> getServer().dispatchCommand(getServer().getConsoleSender(), "whitelist add " + username));
    }

    @Override
    public void onDisable() {
        if (pubSub != null) pubSub.disconnect();
        if (chat != null) chat.leaveChannel(config.getChannelName());
        if (twitchClient != null) twitchClient.close();
    }
}
