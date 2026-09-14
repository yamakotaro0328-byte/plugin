package velodicord;

import lombok.Getter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** VOICEVOX(TTS)機能は無効化されています。 */
public class Voicevox {
    @Getter
    private static final List<ModelInfo> voicevox = new ArrayList<>();

    public static void init() {
    }

    public static synchronized boolean tts(String msg, int id, Path wavPath) {
        return false;
    }

    public record ModelInfo(String file, String name, int id) {
    }
}
