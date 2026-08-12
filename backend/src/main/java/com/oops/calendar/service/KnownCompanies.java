package com.oops.calendar.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 内置知名公司信息表:公司全称 + 行业分类(中文)。
 * 财报日历接口(Finnhub/FMP)不含公司名与行业,知名公司在此补充,
 * 其余公司全称由 {@link FinnhubSymbolService} 的全量列表提供。
 */
public final class KnownCompanies {

    public static final class CompanyInfo {
        public final String name;
        public final String industry;

        CompanyInfo(String name, String industry) {
            this.name = name;
            this.industry = industry;
        }
    }

    private static final Map<String, CompanyInfo> KNOWN = new HashMap<>();

    static {
        put("NBIS", "Nebius Group N.V.", "AI 基础设施");
        put("CBRS", "Cerebras Systems Inc.", "半导体 / AI 芯片");
        put("NVDA", "NVIDIA Corp.", "半导体 / AI 芯片");
        put("AMD", "Advanced Micro Devices Inc.", "半导体");
        put("INTC", "Intel Corp.", "半导体");
        put("QCOM", "Qualcomm Inc.", "半导体 / 通信");
        put("AVGO", "Broadcom Inc.", "半导体");
        put("MU", "Micron Technology Inc.", "半导体 / 存储");
        put("TXN", "Texas Instruments Inc.", "半导体");
        put("ASML", "ASML Holding N.V.", "半导体设备");
        put("AMAT", "Applied Materials Inc.", "半导体设备");
        put("LRCX", "Lam Research Corp.", "半导体设备");
        put("KLAC", "KLA Corp.", "半导体设备");
        put("ARM", "Arm Holdings plc", "半导体 IP");
        put("SMCI", "Super Micro Computer Inc.", "服务器 / AI 硬件");
        put("DELL", "Dell Technologies Inc.", "硬件 / 服务器");
        put("HPQ", "HP Inc.", "硬件");
        put("STX", "Seagate Technology Holdings", "存储");
        put("WDC", "Western Digital Corp.", "存储");
        put("AAPL", "Apple Inc.", "消费电子 / 科技");
        put("MSFT", "Microsoft Corp.", "软件 / 云");
        put("GOOGL", "Alphabet Inc.", "互联网 / 云");
        put("GOOG", "Alphabet Inc.", "互联网 / 云");
        put("AMZN", "Amazon.com Inc.", "电商 / 云");
        put("META", "Meta Platforms Inc.", "互联网 / 社交");
        put("TSLA", "Tesla Inc.", "汽车 / 新能源");
        put("NFLX", "Netflix Inc.", "流媒体 / 娱乐");
        put("CRM", "Salesforce Inc.", "软件 / SaaS");
        put("ORCL", "Oracle Corp.", "软件 / 数据库");
        put("ADBE", "Adobe Inc.", "软件 / 创意工具");
        put("INTU", "Intuit Inc.", "软件 / 金融科技");
        put("NOW", "ServiceNow Inc.", "软件 / SaaS");
        put("SNOW", "Snowflake Inc.", "软件 / 数据云");
        put("PLTR", "Palantir Technologies Inc.", "软件 / 数据分析");
        put("SHOP", "Shopify Inc.", "电商 SaaS");
        put("UBER", "Uber Technologies Inc.", "出行 / 配送");
        put("ABNB", "Airbnb Inc.", "在线旅游");
        put("PYPL", "PayPal Holdings Inc.", "金融科技 / 支付");
        put("SQ", "Block Inc.", "金融科技 / 支付");
        put("COIN", "Coinbase Global Inc.", "加密货币交易");
        put("MSTR", "MicroStrategy Inc.", "软件 / 比特币");
        put("HOOD", "Robinhood Markets Inc.", "金融科技 / 券商");
        put("CSCO", "Cisco Systems Inc.", "网络设备");
        put("T", "AT&T Inc.", "电信");
        put("VZ", "Verizon Communications Inc.", "电信");
        put("TMUS", "T-Mobile US Inc.", "电信");
        put("JPM", "JPMorgan Chase & Co.", "金融 / 银行");
        put("BAC", "Bank of America Corp.", "金融 / 银行");
        put("C", "Citigroup Inc.", "金融 / 银行");
        put("WFC", "Wells Fargo & Co.", "金融 / 银行");
        put("GS", "Goldman Sachs Group Inc.", "金融 / 投行");
        put("MS", "Morgan Stanley", "金融 / 投行");
        put("V", "Visa Inc.", "金融科技 / 支付");
        put("MA", "Mastercard Inc.", "金融科技 / 支付");
        put("AXP", "American Express Co.", "金融 / 信用卡");
        put("WMT", "Walmart Inc.", "零售 / 大卖场");
        put("COST", "Costco Wholesale Corp.", "零售 / 会员制");
        put("TGT", "Target Corp.", "零售");
        put("HD", "Home Depot Inc.", "零售 / 家居建材");
        put("LOW", "Lowe's Companies Inc.", "零售 / 家居建材");
        put("MCD", "McDonald's Corp.", "餐饮 / 快餐");
        put("SBUX", "Starbucks Corp.", "餐饮 / 咖啡");
        put("NKE", "Nike Inc.", "消费 / 运动服饰");
        put("DIS", "Walt Disney Co.", "传媒 / 娱乐");
        put("CMCSA", "Comcast Corp.", "传媒 / 通信");
        put("XOM", "Exxon Mobil Corp.", "能源 / 石油");
        put("CVX", "Chevron Corp.", "能源 / 石油");
        put("COP", "ConocoPhillips", "能源 / 石油");
        put("SLB", "Schlumberger Ltd.", "能源服务");
        put("BA", "Boeing Co.", "航空航天 / 军工");
        put("GE", "GE Aerospace", "航空航天 / 工业");
        put("CAT", "Caterpillar Inc.", "工业 / 工程机械");
        put("DE", "Deere & Co.", "工业 / 农机");
        put("MMM", "3M Co.", "工业 / 材料");
        put("HON", "Honeywell International Inc.", "工业 / 自动化");
        put("UNP", "Union Pacific Corp.", "运输 / 铁路");
        put("FDX", "FedEx Corp.", "运输 / 物流");
        put("UPS", "United Parcel Service Inc.", "运输 / 物流");
        put("JNJ", "Johnson & Johnson", "医疗 / 制药");
        put("PFE", "Pfizer Inc.", "医疗 / 制药");
        put("MRK", "Merck & Co. Inc.", "医疗 / 制药");
        put("ABBV", "AbbVie Inc.", "医疗 / 制药");
        put("LLY", "Eli Lilly and Co.", "医疗 / 制药");
        put("UNH", "UnitedHealth Group Inc.", "医疗 / 保险");
        put("CVS", "CVS Health Corp.", "医疗 / 零售药房");
        put("AMGN", "Amgen Inc.", "医疗 / 生物制药");
        put("GILD", "Gilead Sciences Inc.", "医疗 / 生物制药");
        put("BMY", "Bristol-Myers Squibb Co.", "医疗 / 制药");
        put("TMO", "Thermo Fisher Scientific Inc.", "医疗 / 生命科学");
        put("DHR", "Danaher Corp.", "医疗 / 生命科学");
        put("ABT", "Abbott Laboratories", "医疗 / 器械");
        put("PG", "Procter & Gamble Co.", "消费 / 日化");
        put("KO", "Coca-Cola Co.", "消费 / 饮料");
        put("PEP", "PepsiCo Inc.", "消费 / 食品饮料");
        put("PM", "Philip Morris International", "消费 / 烟草");
        put("MO", "Altria Group Inc.", "消费 / 烟草");
        put("CL", "Colgate-Palmolive Co.", "消费 / 日化");
        put("KMB", "Kimberly-Clark Corp.", "消费 / 纸品");
        put("TSM", "Taiwan Semiconductor Manufacturing", "半导体 / 代工");
        put("BABA", "Alibaba Group Holding Ltd.", "互联网 / 电商");
        put("PDD", "PDD Holdings Inc.", "互联网 / 电商");
        put("JD", "JD.com Inc.", "互联网 / 电商");
        put("BIDU", "Baidu Inc.", "互联网 / AI");
        put("NIO", "NIO Inc.", "汽车 / 新能源");
        put("XPEV", "XPeng Inc.", "汽车 / 新能源");
        put("LI", "Li Auto Inc.", "汽车 / 新能源");
        put("YUMC", "Yum China Holdings", "餐饮");
        put("BILI", "Bilibili Inc.", "互联网 / 视频");
        put("TME", "Tencent Music Entertainment", "互联网 / 音乐");
        put("NTES", "NetEase Inc.", "互联网 / 游戏");
        put("ZTO", "ZTO Express (Cayman) Inc.", "物流 / 快递");
        put("BEKE", "KE Holdings Inc.", "房产经纪");
        put("RIOT", "Riot Platforms Inc.", "加密货币 / 挖矿");
        put("MARA", "Marathon Digital Holdings", "加密货币 / 挖矿");
        put("CLSK", "CleanSpark Inc.", "加密货币 / 挖矿");
        put("AAL", "American Airlines Group", "航空");
        put("DAL", "Delta Air Lines Inc.", "航空");
        put("UAL", "United Airlines Holdings", "航空");
        put("ET", "Energy Transfer LP", "能源 / 管道运输");
        put("ENB", "Enbridge Inc.", "能源 / 管道运输");
        put("TJX", "TJX Companies Inc.", "零售 / 折扣");
        put("ROST", "Ross Stores Inc.", "零售 / 折扣");
        put("EA", "Electronic Arts Inc.", "游戏");
        put("TTWO", "Take-Two Interactive Software", "游戏");
    }

    private static void put(String symbol, String name, String industry) {
        KNOWN.put(symbol, new CompanyInfo(name, industry));
    }

    public static CompanyInfo get(String symbol) {
        return symbol == null ? null : KNOWN.get(symbol.toUpperCase());
    }

    public static Map<String, CompanyInfo> all() {
        return Collections.unmodifiableMap(KNOWN);
    }

    private KnownCompanies() {
    }
}
