package com.oops.calendar.service.news;

import com.oops.calendar.dto.NewsItem;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 格隆汇 要闻(港股/A股研究)。
 * 解析 https://www.gelonghui.com/news/ 首页的文章卡片,参考 newsnow 的 cheerio 选择器。
 */
@Component
public class GelonghuiNewsSource implements NewsSource {

    private static final String BASE = "https://www.gelonghui.com";

    private final NewsHttpClient http;

    public GelonghuiNewsSource(NewsHttpClient http) {
        this.http = http;
    }

    @Override
    public String key() {
        return "gelonghui";
    }

    @Override
    public String name() {
        return "格隆汇";
    }

    @Override
    public String icon() {
        return "gelonghui.png";
    }

    @Override
    public List<NewsItem> fetch() {
        String html = http.getText(BASE + "/news/");
        Document doc = Jsoup.parse(html);
        Elements blocks = doc.select(".article-content");
        List<NewsItem> items = new ArrayList<>();
        for (Element el : blocks) {
            Element a = el.selectFirst(".detail-right>a");
            Element h2 = a != null ? a.selectFirst("h2") : null;
            if (a == null || h2 == null) {
                continue;
            }
            String href = trimToNull(a.attr("href"));
            String title = trimToNull(h2.text());
            if (href == null || title == null) {
                continue;
            }
            Elements timeSpans = el.select(".time > span");
            String timeText = timeSpans.size() >= 3 ? trimToNull(timeSpans.get(2).text()) : null;
            String info = timeSpans.size() >= 1 ? trimToNull(timeSpans.get(0).text()) : null;

            NewsItem item = new NewsItem();
            item.setId("gelonghui:" + href);
            item.setTitle(title);
            item.setUrl(href.startsWith("http") ? href : BASE + href);
            item.setPubDate(RelativeTime.parse(timeText));
            item.setSource(key());
            item.setSourceName(name());
            item.setSummary(info);
            items.add(item);
        }
        return items;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
