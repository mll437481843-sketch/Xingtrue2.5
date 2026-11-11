import com.ncmd.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class TestApi {
    static int pass = 0, fail = 0;
    static void check(String name, boolean cond, String detail) {
        if (cond) { pass++; System.out.println("PASS  " + name + "  " + detail); }
        else { fail++; System.out.println("FAIL  " + name + "  " + detail); }
    }
    public static void main(String[] args) throws Exception {
        // ---- LinkParser ----
        String[][] cases = {
            {"https://music.163.com/playlist?id=3778678", "playlist", "3778678"},
            {"https://music.163.com/#/playlist?id=24381616", "playlist", "24381616"},
            {"分享歌单 《热歌榜》 https://music.163.com/playlist?id=3778678 (来自网易云音乐)", "playlist", "3778678"},
            {"https://y.music.163.com/m/song?id=347230&userid=123", "song", "347230"},
            {"https://music.163.com/song/347230", "song", "347230"},
            {"https://music.163.com/album?id=32311", "album", "32311"},
            {"347230", "song", "347230"},
            {"随便一段文字", "unknown", ""},
        };
        for (String[] c : cases) {
            LinkParser.Parsed p = LinkParser.INSTANCE.parse(c[0]);
            check("parse " + c[0].substring(0, Math.min(28, c[0].length())),
                p.getType().equals(c[1]) && p.getId().equals(c[2]), "-> " + p.getType() + "/" + p.getId());
        }
        String[] bad = {"", "https://music.163.com/discover", "abc123"};
        for (String b : bad) {
            LinkParser.Parsed p = LinkParser.INSTANCE.parse(b);
            check("parse-invalid [" + b + "]", p.getType().equals("unknown"), "-> " + p.getType());
        }

        // ---- real API ----
        NeteaseApi.Result pl = NeteaseApi.INSTANCE.playlist("3778678");
        check("playlist.load", pl.getOk(), pl.getOk() ? "" : pl.getMessage());
        if (pl.getOk()) {
            JSONObject d = pl.getData();
            JSONObject info = d.getJSONObject("playlist");
            JSONArray songs = d.getJSONArray("songs");
            check("playlist.meta", info.optString("name").length() > 0, "name=" + info.optString("name") + " count=" + songs.length());
            check("playlist.songs>50", songs.length() > 50, "实际 " + songs.length() + " 首");
            JSONObject s0 = songs.getJSONObject(0);
            check("song.fields", s0.optString("title").length() > 0 && s0.optString("artist").length() > 0,
                s0.optString("title") + " / " + s0.optString("artist") + " / " + s0.optString("album") + " " + s0.optInt("duration") + "s");
        }

        NeteaseApi.PlaySource src = NeteaseApi.INSTANCE.playUrl("347230", "standard");
        check("playUrl.free-track", src.getOk(), src.getOk() ? ("url=" + src.getUrl().substring(0, Math.min(48, src.getUrl().length())) + " br=" + src.getBitrate()) : src.getReason());

        NeteaseApi.Lyric ly = NeteaseApi.INSTANCE.lyric("347230");
        check("lyric", ly.getOk() && ly.getLrc().length() > 20, "len=" + ly.getLrc().length());

        NeteaseApi.Result se = NeteaseApi.INSTANCE.search("周杰伦", 5);
        check("search", se.getOk() && se.getData().optJSONArray("songs").length() > 0, "n=" + (se.getOk() ? se.getData().optJSONArray("songs").length() : -1));

        List<String> ids = Arrays.asList("347230", "1330348068", "186016");
        List<Song> ds = NeteaseApi.INSTANCE.songDetails(ids);
        check("songDetails.batch", ds.size() == 3, "n=" + ds.size());

        NeteaseApi.PlaySource vip = NeteaseApi.INSTANCE.playUrl("186016", "standard");
        check("playUrl.returns-reason-when-unavailable", vip.getOk() || vip.getReason().length() > 0, vip.getOk() ? "未受限" : vip.getReason());

        NeteaseApi.PlaySource hi = NeteaseApi.INSTANCE.playUrl("1330348068", "exhigh");
        check("playUrl.v1-320k", hi.getOk() && hi.getBitrate() >= 320000, "br=" + hi.getBitrate() + " level=" + hi.getLevel() + " size=" + (hi.getSize() / 1048576) + "MB trial=" + hi.getTrial());
        NeteaseApi.PlaySource tv = NeteaseApi.INSTANCE.playUrl("347230", "exhigh");
        check("playUrl.trial-detected", tv.getTrial() || !tv.getOk(), "trial=" + tv.getTrial() + " ok=" + tv.getOk() + " br=" + tv.getBitrate());
        check("playUrl.free-not-trial", !hi.getTrial(), "trial=" + hi.getTrial());

        System.out.println("\nRESULT pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }
}
