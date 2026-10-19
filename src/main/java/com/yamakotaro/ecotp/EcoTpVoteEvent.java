package com.yamakotaro.ecotp;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * EcoTP 内蔵の Votifier 受け口が正しい投票を受け付けた時に、メインスレッドで発火する。
 * 他のプラグインはこれを聞けば、NuVotifier 無しでも投票を数えられる。
 * (NuVotifier が入っている時は内蔵の受け口は起動しないので、そちらの VotifierEvent を使う。)
 */
public class EcoTpVoteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String serviceName;
    private final String username;
    private final String address;
    private final String timestamp;
    private final String protocol;

    public EcoTpVoteEvent(String serviceName, String username, String address, String timestamp, String protocol) {
        this.serviceName = serviceName;
        this.username = username;
        this.address = address;
        this.timestamp = timestamp;
        this.protocol = protocol;
    }

    /** @return 投票サイトの名前 (Votifier の serviceName)。 */
    public String getServiceName() {
        return serviceName;
    }

    /** @return 投票したプレイヤー名 (サイトで入力された名前。オフラインの場合もある)。 */
    public String getUsername() {
        return username;
    }

    /** @return サイトが送ってきた投票者のアドレス (空のこともある)。 */
    public String getAddress() {
        return address;
    }

    /** @return サイトが送ってきたタイムスタンプ (空のこともある)。 */
    public String getTimestamp() {
        return timestamp;
    }

    /** @return "v1" または "v2"。 */
    public String getProtocol() {
        return protocol;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
