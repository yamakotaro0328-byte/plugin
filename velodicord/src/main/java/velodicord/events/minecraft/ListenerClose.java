package velodicord.events.minecraft;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ListenerCloseEvent;
import velodicord.Config;
import velodicord.Discordbot;

public class ListenerClose {
    @Subscribe(order = PostOrder.FIRST)
    public void onListenerClose(ListenerCloseEvent event) {
        Config.saveAll();
        Discordbot.getNoticeChannel().sendMessage("🛑velocityサーバーが停止しました").complete();
        Discordbot.getJda().shutdown();
    }
}
