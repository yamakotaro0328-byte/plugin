package velodicord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Config {

    @Getter
    @Setter
    private static LinkedHashMap<String, String> dic;

    @Getter
    private static LinkedHashMap<String, String> config;

    @Getter
    private static ArrayList<String> detectbot;

    @Getter
    private static ArrayList<String> disadmincommand;

    /** discordユーザーID -> マイクラプレイヤーUUID(文字列)。LinkManager経由でのみ書き込む。 */
    @Getter
    private static LinkedHashMap<String, String> link;

    @Getter
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final Type listtype = new TypeToken<ArrayList<String>>() {
    }.getType();

    @DataDirectory
    @Getter
    @Setter
    private static Path dataDirectory;

    @Getter
    @Setter
    private static Path configjson;

    @Getter
    @Setter
    private static Path dicjson;

    @Getter
    @Setter
    private static Path detectbotjson;

    @Getter
    @Setter
    private static Path disadmincommandjson;

    @Getter
    @Setter
    private static Path mentionablejson;

    @Getter
    @Setter
    private static Path linkjson;

    public static void init() {
        if (Files.notExists(dataDirectory)) {
            try {
                Files.createDirectory(dataDirectory);
            } catch (IOException e) {
                Velodicord.getVelodicord().getLogger().error("Velodicordのconfigディレクトリを作成できませんでした: {}", ExceptionUtils.getStackTrace(e));
            }
        }

        if (Files.notExists(configjson)) {
            try {
                Files.copy(Objects.requireNonNull(Velodicord.class.getResourceAsStream("/config.json")), configjson);
            } catch (IOException e) {
                Velodicord.getVelodicord().getLogger().error("Velodicordのconfigを作成できませんでした: {}", ExceptionUtils.getStackTrace(e));
            }
            Velodicord.getVelodicord().getLogger().info("Velodicordのconfigを設定してください");
        }

        if (Files.notExists(dicjson)) {
            try {
                Files.copy(Objects.requireNonNull(Velodicord.class.getResourceAsStream("/object.json")), dicjson);
            } catch (IOException e) {
                Velodicord.getVelodicord().getLogger().error("Velodicordの辞書を作成できませんでした: {}", ExceptionUtils.getStackTrace(e));
            }
        }

        if (Files.notExists(detectbotjson)) {
            try {
                Files.copy(Objects.requireNonNull(Velodicord.class.getResourceAsStream("/array.json")), detectbotjson);
            } catch (IOException e) {
                Velodicord.getVelodicord().getLogger().error("Velodicordのdetectbotリストを作成できませんでした: {}", ExceptionUtils.getStackTrace(e));
            }
        }

        if (Files.notExists(disadmincommandjson)) {
            try {
                Files.copy(Objects.requireNonNull(Velodicord.class.getResourceAsStream("/disadmincommand.json")), disadmincommandjson);
            } catch (IOException e) {
                Velodicord.getVelodicord().getLogger().error("Velodicordのdisadmincommandリストを作成できませんでした: {}", ExceptionUtils.getStackTrace(e));
            }
        }

        if (Files.notExists(mentionablejson)) {
            try {
                Files.copy(Objects.requireNonNull(Velodicord.class.getResourceAsStream("/mentionable.json")), mentionablejson);
            } catch (IOException e) {
                Velodicord.getVelodicord().getLogger().error("Velodicordのmentionableリストを作成できませんでした: {}", ExceptionUtils.getStackTrace(e));
            }
        }

        if (Files.notExists(linkjson)) {
            try {
                Files.copy(Objects.requireNonNull(Velodicord.class.getResourceAsStream("/object.json")), linkjson);
            } catch (IOException e) {
                Velodicord.getVelodicord().getLogger().error("Velodicordのlinkリストを作成できませんでした: {}", ExceptionUtils.getStackTrace(e));
            }
        }

        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(String.valueOf(configjson)), StandardCharsets.UTF_8))) {
            config = gson.fromJson(reader, new TypeToken<LinkedHashMap<String, String>>() {
            }.getType());
        } catch (IOException e) {
            Velodicord.getVelodicord().getLogger().error("Velodicordのconfigが見つかりませんでした: {}", ExceptionUtils.getStackTrace(e));
        }
        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(String.valueOf(dicjson)), StandardCharsets.UTF_8))) {
            dic = ((HashMap<String, String>) gson.fromJson(reader, new TypeToken<HashMap<String, String>>() {
            }.getType())).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(String::length).reversed()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
        } catch (IOException e) {
            Velodicord.getVelodicord().getLogger().error("Velodicordの辞書が見つかりませんでした: {}", ExceptionUtils.getStackTrace(e));
        }
        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(String.valueOf(detectbotjson)), StandardCharsets.UTF_8))) {
            detectbot = gson.fromJson(reader, listtype);
        } catch (IOException e) {
            Velodicord.getVelodicord().getLogger().error("Velodicordのdetectbotリストが見つかりませんでした: {}", ExceptionUtils.getStackTrace(e));
        }
        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(String.valueOf(disadmincommandjson)), StandardCharsets.UTF_8))) {
            disadmincommand = gson.fromJson(reader, listtype);
        } catch (IOException e) {
            Velodicord.getVelodicord().getLogger().error("Velodicordのdisadmincommandリストが見つかりませんでした: {}", ExceptionUtils.getStackTrace(e));
        }
        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(String.valueOf(mentionablejson)), StandardCharsets.UTF_8))) {
            Discordbot.setMentionable(gson.fromJson(reader, listtype));
        } catch (IOException e) {
            Velodicord.getVelodicord().getLogger().error("Velodicordのmentionableリストが見つかりませんでした: {}", ExceptionUtils.getStackTrace(e));
        }
        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(String.valueOf(linkjson)), StandardCharsets.UTF_8))) {
            link = gson.fromJson(reader, new TypeToken<LinkedHashMap<String, String>>() {
            }.getType());
        } catch (IOException e) {
            Velodicord.getVelodicord().getLogger().error("Velodicordのlinkリストが見つかりませんでした: {}", ExceptionUtils.getStackTrace(e));
        }
    }
}
