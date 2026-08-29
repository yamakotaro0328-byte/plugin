package com.yamakotaro.serverkit.dragonarena;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Minimal leader-based party used only to group players for a dragon fight (solo = a leader
 * with no members). Not a general-purpose party/guild system.
 */
public class PartyManager {

    private record Invite(UUID leader, long expiresAt) {
    }

    private final Map<UUID, Set<UUID>> parties = new HashMap<>();
    private final Map<UUID, UUID> memberToLeader = new HashMap<>();
    private final Map<UUID, Invite> pendingInvites = new HashMap<>();

    public enum InviteResult { SUCCESS, NOT_LEADER, TARGET_IN_PARTY, TARGET_IS_SELF, PARTY_FULL }

    public enum AcceptResult { SUCCESS, NO_PENDING_INVITE, EXPIRED }

    public UUID getLeader(UUID uuid) {
        UUID leader = memberToLeader.get(uuid);
        return leader != null ? leader : uuid;
    }

    public Set<UUID> getMembers(UUID uuid) {
        UUID leader = getLeader(uuid);
        Set<UUID> members = new HashSet<>();
        members.add(leader);
        members.addAll(parties.getOrDefault(leader, Set.of()));
        return members;
    }

    public boolean isLeader(UUID uuid) {
        return !memberToLeader.containsKey(uuid);
    }

    public InviteResult invite(UUID leader, UUID target, int teamSizeMax, long timeoutMillis) {
        if (!isLeader(leader)) {
            return InviteResult.NOT_LEADER;
        }
        if (leader.equals(target)) {
            return InviteResult.TARGET_IS_SELF;
        }
        if (memberToLeader.containsKey(target) || parties.containsKey(target)) {
            return InviteResult.TARGET_IN_PARTY;
        }
        int currentSize = 1 + parties.getOrDefault(leader, Set.of()).size();
        if (currentSize >= teamSizeMax) {
            return InviteResult.PARTY_FULL;
        }
        pendingInvites.put(target, new Invite(leader, System.currentTimeMillis() + timeoutMillis));
        return InviteResult.SUCCESS;
    }

    public UUID acceptResult(UUID invitee) {
        Invite invite = pendingInvites.get(invitee);
        return invite == null ? null : invite.leader();
    }

    public AcceptResult accept(UUID invitee) {
        Invite invite = pendingInvites.remove(invitee);
        if (invite == null) {
            return AcceptResult.NO_PENDING_INVITE;
        }
        if (invite.expiresAt() < System.currentTimeMillis()) {
            return AcceptResult.EXPIRED;
        }
        parties.computeIfAbsent(invite.leader(), k -> new HashSet<>()).add(invitee);
        memberToLeader.put(invitee, invite.leader());
        return AcceptResult.SUCCESS;
    }

    /** @return true if the player was actually in a party (as leader or member) */
    public boolean leave(UUID uuid) {
        UUID asMemberLeader = memberToLeader.remove(uuid);
        if (asMemberLeader != null) {
            Set<UUID> members = parties.get(asMemberLeader);
            if (members != null) {
                members.remove(uuid);
                if (members.isEmpty()) {
                    parties.remove(asMemberLeader);
                }
            }
            return true;
        }
        Set<UUID> members = parties.remove(uuid);
        if (members != null) {
            for (UUID member : members) {
                memberToLeader.remove(member);
            }
            return true;
        }
        return false;
    }

    /** Drops all state for a disconnecting player: their party membership and any invites they sent or hold. */
    public void handleQuit(UUID uuid) {
        leave(uuid);
        pendingInvites.remove(uuid);
        pendingInvites.values().removeIf(invite -> invite.leader().equals(uuid));
    }
}
