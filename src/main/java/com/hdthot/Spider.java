package com.hdthot;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.util.*;
import java.util.regex.*;
import java.util.Base64;

/**
 * HDThot 解析器 - 兼容 CatVodSpider 接口
 * 供 OK影视 / TVBox 使用
 */
public class Spider {

    private static final String BASE_URL = "https://hdthot.com";
    private final OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                Request req = chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15")
                        .header("Referer", BASE_URL)
                        .build();
                return chain.proceed(req);
            })
            .build();

    // ========== 核心解码 ==========
    private String decodeSource(String encrypted) {
        if (encrypted == null || encrypted.length() < 32) return null;
        try {
            String trimmed = encrypted.substring(16, encrypted.length() - 16);
            String reversed = new StringBuilder(trimmed).reverse().toString();
            return new String(Base64.getDecoder().decode(reversed));
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 请求页面 ==========
    private String fetchHtml(String url) throws Exception {
        Request req = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(req).execute()) {
            return resp.body().string();
        }
    }

    // ========== 解析列表（分类/首页/搜索） ==========
    public List<Map<String, String>> getList(String url) {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            String html = fetchHtml(url);
            Pattern blockPat = Pattern.compile("<div class=\"post\">(.*?)</div>\\s*</div>", Pattern.DOTALL);
            Matcher m = blockPat.matcher(html);
            while (m.find()) {
                String block = m.group(1);
                String link = extract(block, "href=\"([^\"]+)\"");
                if (link == null) continue;
                if (!link.startsWith("http")) link = BASE_URL + link;
                String title = extract(block, "title=\"([^\"]+)\"");
                if (title == null) {
                    title = extract(block, "<h3[^>]*>(.*?)</h3>");
                }
                String img = extract(block, "<img[^>]+src=\"([^\"]+)\"");
                if (img != null && !img.startsWith("http")) img = BASE_URL + img;
                String dur = extract(block, "<div class=\"duration\">([^<]+)</div>");
                String id = extract(link, "-(\\d+)$");
                Map<String, String> item = new HashMap<>();
                item.put("vod_id", id != null ? id : "");
                item.put("vod_name", title != null ? title : "");
                item.put("vod_pic", img != null ? img : "");
                item.put("vod_remarks", dur != null ? dur : "");
                item.put("vod_url", link);
                list.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private String extract(String text, String regex) {
        Pattern p = Pattern.compile(regex, Pattern.DOTALL);
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    // ========== 获取详情（TVBox 要求返回 Video 对象，这里简化返回 Map）==========
    public Map<String, Object> detail(String vodId) {
        Map<String, Object> result = new HashMap<>();
        try {
            String url = BASE_URL + "/" + vodId;
            String html = fetchHtml(url);
            String title = extract(html, "<h1[^>]*>(.*?)</h1>");
            String img = extract(html, "<meta property=\"og:image\" content=\"([^\"]+)\"");
            String source = extract(html, "data-source=\"([^\"]+)\"");
            String playUrl = null;
            if (source != null) {
                playUrl = decodeSource(source);
            }
            if (playUrl == null) {
                // 尝试 video src
                playUrl = extract(html, "<video[^>]+src=\"([^\"]+)\"");
            }
            result.put("vod_id", vodId);
            result.put("vod_name", title != null ? title : "");
            result.put("vod_pic", img != null ? img : "");
            result.put("vod_play_url", playUrl != null ? playUrl : "");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    // ========== 播放接口 ==========
    public String play(String flag, String vid, String... flags) {
        Map<String, Object> detail = detail(vid);
        return (String) detail.getOrDefault("vod_play_url", "");
    }

    // ========== 首页 ==========
    public List<Map<String, String>> home(int page) {
        String url = page == 1 ? BASE_URL : BASE_URL + "/?page=" + page;
        return getList(url);
    }

    // ========== 分类 ==========
    public List<Map<String, String>> category(String tid, int page) {
        String url = page == 1 ? BASE_URL + "/categories/" + tid : BASE_URL + "/categories/" + tid + "?page=" + page;
        return getList(url);
    }

    // ========== 搜索 ==========
    public List<Map<String, String>> search(String keyword, int page) {
        String url = BASE_URL + "/search?s=" + keyword + "&page=" + page;
        return getList(url);
    }

    // ========== 初始化方法（TVBox 会调用） ==========
    public static Spider init(Map<String, String> config) {
        return new Spider();
    }
}
