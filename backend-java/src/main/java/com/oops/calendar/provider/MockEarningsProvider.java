package com.oops.calendar.provider;

import com.oops.calendar.dto.EarningsEvent;
import com.oops.calendar.dto.Session;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 确定性演示数据源:同一日期区间每次生成完全一致的数据。
 * 工作日每天 2-5 条财报;盘前 45%、盘后 50%、盘中 5%;约 30% 已公布。
 * 未配置 FMP_API_KEY 时启用,保证应用开箱可演示。
 */
@Component
public class MockEarningsProvider implements EarningsProvider {

    static final String[] SYMBOLS = {
            "AAPL", "MSFT", "GOOGL", "AMZN", "META", "NVDA", "TSLA", "NFLX",
            "JPM", "V", "UNH", "XOM", "WMT", "JNJ", "PG", "KO", "DIS", "CRM",
            "ORCL", "AMD", "INTC", "QCOM", "ADBE", "PYPL", "BA", "GE", "PFE",
            "T", "CSCO", "CMCSA", "NBIS", "CBRS"
    };

    static final String[] NAMES = {
            "Apple Inc.", "Microsoft Corp.", "Alphabet Inc.", "Amazon.com Inc.",
            "Meta Platforms Inc.", "NVIDIA Corp.", "Tesla Inc.", "Netflix Inc.",
            "JPMorgan Chase & Co.", "Visa Inc.", "UnitedHealth Group Inc.",
            "Exxon Mobil Corp.", "Walmart Inc.", "Johnson & Johnson",
            "Procter & Gamble Co.", "Coca-Cola Co.", "Walt Disney Co.",
            "Salesforce Inc.", "Oracle Corp.", "Advanced Micro Devices Inc.",
            "Intel Corp.", "Qualcomm Inc.", "Adobe Inc.", "PayPal Holdings Inc.",
            "Boeing Co.", "GE Aerospace", "Pfizer Inc.", "AT&T Inc.",
            "Cisco Systems Inc.", "Comcast Corp.", "Nebius Group N.V.", "Cerebras Systems Inc."
    };

    @Override
    public String source() {
        return "mock";
    }

    @Override
    public List<EarningsEvent> fetch(LocalDate from, LocalDate to) {
        List<EarningsEvent> events = new ArrayList<>();
        LocalDate day = from;
        while (!day.isAfter(to)) {
            events.addAll(generateDay(day));
            day = day.plusDays(1);
        }
        return events;
    }

    /** 生成某一天的事件;以 (date, index) 为种子,保证确定性。 */
    private List<EarningsEvent> generateDay(LocalDate date) {
        List<EarningsEvent> events = new ArrayList<>();
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return events; // 美股财报只在交易日
        }
        Random rnd = new Random(seed(date));
        int count = 2 + rnd.nextInt(4); // 2..5
        // 洗牌代码池,保证同一天内代码不重复(一家公司一天只报一次)
        List<String> pool = new ArrayList<>(Arrays.asList(SYMBOLS));
        Collections.shuffle(pool, rnd);
        for (int i = 0; i < count; i++) {
            String symbol = pool.get(i);
            Session session = pickSession(rnd);
            boolean confirmed = rnd.nextInt(10) < 3;
            BigDecimal epsEst = BigDecimal.valueOf(0.10 + rnd.nextInt(400) / 100.0)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal revEst = BigDecimal.valueOf(500 + rnd.nextInt(20000)).setScale(0, RoundingMode.HALF_UP);
            BigDecimal eps = confirmed ? epsEst.add(BigDecimal.valueOf(rnd.nextInt(61) - 30).movePointLeft(2))
                    .setScale(2, RoundingMode.HALF_UP) : null;
            BigDecimal rev = confirmed ? revEst.add(BigDecimal.valueOf(rnd.nextInt(2001) - 1000)) : null;
            events.add(new EarningsEvent(date.toString(), symbol, NAMES[idxOf(symbol)], session, confirmed,
                    eps, epsEst, rev, revEst, source()));
        }
        return events;
    }

    private int idxOf(String symbol) {
        for (int i = 0; i < SYMBOLS.length; i++) {
            if (SYMBOLS[i].equals(symbol)) {
                return i;
            }
        }
        return 0;
    }

    private Session pickSession(Random rnd) {
        int r = rnd.nextInt(20);
        if (r < 9) {
            return Session.BMO;  // 45% 盘前
        }
        if (r < 19) {
            return Session.AMC;  // 50% 盘后
        }
        return Session.DNH;      // 5% 盘中
    }

    /** date + 固定偏移 -> 稳定种子。 */
    private long seed(LocalDate date) {
        return date.toEpochDay() * 2654435761L ^ 0x9E3779B97F4A7C15L;
    }
}
