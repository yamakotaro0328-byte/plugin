package com.yamakotaro.ecotp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /msg・/reply で使う「直前の会話相手」を記録する (オンラインの間だけ、永続化しない)。
 * /msg で送るたびに、送信者・受信者どちらの記録も相手に更新するので、どちらからでも
 * /reply で会話を続けられる。
 */
public class PrivateMessageManager {

    private final Map<UUID, UUID> replyTarget = new HashMap<>();

    public void recordConversation(UUID a, UUID b) {
        replyTarget.put(a, b);
        replyTarget.put(b, a);
    }

    public UUID getReplyTarget(UUID uuid) {
        return replyTarget.get(uuid);
    }
}
