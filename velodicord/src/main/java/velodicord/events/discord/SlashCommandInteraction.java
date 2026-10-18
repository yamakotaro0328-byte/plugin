package velodicord.events.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jetbrains.annotations.NotNull;
import velodicord.*;
import velodicord.link.LinkManager;
import velodicord.pmConnection.DiscordPluginMessageManager;

import java.awt.*;
import java.util.*;
import java.util.stream.Collectors;

import static velodicord.Config.*;
import static velodicord.Discordbot.*;

public class SlashCommandInteraction extends ListenerAdapter {

    /** 設定(disadmincommand.json)に関係なく、常にコマンドロールが必要なコマンド。
     * 特にserver commandはプロキシのコンソール権限でコマンドを実行するので、誰でも使えると乗っ取られる。 */
    private static final Set<String> ALWAYS_ADMIN = Set.of(
            "dic add", "dic del", "ch set", "ch del_log", "commandrole set",
            "detectbot add", "detectbot del", "mentionable set", "server command",
            "admincommand add", "admincommand del", "linkpanel");

    /** 個人用のコマンドなので、コマンドチャンネル以外でも使える。 */
    private static final Set<String> ANY_CHANNEL = Set.of("link", "unlink", "online", "linkpanel");

    private static final int EMBED_DESCRIPTION_LIMIT = 4000;

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String path = event.getFullCommandName();

        if (!ANY_CHANNEL.contains(event.getName()) && !getCommandChannel().equals(event.getChannelId())) {
            event.replyEmbeds(new EmbedBuilder()
                    .setColor(Color.red)
                    .setTitle("このコマンドはコマンドチャンネルでのみ使えます")
                    .build()
            ).setEphemeral(true).queue();
            return;
        }
        boolean needsAdmin = ALWAYS_ADMIN.contains(path) || getDisadmincommand().stream().anyMatch(path::startsWith);
        if (needsAdmin && (event.getMember() == null || !event.getMember().getRoles().contains(getCommandRole()))) {
            event.replyEmbeds(new EmbedBuilder()
                    .setColor(Color.red)
                    .setTitle("このコマンドを実行するのに必要な権限がありません")
                    .build()
            ).setEphemeral(true).queue();
            return;
        }

        try {
            handle(event);
            if (needsAdmin) Config.saveAll();
        } catch (IllegalStateException | IllegalArgumentException e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (event.isAcknowledged()) {
                event.getHook().sendMessage("エラー: " + reason).setEphemeral(true).queue();
            } else {
                event.replyEmbeds(new EmbedBuilder()
                        .setColor(Color.red)
                        .setTitle("実行できませんでした")
                        .setDescription("チャンネルの種類が違うなど、入力内容を確認してください\n" + reason)
                        .build()
                ).setEphemeral(true).queue();
            }
        }
    }

    private static String fit(CharSequence text) {
        return text.length() <= EMBED_DESCRIPTION_LIMIT ? text.toString() : text.subSequence(0, EMBED_DESCRIPTION_LIMIT) + "\n…(省略)";
    }

    private void handle(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "dic" -> {
                switch (Objects.requireNonNull(event.getSubcommandName())) {
                    case "show" -> {
                        StringBuilder builder = new StringBuilder();
                        getDic().keySet().forEach(word -> builder.append("・ ").append(word).append(" -> ").append(getDic().get(word)).append("\n"));
                        event.replyEmbeds(new EmbedBuilder()
                                .setTitle("辞書に登録されている単語一覧")
                                .setDescription(fit(builder))
                                .setColor(Color.blue)
                                .build()
                        ).setEphemeral(true).queue();
                    }

                    case "add" -> {
                        String word = event.getOptions().get(0).getAsString();
                        String read = event.getOptions().get(1).getAsString();
                        getDic().put(word, read);
                        setDic(getDic().entrySet().stream()
                                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(String::length).reversed()))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                        (oldValue, newValue) -> oldValue, LinkedHashMap::new)));
                        event.replyEmbeds(new EmbedBuilder()
                                .setTitle("単語を登録・変更しました")
                                .setDescription("%s -> %s".formatted(word, read))
                                .setColor(Color.blue)
                                .build()
                        ).queue();
                    }

                    case "del" -> {
                        String word = event.getOptions().get(0).getAsString();
                        getDic().remove(word);
                        event.replyEmbeds(new EmbedBuilder()
                                .setTitle("単語を削除しました")
                                .setDescription(word)
                                .setColor(Color.blue)
                                .build()
                        ).queue();
                    }
                }
            }

            case "ch" -> {
                switch (Objects.requireNonNull(event.getSubcommandName())) {
                    case "show" -> {
                        StringBuilder builder = new StringBuilder();
                        builder.append("ログチャンネル -> ").append(getLogForumChannel().map(ForumChannel::getName).orElse("<<設定されていません>>")).append("(").append(getLogForumChannel().map(ForumChannel::getId).orElse("<<設定されていません>>")).append(")\n")
                                .append("メインチャンネル -> ").append(getMainChannel().getName()).append("(").append(getMainChannel().getId()).append(")\n")
                                .append("通知チャンネル 　   -> ").append(getNoticeChannel().getName()).append("(").append(getNoticeChannel().getId()).append(")\n")
                                .append("POSチャンネル 　   -> ").append(getPosChannel().getName()).append("(").append(getPosChannel().getId()).append(")\n")
                                .append("コマンドチャンネル 　-> ").append(Objects.requireNonNull(getJda().getTextChannelById(getCommandChannel())).getName()).append("(").append(getCommandChannel()).append(")\n");

                        if (Velodicord.getPMManager() instanceof DiscordPluginMessageManager manager)
                            builder.append("PMチャンネル -> ").append(manager.getPMChannel().getName()).append("(").append(manager.getPMChannel().getId()).append(")");

                        event.replyEmbeds(new EmbedBuilder()
                                .setTitle("設定されているチャンネル")
                                .setDescription(
                                        builder.toString()
                                )
                                .setColor(Color.blue)
                                .build()
                        ).setEphemeral(true).queue();
                    }

                    case "set" -> {
                        switch (event.getOptions().get(0).getAsString()) {
                            case "log" -> {
                                setLogForumChannel(Optional.of(event.getOptions().get(1).getAsChannel().asForumChannel()));
                                getConfig().put("LogChannelID", getLogForumChannel().get().getId());
                                event.replyEmbeds(new EmbedBuilder()
                                        .setTitle("ログチャンネルを%s(%s)に設定しました".formatted(getLogForumChannel().get().getName(), getLogForumChannel().get().getId()))
                                        .setColor(Color.blue)
                                        .build()
                                ).queue();

                                getLogForumChannel().get().getThreadChannels()
                                        .stream().filter(thread -> "velocity".equals(thread.getName())).findFirst().ifPresentOrElse(
                                                Discordbot::setLogChannel,

                                                () -> setLogChannel(getLogForumChannel().get()
                                                        .createForumPost("velocity", MessageCreateData.fromContent("velocity server's log")).complete().getThreadChannel())
                                        );
                                if (getLog() == null) {
                                    setLog(new Thread(new Log(true)));
                                    getLog().start();
                                } else if (!getLog().isAlive()) {
                                    setLog(new Thread(new Log(false)));
                                    getLog().start();
                                }
                            }

                            case "main" -> {
                                String lm = getMainChannel().getId();
                                setMainChannel(event.getOptions().get(1).getAsChannel().asTextChannel());
                                getConfig().put("MainChannelID", getMainChannel().getId());
                                if (lm.equals(getNoticeChannel().getId())) {
                                    setNoticeChannel(getMainChannel());
                                    getConfig().put("NoticeChannelID", getNoticeChannel().getId());
                                }
                                if (lm.equals(getPosChannel().getId())) {
                                    setPosChannel(getMainChannel());
                                    getConfig().put("PosChannelID", getPosChannel().getId());
                                }
                                if (lm.equals(getCommandChannel())) {
                                    setCommandChannel(getMainChannel().getId());
                                    getConfig().put("CommandChannelID", getCommandChannel());
                                }
                                if (Velodicord.getPMManager() instanceof DiscordPluginMessageManager manager && lm.equals(manager.getPMChannel().getId())) {
                                    manager.setPMChannel(getMainChannel());
                                    getConfig().put("PMChannelID", getMainChannel().getId());
                                }

                                // 以前はギルド全体から"Velodicord"のwebhookを探していたため、古いメインチャンネルの
                                // webhookを拾ってしまい、チャットが旧チャンネルに投稿され続けていた。新しいチャンネルの中だけを探す。
                                String webhookname = "Velodicord";
                                setWebhook(getMainChannel().retrieveWebhooks().complete().stream()
                                        .filter(webhook -> webhookname.equals(webhook.getName()))
                                        .findFirst()
                                        .orElseGet(() -> getMainChannel().createWebhook(webhookname).complete()));

                                event.replyEmbeds(new EmbedBuilder()
                                        .setTitle("メインチャンネルを%s(%s)に設定しました".formatted(getMainChannel().getName(), getMainChannel().getId()))
                                        .setColor(Color.blue)
                                        .build()
                                ).queue();

                                Velodicord.getPMManager().sendMessage("ALL", "OK&%s&%s&%s&%s".formatted(getNoticeChannel().getId(), getLogForumChannel().map(ForumChannel::getId).orElse(""), getCommandChannel(), getCommandRole().getId()));
                            }

                            case "pm" -> {
                                if (Velodicord.getPMManager() instanceof DiscordPluginMessageManager manager) {
                                    TextChannel pmtmp = event.getOptions().get(1).getAsChannel().asTextChannel();

                                    manager.setPMChannel(pmtmp);
                                    getConfig().put("PMChannelID", pmtmp.getId());
                                    event.replyEmbeds(new EmbedBuilder()
                                            .setTitle("PMチャンネルを%s(%s)に設定しました".formatted(pmtmp.getName(), pmtmp.getId()))
                                            .setColor(Color.blue)
                                            .build()
                                    ).queue();
                                } else {
                                    event.replyEmbeds(new EmbedBuilder()
                                            .setTitle("PMチャンネルはWebSocket版では設定できません")
                                            .setColor(Color.red)
                                            .build()
                                    ).queue();
                                }
                            }

                            case "notice" -> {
                                setNoticeChannel(event.getOptions().get(1).getAsChannel().asTextChannel());
                                getConfig().put("NoticeChannelID", getNoticeChannel().getId());
                                event.replyEmbeds(new EmbedBuilder()
                                        .setTitle("通知チャンネルを%s(%s)に設定しました".formatted(getNoticeChannel().getName(), getNoticeChannel().getId()))
                                        .setColor(Color.blue)
                                        .build()
                                ).queue();
                            }

                            case "pos" -> {
                                setPosChannel(event.getOptions().get(1).getAsChannel().asTextChannel());
                                getConfig().put("PosChannelID", getPosChannel().getId());
                                event.replyEmbeds(new EmbedBuilder()
                                        .setTitle("POSチャンネルを%s(%s)に設定しました".formatted(getPosChannel().getName(), getPosChannel().getId()))
                                        .setColor(Color.blue)
                                        .build()
                                ).queue();
                            }

                            case "command" -> {
                                setCommandChannel(event.getOptions().get(1).getAsChannel().asTextChannel().getId());
                                getConfig().put("CommandChannelID", getCommandChannel());
                                event.replyEmbeds(new EmbedBuilder()
                                        .setTitle("コマンドチャンネルを%s(%s)に設定しました".formatted(event.getOptions().get(1).getAsChannel().asTextChannel().getName(), getCommandChannel()))
                                        .setColor(Color.blue)
                                        .build()
                                ).queue();
                            }
                        }
                    }

                    case "del_log" -> {
                        if (getLog() != null) getLog().interrupt();
                        setLogForumChannel(Optional.empty());
                        getConfig().put("LogChannelID", "000000");
                        event.replyEmbeds(new EmbedBuilder()
                                .setTitle("ログチャンネルを削除しました")
                                .setColor(Color.red)
                                .build()
                        ).queue();
                    }
                }
            }

            case "commandrole" -> {
                switch (Objects.requireNonNull(event.getSubcommandName())) {
                    case "show" -> event.replyEmbeds(new EmbedBuilder()
                            .setTitle("設定されているロール")
                            .setDescription("%s(%s)".formatted(getCommandRole().getName(), getCommandRole().getId()))
                            .setColor(Color.blue)
                            .build()
                    ).setEphemeral(true).queue();

                    case "set" -> {
                        setCommandRole(event.getOptions().get(0).getAsRole());
                        getConfig().put("CommandRoleID", getCommandRole().getId());
                        event.replyEmbeds(new EmbedBuilder()
                                .setTitle("コマンドロールを%s(%s)に設定しました".formatted(getCommandRole().getName(), getCommandRole().getId()))
                                .setColor(Color.blue)
                                .build()
                        ).queue();
                    }
                }
            }

            case "detectbot" -> {
                switch (Objects.requireNonNull(event.getSubcommandName())) {
                    case "show" -> {
                        StringBuilder bots = new StringBuilder();
                        getDetectbot().forEach(id -> {
                            User user = getJda().getUserById(id);
                            bots.append("・ ").append(user == null ? "(不明なユーザー)" : user.getName()).append("(").append(id).append(")\n");
                        });
                        event.replyEmbeds(new EmbedBuilder()
                                .setTitle("登録されている発言を無視しないbot一覧")
                                .setDescription(fit(bots))
                                .setColor(Color.blue)
                                .build()
                        ).setEphemeral(true).queue();
                    }

                    case "add" -> {
                        User bot = event.getOptions().get(0).getAsUser();
                        getDetectbot().add(bot.getId());
                        event.replyEmbeds(new EmbedBuilder()
                                .setTitle("%s(%s)を登録しました".formatted(bot.getName(), bot.getId()))
                                .setColor(Color.blue)
                                .build()
                        ).queue();
                    }

                    case "del" -> {
                        User bot = event.getOptions().get(0).getAsUser();
                        getDetectbot().remove(bot.getId());
                        event.replyEmbeds(new EmbedBuilder()
                                .setTitle("%s(%s)を削除しました".formatted(bot.getName(), bot.getId()))
                                .setColor(Color.blue)
                                .build()
                        ).queue();
                    }
                }
            }

            case "mentionable" -> {
                switch (Objects.requireNonNull(event.getSubcommandName())) {
                    case "show" -> {
                        StringBuilder builder = new StringBuilder();
                        getMentionable().forEach(mention -> builder.append("・ ").append(mention).append("\n"));
                        event.replyEmbeds(new EmbedBuilder()
                                .setTitle("登録されているメンション可能ロール")
                                .setDescription(fit(builder))
                                .setColor(Color.blue)
                                .build()
                        ).setEphemeral(true).queue();
                    }

                    case "set" -> {
                        StringBuilder builder = new StringBuilder();
                        getMentionable().clear();
                        getMentionable().addAll(event.getOptions().stream().map(OptionMapping::getAsString).toList());
                        getMentionable().forEach(mention -> builder.append("・ ").append(mention).append("\n"));
                        event.replyEmbeds(new EmbedBuilder()
                                .setTitle("メンション可能ロールを設定しました")
                                .setDescription(fit(builder))
                                .setColor(Color.blue)
                                .build()
                        ).queue();
                    }
                }
            }

            case "server" -> {
                switch (Objects.requireNonNull(event.getSubcommandName())) {
                    case "info" -> event.replyEmbeds(new EmbedBuilder()
                            .setTitle("velocity info")
                            .setDescription("```\n\n( %d )人のプレイヤーがオンライン\n\n使用メモリ:\n%d MB / %d MB\n```".formatted(Velodicord.getVelodicord().getProxy().getPlayerCount(), (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024, Runtime.getRuntime().totalMemory() / 1024 / 1024)
                            )
                            .setColor(Color.green)
                            .build()
                    ).queue();

                    case "command" -> {
                        if (!"velocity".equals(event.getOptions().get(0).getAsString())) {
                            event.replyEmbeds(new EmbedBuilder()
                                    .setColor(Color.red)
                                    .setTitle("現在はvelocity(プロキシ)へのコマンドのみ対応しています")
                                    .build()
                            ).setEphemeral(true).queue();
                            return;
                        }

                        String command = event.getOptions().get(1).getAsString();

                        // DiscordCommandSourceが既に「実行しています...」と返信済みなので、結果はその返信を書き換えて伝える
                        // (以前はここで2回目のreplyをしてIllegalStateExceptionになっていた)。
                        boolean found = Velodicord.getVelodicord().getProxy().getCommandManager().executeAsync(new DiscordCommandSource(event), command).join();
                        event.getHook().editOriginalEmbeds(new EmbedBuilder()
                                .setColor(found ? Color.green : Color.red)
                                .setTitle(found ? "実行しました" : "このコマンドは存在しません")
                                .setDescription("`" + command.replace("`", "") + "`")
                                .build()
                        ).queue();
                    }
                }
            }

            case "online" -> {
                StringBuilder builder = new StringBuilder();
                int total = 0;
                for (var server : Velodicord.getVelodicord().getProxy().getAllServers()) {
                    var players = server.getPlayersConnected().stream().map(player -> player.getUsername()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
                    total += players.size();
                    builder.append("**").append(server.getServerInfo().getName()).append("** (").append(players.size()).append("人)\n");
                    builder.append(players.isEmpty() ? "-" : String.join(", ", players).replace("_", "\\_")).append("\n\n");
                }
                event.replyEmbeds(new EmbedBuilder()
                        .setTitle("オンラインのプレイヤー: %d人".formatted(total))
                        .setDescription(fit(builder))
                        .setColor(Color.green)
                        .build()
                ).setEphemeral(true).queue();
            }

            case "link" -> event.replyModal(ModalInteraction.buildLinkModal()).queue();

            case "linkpanel" -> {
                event.getChannel().sendMessageEmbeds(new EmbedBuilder()
                        .setColor(Color.blue)
                        .setTitle("アカウント連携")
                        .setDescription("下のボタンを押して、マイクラで /link を実行して発行されたコードを入力してください")
                        .build()
                ).setComponents(ActionRow.of(Button.primary("link-panel-open", "連携する"))).queue();
                event.replyEmbeds(new EmbedBuilder()
                        .setColor(Color.blue)
                        .setTitle("連携パネルを設置しました")
                        .build()
                ).setEphemeral(true).queue();
            }

            case "unlink" -> {
                if (LinkManager.unlinkDiscord(event.getUser().getId())) {
                    if (getLinkedRole() != null && event.getMember() != null) {
                        event.getGuild().removeRoleFromMember(event.getMember(), getLinkedRole()).queue();
                    }
                    event.replyEmbeds(new EmbedBuilder()
                            .setTitle("連携を解除しました")
                            .setColor(Color.blue)
                            .build()
                    ).setEphemeral(true).queue();
                } else {
                    event.replyEmbeds(new EmbedBuilder()
                            .setTitle("連携されていません")
                            .setColor(Color.red)
                            .build()
                    ).setEphemeral(true).queue();
                }
            }

            case "admincommand" -> {
                switch (Objects.requireNonNull(event.getSubcommandName())) {
                    case "show" -> {
                        StringBuilder builder = new StringBuilder("discordの管理者コマンド\n");
                        getDisadmincommand().forEach(command -> builder.append("・ ").append(command).append("\n"));
                        event.replyEmbeds(new EmbedBuilder()
                                .setTitle("登録されている管理者コマンドコマンド一覧")
                                .setDescription(fit(builder))
                                .setColor(Color.blue)
                                .build()
                        ).setEphemeral(true).queue();
                    }

                    case "add" -> {
                        String command = event.getOptions().get(0).getAsString();
                        getDisadmincommand().add(command);
                        event.replyEmbeds(new EmbedBuilder()
                                .setTitle("discordの管理者コマンドを登録しました")
                                .setDescription(command)
                                .setColor(Color.blue)
                                .build()
                        ).queue();
                    }

                    case "del" -> {
                        String command = event.getOptions().get(0).getAsString();
                        getDisadmincommand().remove(command);
                        event.replyEmbeds(new EmbedBuilder()
                                .setTitle("discordの管理者コマンドを削除しました")
                                .setDescription(command)
                                .setColor(Color.blue)
                                .build()
                        ).queue();
                    }
                }
            }
        }
    }
}
