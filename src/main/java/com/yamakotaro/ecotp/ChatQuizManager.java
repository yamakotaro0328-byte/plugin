package com.yamakotaro.ecotp;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * チャットクイズ: 一定時間ごとにチャットへ問題 (計算・早打ち・並べ替え) を出し、最初に
 * 正解をチャットに打った人に賞金を払う。正解数は quiz-wins.yml に保存し /quiz top で見られる。
 */
public class ChatQuizManager implements Listener {

    public enum Type {
        MATH, TYPING, SCRAMBLE;

        public String key() {
            return name().toLowerCase();
        }

        public static Type parse(String text) {
            for (Type type : values()) {
                if (type.key().equalsIgnoreCase(text)) {
                    return type;
                }
            }
            return null;
        }
    }

    /** 出題中の問題。チャットは非同期スレッドで来るので、正解者は won の CAS で1人に決める。 */
    private record Question(Type type, String shown, String answer, double reward, long startedAt, AtomicBoolean won) {
    }

    private static final String TYPING_CHARS = "abcdefghjkmnpqrstuvwxyz23456789";

    private final EcoTpPlugin plugin;
    private final File file;
    private final Map<UUID, Integer> wins = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private volatile Question current;
    private int secondsUntilNext;
    private int secondsLeft;

    public ChatQuizManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "quiz-wins.yml");
        load();
        this.secondsUntilNext = intervalSeconds();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("chat-quiz.enabled", true);
    }

    private int intervalSeconds() {
        return Math.max(60, plugin.getConfig().getInt("chat-quiz.interval-minutes", 15) * 60);
    }

    private void tick() {
        Question question = current;
        if (question != null) {
            if (question.won().get()) {
                current = null;
                return;
            }
            if (--secondsLeft <= 0 && question.won().compareAndSet(false, true)) {
                current = null;
                Bukkit.broadcastMessage(plugin.getMessages().get("quiz.timeout", "answer", question.answer()));
            }
            return;
        }
        if (!enabled()) {
            secondsUntilNext = intervalSeconds();
            return;
        }
        if (--secondsUntilNext > 0) {
            return;
        }
        secondsUntilNext = intervalSeconds();
        if (Bukkit.getOnlinePlayers().size() >= plugin.getConfig().getInt("chat-quiz.min-players", 2)) {
            start(null);
        }
    }

    /** @param forced null ならランダム (config の types から)。 */
    public boolean start(Type forced) {
        if (current != null) {
            return false;
        }
        Type type = forced != null ? forced : randomType();
        Question question = create(type);
        if (question == null) {
            return false;
        }
        secondsLeft = Math.max(10, plugin.getConfig().getInt("chat-quiz.answer-seconds", 60));
        current = question;
        Bukkit.broadcastMessage(plugin.getMessages().get("quiz.question-" + type.key(),
                "question", question.shown(), "seconds", String.valueOf(secondsLeft),
                "reward", ChatUtil.formatMoney(question.reward())));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
        }
        return true;
    }

    public boolean stop() {
        Question question = current;
        if (question == null || !question.won().compareAndSet(false, true)) {
            return false;
        }
        current = null;
        Bukkit.broadcastMessage(plugin.getMessages().get("quiz.cancelled", "answer", question.answer()));
        return true;
    }

    private Type randomType() {
        List<Type> types = new ArrayList<>();
        for (String key : plugin.getConfig().getStringList("chat-quiz.types")) {
            Type type = Type.parse(key);
            if (type != null) {
                types.add(type);
            }
        }
        if (types.isEmpty()) {
            types = List.of(Type.values());
        }
        return types.get(ThreadLocalRandom.current().nextInt(types.size()));
    }

    private Question create(Type type) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String shown;
        String answer;
        switch (type) {
            case MATH -> {
                int op = random.nextInt(3);
                if (op == 0) {
                    int a = random.nextInt(10, 1000);
                    int b = random.nextInt(10, 1000);
                    shown = a + " + " + b;
                    answer = String.valueOf(a + b);
                } else if (op == 1) {
                    int a = random.nextInt(50, 1000);
                    int b = random.nextInt(10, a);
                    shown = a + " - " + b;
                    answer = String.valueOf(a - b);
                } else {
                    int a = random.nextInt(2, 21);
                    int b = random.nextInt(2, 21);
                    shown = a + " × " + b;
                    answer = String.valueOf(a * b);
                }
            }
            case TYPING -> {
                StringBuilder text = new StringBuilder();
                int length = random.nextInt(6, 9);
                for (int i = 0; i < length; i++) {
                    text.append(TYPING_CHARS.charAt(random.nextInt(TYPING_CHARS.length())));
                }
                shown = text.toString();
                answer = shown;
            }
            default -> {
                List<String> words = plugin.getConfig().getStringList("chat-quiz.words");
                if (words.isEmpty()) {
                    return null;
                }
                answer = words.get(random.nextInt(words.size()));
                shown = scramble(answer);
            }
        }
        double reward = plugin.getConfig().getDouble("chat-quiz.rewards." + type.key(), 300.0);
        return new Question(type, shown, answer, reward, System.currentTimeMillis(), new AtomicBoolean());
    }

    private static String scramble(String word) {
        List<String> chars = new ArrayList<>();
        word.codePoints().forEach(cp -> chars.add(new String(Character.toChars(cp))));
        if (chars.size() < 2) {
            return word;
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            Collections.shuffle(chars);
            String shuffled = String.join("", chars);
            if (!shuffled.equals(word)) {
                return shuffled;
            }
        }
        Collections.reverse(chars);
        return String.join("", chars);
    }

    /** 全角→半角・大文字→小文字・カタカナ→ひらがな にそろえて比べる。 */
    private static String normalize(String text) {
        String n = Normalizer.normalize(text, Normalizer.Form.NFKC).trim().toLowerCase();
        StringBuilder out = new StringBuilder(n.length());
        for (char c : n.toCharArray()) {
            out.append(c >= 'ァ' && c <= 'ヶ' ? (char) (c - 0x60) : c);
        }
        return out.toString().replace(" ", "");
    }

    // MONITOR: ローマ字変換などで書き換えられた後の本文と、打った元の文の両方で判定する。
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Question question = current;
        if (question == null || question.won().get()) {
            return;
        }
        String expected = normalize(question.answer());
        String typed = normalize(event.signedMessage().message());
        String shown = normalize(PlainTextComponentSerializer.plainText().serialize(event.message()));
        if (!expected.equals(typed) && !expected.equals(shown)) {
            return;
        }
        if (!question.won().compareAndSet(false, true)) {
            return; // 一瞬遅かった
        }
        Player player = event.getPlayer();
        double seconds = (System.currentTimeMillis() - question.startedAt()) / 1000.0;
        Bukkit.getScheduler().runTask(plugin, () -> reward(player, question, seconds));
    }

    private void reward(Player player, Question question, double seconds) {
        current = null;
        Economy economy = plugin.getEconomyHolder().get();
        boolean paid = economy != null && question.reward() > 0
                && economy.depositPlayer(player, question.reward()).transactionSuccess();
        int total = wins.merge(player.getUniqueId(), 1, Integer::sum);
        names.put(player.getUniqueId(), player.getName());
        save();
        Bukkit.broadcastMessage(plugin.getMessages().get("quiz.winner",
                "player", player.getName(), "time", String.format("%.1f", seconds),
                "answer", question.answer(), "reward", paid ? ChatUtil.formatMoney(question.reward()) : "-",
                "wins", String.valueOf(total)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
        player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0),
                40, 0.4, 0.6, 0.4, 0.3);
    }

    public int getWins(UUID uuid) {
        return wins.getOrDefault(uuid, 0);
    }

    public boolean isRunning() {
        return current != null;
    }

    public int getSecondsUntilNext() {
        return secondsUntilNext;
    }

    /** @return 正解数の多い順に [名前, 回数]。 */
    public List<Map.Entry<String, Integer>> top(int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>();
        wins.forEach((uuid, count) -> list.add(Map.entry(names.getOrDefault(uuid, uuid.toString()), count)));
        list.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)));
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    private void load() {
        YamlConfiguration data = YamlIo.load(file);
        var section = data.getConfigurationSection("wins");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                wins.put(uuid, section.getInt(key + ".count"));
                names.put(uuid, section.getString(key + ".name", key));
            } catch (IllegalArgumentException ignored) {
                // UUID でないキーは無視
            }
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        wins.forEach((uuid, count) -> {
            data.set("wins." + uuid + ".name", names.getOrDefault(uuid, uuid.toString()));
            data.set("wins." + uuid + ".count", count);
        });
        try {
            YamlIo.save(data, file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save quiz-wins.yml", e);
        }
    }
}
