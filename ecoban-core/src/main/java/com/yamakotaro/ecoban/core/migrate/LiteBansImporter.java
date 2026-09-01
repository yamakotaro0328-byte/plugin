package com.yamakotaro.ecoban.core.migrate;

import com.yamakotaro.ecoban.core.Punishment;
import com.yamakotaro.ecoban.core.PunishmentStorage;
import com.yamakotaro.ecoban.core.PunishmentType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Imports from LiteBans' database. LiteBans itself is closed-source, so this is built against its
 * real, well-documented table layout as confirmed through third-party tools written against it
 * (e.g. the NyaStudio/Litebans-Bridge Velocity plugin and the deltaflyer4747/litebans-php web
 * panel) rather than LiteBans' own source: tables litebans_bans / litebans_mutes /
 * litebans_warnings / litebans_kicks / litebans_history under the default "litebans_" table
 * prefix, with columns id, uuid, ip, reason, banned_by_uuid, banned_by_name, removed_by_uuid,
 * removed_by_name, removed_by_reason, time, until, server_scope, server_origin, silent, ipban,
 * ipban_wildcard, active (until == 0 means permanent). The warnings/kicks tables may not carry
 * every one of those columns (a kick has no "until"), so each optional column is read
 * defensively - only if the query's ResultSetMetaData actually reports it - rather than assumed.
 */
public final class LiteBansImporter {

    private LiteBansImporter() {
    }

    /**
     * @return the number of punishments imported.
     */
    public static int importInto(Connection sourceConnection, PunishmentStorage destination, String tablePrefix, Logger logger) {
        Map<String, String> nameByUuid = loadNames(sourceConnection, tablePrefix + "history", logger);
        int count = 0;
        count += importTable(sourceConnection, destination, tablePrefix + "bans", PunishmentType.BAN, nameByUuid, logger);
        count += importTable(sourceConnection, destination, tablePrefix + "mutes", PunishmentType.MUTE, nameByUuid, logger);
        count += importTable(sourceConnection, destination, tablePrefix + "warnings", PunishmentType.WARN, nameByUuid, logger);
        count += importTable(sourceConnection, destination, tablePrefix + "kicks", PunishmentType.KICK, nameByUuid, logger);
        return count;
    }

    private static Map<String, String> loadNames(Connection sourceConnection, String historyTable, Logger logger) {
        Map<String, String> names = new HashMap<>();
        String sql = "SELECT uuid, name FROM " + historyTable + " ORDER BY date DESC";
        try (PreparedStatement statement = sourceConnection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                // Most-recent-first, so the first name seen for a uuid is its latest known one.
                names.putIfAbsent(rs.getString("uuid"), rs.getString("name"));
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to read LiteBans' history table for name lookups "
                    + "(imported punishments will be missing a target name)", e);
        }
        return names;
    }

    private static int importTable(Connection sourceConnection, PunishmentStorage destination, String table,
                                    PunishmentType baseType, Map<String, String> nameByUuid, Logger logger) {
        int count = 0;
        String sql = "SELECT * FROM " + table;
        try (PreparedStatement statement = sourceConnection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            Set<String> columns = columnNames(rs.getMetaData());
            while (rs.next()) {
                Punishment imported = convertRow(rs, columns, baseType, nameByUuid);
                if (imported != null) {
                    destination.insert(imported);
                    count++;
                }
            }
        } catch (SQLException e) {
            // Perfectly normal if this server never used that punishment type - LiteBans still
            // creates all four tables regardless, but log at WARNING (not SEVERE) just in case.
            logger.log(Level.WARNING, "Failed to read LiteBans table " + table, e);
        }
        return count;
    }

    private static Set<String> columnNames(ResultSetMetaData metaData) throws SQLException {
        Set<String> columns = new HashSet<>();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            columns.add(metaData.getColumnLabel(i).toLowerCase());
        }
        return columns;
    }

    private static Punishment convertRow(ResultSet rs, Set<String> columns, PunishmentType baseType,
                                          Map<String, String> nameByUuid) throws SQLException {
        String uuidString = getStringOrNull(rs, columns, "uuid");
        String ip = getStringOrNull(rs, columns, "ip");
        String reason = getStringOrNull(rs, columns, "reason");
        String operator = getStringOrNull(rs, columns, "banned_by_name");
        long time = columns.contains("time") ? rs.getLong("time") : System.currentTimeMillis();
        long until = columns.contains("until") ? rs.getLong("until") : 0L;
        boolean ipban = columns.contains("ipban") && rs.getBoolean("ipban");
        boolean activeColumn = !columns.contains("active") || rs.getBoolean("active");

        PunishmentType type = switch (baseType) {
            case BAN -> ipban ? PunishmentType.IPBAN : (until > 0 ? PunishmentType.TEMPBAN : PunishmentType.BAN);
            case MUTE -> until > 0 ? PunishmentType.TEMPMUTE : PunishmentType.MUTE;
            default -> baseType;
        };

        UUID targetUuid = null;
        if (!type.isIpBased() && uuidString != null) {
            try {
                targetUuid = UUID.fromString(uuidString);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        String targetIp = type.isIpBased() ? ip : null;
        String targetName = uuidString != null ? nameByUuid.get(uuidString) : null;
        boolean expired = until > 0 && until <= System.currentTimeMillis();
        boolean active = type.canBeActive() && activeColumn && !expired;
        return new Punishment(type, targetUuid, targetName, targetIp, reason, operator, time, until, active);
    }

    private static String getStringOrNull(ResultSet rs, Set<String> columns, String name) throws SQLException {
        return columns.contains(name) ? rs.getString(name) : null;
    }
}
