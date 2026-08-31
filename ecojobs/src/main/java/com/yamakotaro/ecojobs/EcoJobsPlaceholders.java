package com.yamakotaro.ecojobs;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * PlaceholderAPI placeholders (registered only if PlaceholderAPI is installed - see
 * EcoJobsPlugin#onEnable). Job ids never contain underscores (see config.yml's "jobs" section),
 * so every identifier below can be safely split on "_" without ambiguity.
 *
 * %ecojobs_level_<job>%           current level in <job>
 * %ecojobs_xp_<job>%              current xp in <job>
 * %ecojobs_xp_max_<job>%          xp needed for the next level in <job>
 * %ecojobs_prestige_<job>%        prestige count in <job>
 * %ecojobs_joined_<job>%          "true"/"false" - currently active in <job>
 * %ecojobs_total_level%           sum of levels across every job ever joined
 * %ecojobs_top_<job>_<rank>_name%     the #<rank> player's name on <job>'s leaderboard
 * %ecojobs_top_<job>_<rank>_level%    ...their level
 * %ecojobs_top_<job>_<rank>_prestige% ...their prestige
 */
public class EcoJobsPlaceholders extends PlaceholderExpansion {

    private final EcoJobsPlugin plugin;
    private final PlayerJobManager playerJobManager;

    public EcoJobsPlaceholders(EcoJobsPlugin plugin, PlayerJobManager playerJobManager) {
        this.plugin = plugin;
        this.playerJobManager = playerJobManager;
    }

    @Override
    public String getIdentifier() {
        return "ecojobs";
    }

    @Override
    public String getAuthor() {
        return "yamakotaro0328";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        String[] parts = identifier.toLowerCase().split("_");

        if (parts.length == 2 && parts[0].equals("total") && parts[1].equals("level")) {
            return player == null ? "0" : String.valueOf(totalLevel(player));
        }

        if (parts.length == 4 && parts[0].equals("top")) {
            return topPlaceholder(parts[1], parts[2], parts[3]);
        }

        if (player == null) {
            return null;
        }

        if (parts.length == 3 && parts[0].equals("xp") && parts[1].equals("max")) {
            PlayerJobProgress progress = playerJobManager.allProgress(player.getUniqueId()).get(parts[2]);
            return progress == null ? "0" : String.format("%.0f", playerJobManager.xpToNextLevel(progress.getLevel()));
        }

        if (parts.length == 2) {
            String field = parts[0];
            String jobId = parts[1];
            if (field.equals("joined")) {
                return String.valueOf(playerJobManager.isJoined(player.getUniqueId(), jobId));
            }
            PlayerJobProgress progress = playerJobManager.allProgress(player.getUniqueId()).get(jobId);
            return switch (field) {
                case "level" -> String.valueOf(progress != null ? progress.getLevel() : 0);
                case "xp" -> String.format("%.0f", progress != null ? progress.getXp() : 0);
                case "prestige" -> String.valueOf(progress != null ? progress.getPrestige() : 0);
                default -> null;
            };
        }

        return null;
    }

    private int totalLevel(Player player) {
        int total = 0;
        for (PlayerJobProgress progress : playerJobManager.allProgress(player.getUniqueId()).values()) {
            total += progress.getLevel();
        }
        return total;
    }

    private String topPlaceholder(String jobId, String rankString, String field) {
        int rank;
        try {
            rank = Integer.parseInt(rankString);
        } catch (NumberFormatException e) {
            return null;
        }
        if (rank < 1) {
            return null;
        }
        List<PlayerJobManager.TopEntry> top = playerJobManager.top(jobId, rank);
        if (top.size() < rank) {
            return "";
        }
        PlayerJobManager.TopEntry entry = top.get(rank - 1);
        return switch (field) {
            case "name" -> entry.name();
            case "level" -> String.valueOf(entry.level());
            case "xp" -> String.format("%.0f", entry.xp());
            case "prestige" -> String.valueOf(entry.prestige());
            default -> null;
        };
    }
}
