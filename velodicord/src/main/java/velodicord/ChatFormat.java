package velodicord;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * プレイヤーやDiscordユーザーが入力した文字列を、MiniMessageとして安全に表示できる形に変換する。
 * 先にユーザー入力中のタグを全てエスケープしてから、こちらで用意したタグ(太字・リンク等)だけを
 * 付け足すので、入力に <click:run_command:'/op ...'> のようなタグを仕込まれても
 * ただの文字として表示され、クリックで他人にコマンドを実行させることはできない。
 */
public final class ChatFormat {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]*?)]\\((https?://[^\\s)]+)\\)");
    private static final Pattern BARE_URL = Pattern.compile("https?://\\S+");

    private ChatFormat() {
    }

    public static String escape(String raw) {
        return MM.escapeTags(raw);
    }

    /** Discord風マークダウンとURLをMiniMessageに変換する(入力中のタグは無効化済み)。 */
    public static String toMiniMessage(String raw) {
        String text = escape(raw)
                .replaceAll("~~(.*?)~~", "<st>$1</st>")
                .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                .replaceAll("__(.*?)__", "<u>$1</u>")
                .replaceAll("(?<![\\w])_(.+?)_(?![\\w])", "<i>$1</i>")
                .replaceAll("```(.*?)```", "$1")
                .replaceAll("\\|\\|(.*?)\\|\\|", "<gray>[ネタバレ]</gray>");
        return linkify(text);
    }

    /** 文字列中の部分一致を青色で強調する(メンション表示用)。needleもエスケープしてから照合する。 */
    public static String highlight(String miniMessage, String needle) {
        String escaped = escape(needle);
        return miniMessage.replace(escaped, "<blue>" + escaped + "</blue>");
    }

    private static String linkify(String text) {
        Matcher markdown = MARKDOWN_LINK.matcher(text);
        if (markdown.find()) {
            markdown.reset();
            StringBuilder out = new StringBuilder();
            while (markdown.find()) {
                markdown.appendReplacement(out, Matcher.quoteReplacement(link(markdown.group(1), markdown.group(2))));
            }
            markdown.appendTail(out);
            return out.toString();
        }
        Matcher bare = BARE_URL.matcher(text);
        StringBuilder out = new StringBuilder();
        while (bare.find()) {
            bare.appendReplacement(out, Matcher.quoteReplacement(link(bare.group(), bare.group())));
        }
        bare.appendTail(out);
        return out.toString();
    }

    private static String link(String label, String url) {
        String safeUrl = url.replaceAll("[\\\\'\"<>]", "");
        return "<blue><u><click:open_url:'" + safeUrl + "'>" + label + "</click></u></blue>";
    }
}
