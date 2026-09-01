package com.yamakotaro.ecoban.core.migrate;

import com.yamakotaro.ecoban.core.Punishment;
import com.yamakotaro.ecoban.core.PunishmentStorage;
import com.yamakotaro.ecoban.core.PunishmentType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Imports from AdvancedBan's own database. Verified against AdvancedBan's real schema
 * (github.com/DevLeoko/AdvancedBan, SQLQuery.java): the Punishments table holds only currently
 * open punishments, PunishmentHistory holds every one ever issued (a superset), and both share
 * identical columns: name, uuid, reason, operator, punishmentType, start, end, calculation.
 * end == -1 means permanent. punishmentType is the Java enum's name() (BAN, TEMP_BAN, IP_BAN,
 * TEMP_IP_BAN, MUTE, TEMP_MUTE, WARNING, TEMP_WARNING, KICK, NOTE) - AdvancedBan stores an IP
 * address in the `uuid` column itself for IP_BAN/TEMP_IP_BAN rows, its own convention.
 *
 * AdvancedBan has no "is this still active" column of its own; whether a PunishmentHistory row is
 * still open is instead determined by whether a matching row still exists in Punishments. Since
 * the two tables have independent auto-increment ids, rows are matched by (uuid, punishmentType,
 * start) instead, which AdvancedBan writes identically to both tables for the same punishment.
 */
public final class AdvancedBanImporter {

    private AdvancedBanImporter() {
    }

    /**
     * @return the number of punishments imported.
     */
    public static int importInto(Connection sourceConnection, PunishmentStorage destination, Logger logger) {
        Set<String> stillOpenKeys = loadStillOpenKeys(sourceConnection, logger);
        int count = 0;
        String sql = "SELECT name, uuid, reason, operator, punishmentType, start, end FROM PunishmentHistory";
        try (PreparedStatement statement = sourceConnection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Punishment imported = convertRow(rs, stillOpenKeys);
                if (imported != null) {
                    destination.insert(imported);
                    count++;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to read AdvancedBan's PunishmentHistory table", e);
        }
        return count;
    }

    private static Set<String> loadStillOpenKeys(Connection sourceConnection, Logger logger) {
        Set<String> keys = new HashSet<>();
        String sql = "SELECT uuid, punishmentType, start FROM Punishments";
        try (PreparedStatement statement = sourceConnection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                keys.add(openKey(rs.getString("uuid"), rs.getString("punishmentType"), rs.getLong("start")));
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to read AdvancedBan's Punishments table "
                    + "(imported active/inactive status may be inaccurate)", e);
        }
        return keys;
    }

    private static String openKey(String uuid, String type, long start) {
        return uuid + "|" + type + "|" + start;
    }

    private static Punishment convertRow(ResultSet rs, Set<String> stillOpenKeys) throws SQLException {
        String rawType = rs.getString("punishmentType");
        PunishmentType type = mapType(rawType);
        if (type == null) {
            // NOTE has no EcoBan equivalent; nothing lost since it never blocked anything anyway.
            return null;
        }
        String rawUuid = rs.getString("uuid");
        String name = rs.getString("name");
        String reason = rs.getString("reason");
        String operator = rs.getString("operator");
        long start = rs.getLong("start");
        long end = rs.getLong("end");
        long expiresAt = end <= 0 ? 0 : end;

        UUID targetUuid = null;
        String ip = null;
        if (type == PunishmentType.IPBAN) {
            ip = rawUuid;
        } else if (rawUuid != null) {
            try {
                targetUuid = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException ignored) {
                // A handful of very old rows store an IP even for a non-IP punishment type;
                // skip rather than guess which player it belonged to.
                return null;
            }
        }

        boolean expired = expiresAt > 0 && expiresAt <= System.currentTimeMillis();
        boolean stillOpen = stillOpenKeys.contains(openKey(rawUuid, rawType, start));
        boolean active = type.canBeActive() && stillOpen && !expired;
        return new Punishment(type, targetUuid, name, ip, reason, operator, start, expiresAt, active);
    }

    private static PunishmentType mapType(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw) {
            case "BAN" -> PunishmentType.BAN;
            case "TEMP_BAN" -> PunishmentType.TEMPBAN;
            case "IP_BAN", "TEMP_IP_BAN" -> PunishmentType.IPBAN;
            case "MUTE" -> PunishmentType.MUTE;
            case "TEMP_MUTE" -> PunishmentType.TEMPMUTE;
            case "KICK" -> PunishmentType.KICK;
            case "WARNING", "TEMP_WARNING" -> PunishmentType.WARN;
            default -> null;
        };
    }
}
