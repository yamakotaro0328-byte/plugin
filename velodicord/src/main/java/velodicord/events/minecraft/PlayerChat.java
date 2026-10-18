package velodicord.events.minecraft;

import com.github.ucchyocean.lc3.japanize.Japanizer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import velodicord.Discordbot;
import velodicord.Velodicord;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;

/**
 * velodicord-bridgeのBridgeListener#onChat(MONITOR優先度)が「他の全プラグイン(ミュート、
 * GUI入力待ち、国/タウンチャットなど)がキャンセルしなかった、本物の公開チャットです」と
 * 報告してきたとき(PluginMessageManagerのCHATケース)だけ呼ばれる。つまりここに来た時点で
 * 何もフィルタせずそのままDiscord/他サーバーへ中継してよい。
 */
public final class PlayerChat {

    private static final OkHttpClient httpClient = new OkHttpClient();

    private PlayerChat() {
    }

    public static void relay(String server, String playerName, String rawMessage) {
        Optional<Player> maybePlayer = Velodicord.getVelodicord().getProxy().getPlayer(playerName);
        if (maybePlayer.isEmpty()) return;
        Player player = maybePlayer.get();

        String discord;
        String message = discord = rawMessage;
        String japanese = Japanizer.japanize(message);
        Component nameComponent = text("<%s> ".formatted(playerName));
        TextColor nameColor = luckPermsNameColor(player);
        if (nameColor != null) {
            nameComponent = nameComponent.color(nameColor);
        }
        TextComponent.Builder component = text()
                .append(text("[%s]".formatted(server), DARK_GREEN))
                .append(nameComponent);
        message = message.replaceAll("~~(.*?)~~", "<st>$1</st>")
                .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                .replaceAll("__(.*?)__", "<u>$1</u>")
                .replaceAll("_(.*?)_", "<i>$1</i>")
                .replaceAll("```(.*?)```", "$1")
                .replaceAll("\\|\\|(.*?)\\|\\|", "<ネタバレ>");

        if (Pattern.compile("\\[.*?]\\(https?://.*?\\)").matcher(message).find()) {
            message = message.replaceAll("\\[(.*?)]\\((https?://.*?)\\)", "<blue><u><click:open_url:'$2'>$1");
        } else if (Pattern.compile("https?://\\S+").matcher(message).find()) {
            message = message.replaceAll("(https?://\\S+)", "<blue><u><click:open_url:'$1'>$1");
        }

        if (message.contains("@")) {
            for (Member member : Discordbot.getMainChannel().getMembers()) {
                String usernameMention = "@%s".formatted(member.getUser().getName());
                String displayNameMention = "@%s".formatted(member.getEffectiveName());

                message = message.replace(usernameMention, "<blue>%s</blue>".formatted(usernameMention));
                message = message.replace(displayNameMention, "<blue>%s</blue>".formatted(displayNameMention));

                discord = StringUtils.replaceIgnoreCase(discord, displayNameMention, member.getAsMention());
                discord = StringUtils.replaceIgnoreCase(discord, usernameMention, member.getAsMention());


                if (member.getNickname() != null) {
                    String nicknameMention = "@%s".formatted(member.getNickname());
                    discord = StringUtils.replaceIgnoreCase(discord, nicknameMention, member.getAsMention());
                    message = message.replace(nicknameMention, "<blue>%s</blue>".formatted(nicknameMention));
                }
            }
            for (Role role : Discordbot.getMainChannel().getGuild().getRoles()) {
                String roleMention = "@%s".formatted(role.getName());
                discord = StringUtils.replaceIgnoreCase(discord, roleMention, role.getAsMention());
                message = message.replace(roleMention, "<blue>%s</blue>".formatted(roleMention));
            }
            message = message.replace("@everyone", "<blue>@everyone</blue>");
            message = message.replace("@here", "<blue>@here</blue>");
        }
        component.append(MiniMessage.miniMessage().deserialize(message));
        discord = "[%s] %s".formatted(server, discord);
        if (!japanese.isEmpty() && !rawMessage.contains("https://") && !rawMessage.contains("http://") && !rawMessage.contains("```")) {
            component.append(text("(%s)".formatted(japanese), GOLD));
            discord += "(%s)".formatted(japanese);
        }
        // 発言元のサーバーには通常のバニラチャットが既に流れているため、他サーバーにだけ同期する(二重表示防止)。
        Component finalComponent = component.build();
        for (RegisteredServer registeredServer : Velodicord.getVelodicord().getProxy().getAllServers()) {
            if (registeredServer.getServerInfo().getName().equals(server)) continue;
            for (Player recipient : registeredServer.getPlayersConnected()) {
                recipient.sendMessage(finalComponent);
            }
        }
        JsonObject body = new JsonObject();
        body.addProperty("content", discord);
        body.addProperty("username", playerName);
        body.addProperty("avatar_url", "https://mc-heads.net/avatar/%s.png".formatted(playerName));
        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new Gson().toJsonTree(Discordbot.getMentionable()).getAsJsonArray());
        body.add("allowed_mentions", allowedMentions);
        Request request = new Request.Builder()
                .url(Discordbot.getWebhook().getUrl())
                .post(RequestBody.create(MediaType.get("application/json"), body.toString()))
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.submit(() -> {
            try {
                Response response = httpClient.newCall(request).execute();
                response.close();
            } catch (Exception e) {
                Velodicord.getVelodicord().getLogger().error(ExceptionUtils.getStackTrace(e));
            }
        });
        executor.shutdown();
    }

    /** LuckPermsで"color"メタが設定されているプレイヤーだけ、その色を名前に適用する。
     * LuckPerms未導入時やメタ未設定時はnullを返し、呼び出し側は無色のままにする。 */
    private static TextColor luckPermsNameColor(Player player) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return null;
            return parseColor(user.getCachedData().getMetaData().getMetaValue("color"));
        } catch (IllegalStateException | NoClassDefFoundError e) {
            return null;
        }
    }

    private static TextColor parseColor(String value) {
        if (value == null || value.isBlank()) return null;
        value = value.trim();
        if (value.startsWith("#")) {
            return TextColor.fromHexString(value);
        }
        if (value.length() == 1) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize("&" + value + "x").color();
        }
        return NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT));
    }
}
