package velodicord.events.minecraft;

import com.github.ucchyocean.lc3.japanize.Japanizer;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
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
import org.apache.commons.lang3.StringUtils;
import velodicord.ChatFormat;
import velodicord.DiscordWebhook;
import velodicord.Discordbot;
import velodicord.Velodicord;
import velodicord.pmConnection.WebSocketPluginMessageManager;

import java.util.Locale;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;

/**
 * マイクラのチャットをDiscordと他サーバーへ中継する。
 * 通常はvelodicord-bridgeが、他の全プラグイン(ミュート、GUI入力待ち、国/タウンチャットなど)の判定が
 * 終わった後に「本物の公開チャット」だけをCHATで報告してくるので、それを受けてrelay()する。
 * velodicord-bridgeが繋がっていないサーバー(ロビー等)の発言は、従来通りプロキシの
 * PlayerChatEventから中継する(onPlayerChat)。こうしてブリッジの有無に関わらずチャットが消えないようにする。
 */
public class PlayerChat {

    /** ブリッジ未接続サーバー用のフォールバック。LASTなので、プロキシ側のミュート等で拒否された発言は中継しない。 */
    @Subscribe(order = PostOrder.LAST)
    public void onPlayerChat(PlayerChatEvent event) {
        if (!event.getResult().isAllowed()) return;
        Player player = event.getPlayer();
        String server = player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse(null);
        if (server == null) return;
        if (Velodicord.getPMManager() instanceof WebSocketPluginMessageManager manager && manager.isBridgeConnected(server)) {
            return;
        }
        relay(server, player, event.getResult().getMessage().orElse(event.getMessage()));
    }

    /** velodicord-bridgeからのCHAT報告(PluginMessageManager)から呼ばれる。 */
    public static void relay(String server, String playerName, String rawMessage) {
        Velodicord.getVelodicord().getProxy().getPlayer(playerName)
                .ifPresent(player -> relay(server, player, rawMessage));
    }

    private static void relay(String server, Player player, String rawMessage) {
        String playerName = player.getUsername();
        String discord = rawMessage;
        String message = ChatFormat.toMiniMessage(rawMessage);
        String japanese = Japanizer.japanize(rawMessage);

        Component nameComponent = text("<%s> ".formatted(playerName));
        TextColor nameColor = luckPermsNameColor(player);
        if (nameColor != null) {
            nameComponent = nameComponent.color(nameColor);
        }
        TextComponent.Builder component = text()
                .append(text("[%s]".formatted(server), DARK_GREEN))
                .append(nameComponent);

        if (rawMessage.contains("@")) {
            for (Member member : Discordbot.getMainChannel().getMembers()) {
                String usernameMention = "@%s".formatted(member.getUser().getName());
                String displayNameMention = "@%s".formatted(member.getEffectiveName());

                message = ChatFormat.highlight(message, usernameMention);
                message = ChatFormat.highlight(message, displayNameMention);
                discord = StringUtils.replaceIgnoreCase(discord, displayNameMention, member.getAsMention());
                discord = StringUtils.replaceIgnoreCase(discord, usernameMention, member.getAsMention());

                if (member.getNickname() != null) {
                    String nicknameMention = "@%s".formatted(member.getNickname());
                    discord = StringUtils.replaceIgnoreCase(discord, nicknameMention, member.getAsMention());
                    message = ChatFormat.highlight(message, nicknameMention);
                }
            }
            for (Role role : Discordbot.getMainChannel().getGuild().getRoles()) {
                String roleMention = "@%s".formatted(role.getName());
                discord = StringUtils.replaceIgnoreCase(discord, roleMention, role.getAsMention());
                message = ChatFormat.highlight(message, roleMention);
            }
            message = ChatFormat.highlight(message, "@everyone");
            message = ChatFormat.highlight(message, "@here");
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
        DiscordWebhook.send(playerName, discord);
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
