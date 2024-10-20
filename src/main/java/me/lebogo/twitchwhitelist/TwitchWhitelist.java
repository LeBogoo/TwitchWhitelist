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
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.lebogo.twitchwhitelist.commands.RedemptionCommand;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TwitchWhitelist extends JavaPlugin {
    static {
        ConfigurationSerialization.registerClass(TwitchWhitelisting.class, "TwitchWhitelisting");
    }

    private final Logger logger = getLogger();
    private TwitchWhitelistConfig config;
    private WhitelistingStore whitelistingStore;
    private OAuth2Credential credential;
    private TwitchClient twitchClient;
    private TwitchPubSub pubSub;
    private TwitchHelix helix;
    private TwitchChat chat;
    private String channelId;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new TwitchWhitelistConfig(getConfig());

        String pluginDirectory = getDataFolder().getAbsolutePath();
        Path whitelistingPath = Path.of(pluginDirectory + "/whitelistings.yml");
        whitelistingStore = new WhitelistingStore(whitelistingPath);


        if ("<oauth:xxxxxxxxxxxxxxxx>".equals(config.getAccessToken())) {
            getLogger().log(Level.SEVERE, "Please set your access token in the plugins config.yml file and restart the server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        credential = new OAuth2Credential("twitch", config.getAccessToken());

        twitchClient = TwitchClientBuilder.builder().withDefaultAuthToken(credential).withEnablePubSub(true).withChatAccount(credential).withEnableChat(true).withEnableHelix(true).build();
        twitchClient.getPubSub().connect();

        helix = twitchClient.getHelix();
        chat = twitchClient.getChat();
        pubSub = twitchClient.getPubSub();

        String authToken = config.getAccessToken();
        channelId = getChannelId(helix, authToken, config.getChannelName());

        chat.joinChannel(config.getChannelName());

        cleanupCustomRewards();

        registerPubSubListeners();

        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register("redemption", new RedemptionCommand(whitelistingStore));
        });
    }

    private String getChannelId(TwitchHelix helix, String authToken, String channelName) {
        UserList userList = helix.getUsers(authToken, null, List.of(channelName)).execute();
        return userList.getUsers().getFirst().getId();
    }


    private void cleanupCustomRewards() {
        CustomRewardList customRewards = helix.getCustomRewards(config.getAccessToken(), channelId, null, true).execute();

        List<CustomReward> rewards = customRewards.getRewards();

        boolean javaPresent = false;
        boolean bedrockPresent = false;

        for (CustomReward reward : rewards) {
            if (reward.getId().equals(config.getJavaRewardId())) {
                javaPresent = true;
            } else if (reward.getId().equals(config.getBedrockRewardId())) {
                bedrockPresent = true;
            }
        }

        boolean regenerate = false;
        if (javaPresent && !config.getEnableJava()) {
            logger.log(Level.INFO, "Java reward is present but disabled.");
            regenerate = true;
        }

        if (bedrockPresent && !config.getEnableBedrock()) {
            logger.log(Level.INFO, "Bedrock reward is present but disabled.");
            regenerate = true;
        }

        if (!javaPresent && config.getEnableJava()) {
            logger.log(Level.INFO, "Java reward is missing.");
            regenerate = true;
        }

        if (!bedrockPresent && config.getEnableBedrock()) {
            logger.log(Level.INFO, "Bedrock reward is missing.");
            regenerate = true;
        }

        if (regenerate) {
            logger.log(Level.INFO, "(Re)generating custom rewards...");
            for (CustomReward reward : rewards) {
                logger.log(Level.INFO, "Deleting old reward: " + reward.getTitle());
                helix.deleteCustomReward(config.getAccessToken(), channelId, reward.getId()).execute();
            }

            createCustomRewards();
        } else {
            logger.log(Level.INFO, "Custom rewards already exist, reusing them.");
        }
    }

    private void createCustomRewards() {
        logger.log(Level.INFO, "Creating new reward for Java Edition...");


        config.setJavaRewardId("Disabled");
        config.setBedrockRewardId("Disabled");

        if (config.getEnableJava()) {
            CustomReward javaReward = helix.createCustomReward(config.getAccessToken(), channelId, CustomReward.builder().title("Minecraft Java Edition").cost(config.getRewardCost()).prompt("Please enter your Minecraft Java Edition username.").isUserInputRequired(true).build()).execute().getRewards().getFirst();
            config.setJavaRewardId(javaReward.getId());

            logger.log(Level.INFO, "Created reward: " + javaReward.getTitle());
        }

        if (config.getEnableBedrock()) {
            logger.log(Level.INFO, "Creating new reward for Bedrock Edition...");
            CustomReward bedrockReward = helix.createCustomReward(config.getAccessToken(), channelId, CustomReward.builder().title("Minecraft Bedrock Edition").cost(config.getRewardCost()).prompt("Please enter your Minecraft Bedrock Edition username.").isUserInputRequired(true).build()).execute().getRewards().getFirst();
            config.setBedrockRewardId(bedrockReward.getId());

            logger.log(Level.INFO, "Created reward: " + bedrockReward.getTitle());
        }

        saveConfig();
    }


    private void registerPubSubListeners() {
        logger.log(Level.INFO, "Registering PubSub listeners.");
        pubSub.listenForChannelPointsRedemptionEvents(credential, channelId);

        pubSub.getEventManager().onEvent(ChannelPointsRedemptionEvent.class, event -> {
            if (!Objects.equals(event.getRedemption().getStatus(), "UNFULFILLED")) {
                return;
            }


            String rewardId = event.getRedemption().getReward().getId();
            String username = event.getRedemption().getUserInput();
            String twitchUsername = event.getRedemption().getUser().getLogin();
            boolean isJava = rewardId.equals(config.getJavaRewardId());
            boolean isBedrock = rewardId.equals(config.getBedrockRewardId());

            if (!isJava && !isBedrock) {
                // this redemption is not for us. Ignore it.
                return;
            }

            logger.log(Level.INFO, "Redemption event: " + event.getRedemption().getReward().getTitle());


            for (TwitchWhitelisting whitelisting : whitelistingStore.getWhitelistings()) {
                // Check if the user already has a whitelist entry
                if (whitelisting.twitchUsername().equals(twitchUsername)) {
                    denyRedemption(event);
                    twitchClient.getChat().sendMessage(config.getChannelName(), "@" + event.getRedemption().getUser().getDisplayName() + " You have already redeemed a whitelisting.");
                    return;
                }
            }

            if (username == null) {
                denyRedemption(event);
                twitchClient.getChat().sendMessage(config.getChannelName(), "@" + event.getRedemption().getUser().getDisplayName() + " Please enter a username.");
                return;
            }

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

            if (isJava && config.getCheckJavaUsernames() && !doesJavaPlayerExist(username)) {
                denyRedemption(event);
                twitchClient.getChat().sendMessage(config.getChannelName(), "@" + event.getRedemption().getUser().getDisplayName() + " The username \"" + event.getRedemption().getUserInput() + "\" does not exist.");
                return;
            }

            fulfillRedemption(event);

            String finalWhitelistCommand = getWhitelistCommand(username, isJava, isBedrock);
            getServer().getScheduler().runTask(this, () -> getServer().dispatchCommand(getServer().getConsoleSender(), finalWhitelistCommand));

            String twitchMessage = isJava ? config.getJavaWhitelistSuccessfullMessage() : config.getBedrockWhitelistSuccessfullMessage();
            twitchMessage = twitchMessage.replace("{username}", username);

            whitelistingStore.addWhitelisting(new TwitchWhitelisting(twitchUsername, username, new Date(), isJava));
            whitelistingStore.save();

            twitchClient.getChat().sendMessage(config.getChannelName(), "@" + event.getRedemption().getUser().getDisplayName() + " " + twitchMessage);
        });
    }

    @NotNull
    private String getWhitelistCommand(String username, boolean isJava, boolean isBedrock) {
        String whitelistCommand = "say An error occurred while processing the whitelist command. Please contact the Plugin Developer.";
        if (isJava) {
            whitelistCommand = config.getJavaWhitelistCommand().replace("{username}", username);
        } else if (isBedrock) {
            whitelistCommand = config.getBedrockWhitelistCommand().replace("{username}", username);
        }

        // remove the leading slash if it exists
        if (whitelistCommand.startsWith("/")) {
            whitelistCommand = whitelistCommand.substring(1);
        }

        return whitelistCommand;
    }

    private void denyRedemption(ChannelPointsRedemptionEvent event) {
        helix.updateRedemptionStatus(config.getAccessToken(), channelId, event.getRedemption().getReward().getId(), List.of(event.getRedemption().getId()), RedemptionStatus.CANCELED).execute();
    }

    private void fulfillRedemption(ChannelPointsRedemptionEvent event) {
        helix.updateRedemptionStatus(config.getAccessToken(), channelId, event.getRedemption().getReward().getId(), List.of(event.getRedemption().getId()), RedemptionStatus.FULFILLED).execute();
    }

    private boolean isPlayerWhitelisted(String username) {
        return getServer().getWhitelistedPlayers().stream().anyMatch(player -> Objects.equals(player.getName(), username));
    }

    private boolean isPlayerBanned(String username) {
        return getServer().getBannedPlayers().stream().anyMatch(player -> Objects.equals(player.getName(), username));
    }

    public boolean doesJavaPlayerExist(String username) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void onDisable() {
        if (pubSub != null) pubSub.disconnect();
        if (chat != null) chat.leaveChannel(config.getChannelName());
        if (twitchClient != null) twitchClient.close();
    }
}
