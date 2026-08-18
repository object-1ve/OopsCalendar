package com.oops.calendar.service.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oops.calendar.config.NewsProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 新闻抓取公共 HTTP 客户端:统一 UA、超时、UTF-8 解码与 JSON 解析。
 */
@Component
public class NewsHttpClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public NewsHttpClient(NewsProperties props, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = objectMapper;
    }

    public String getText(String url) {
        return getText(url, Collections.emptyMap());
    }

    /** GET 并按 UTF-8 解码文本。 */
    public String getText(String url, Map<String, String> headers) {
        ResponseEntity<byte[]> resp = exchange(url, headers);
        byte[] body = resp.getBody();
        return new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8);
    }

    /** GET 并解析 JSON。 */
    public JsonNode getJson(String url) {
        return getJson(url, Collections.emptyMap());
    }

    public JsonNode getJson(String url, Map<String, String> headers) {
        String text = getText(url, headers);
        return parse(text, url);
    }

    /** 纯文本解析(JSONP 剥壳后的 JSON 字符串等),不发起请求。 */
    public JsonNode parse(String text) {
        return parse(text, "(text)");
    }

    private JsonNode parse(String text, String origin) {
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            throw new NewsSourceException("JSON 解析失败: " + origin, e);
        }
    }

    /** GET 并返回 Set-Cookie 列表(如雪球热股需先取 cookie)。 */
    public List<String> getSetCookies(String url) {
        ResponseEntity<byte[]> resp = exchange(url, Collections.emptyMap());
        List<String> cookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
        return cookies == null ? Collections.emptyList() : cookies;
    }

    private ResponseEntity<byte[]> exchange(String url, Map<String, String> headers) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.USER_AGENT, USER_AGENT);
        h.set(HttpHeaders.ACCEPT, "application/json, text/plain, */*");
        headers.forEach(h::set);
        try {
            return restTemplate.exchange(URI.create(url), HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        } catch (RestClientException e) {
            throw new NewsSourceException("请求失败: " + url + " (" + e.getMessage() + ")", e);
        }
    }
}
