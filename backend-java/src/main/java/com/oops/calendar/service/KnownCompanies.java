package com.oops.calendar.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 内置知名公司信息表:英文全称 + 中文名称 + 行业分类(中文)。
 * 财报日历接口(Finnhub/FMP)不含公司名与行业,知名公司在此补充,
 * 其余公司全称由 {@link FinnhubSymbolService} 的全量列表提供。
 */
public final class KnownCompanies {

    public static final class CompanyInfo {
        public final String name;      // 英文全称
        public final String nameZh;    // 中文名称(无标准译名时为 null)
        public final String industry;  // 行业分类(中文)

        CompanyInfo(String name, String nameZh, String industry) {
            this.name = name;
            this.nameZh = nameZh;
            this.industry = industry;
        }
    }

    private static final Map<String, CompanyInfo> KNOWN = new HashMap<>();

    static {
        // ===== AI / 半导体 =====
        put("NBIS", "Nebius Group N.V.", null, "AI 基础设施");
        put("CBRS", "Cerebras Systems Inc.", null, "半导体 / AI 芯片");
        put("NVDA", "NVIDIA Corp.", "英伟达", "半导体 / AI 芯片");
        put("AMD", "Advanced Micro Devices Inc.", "超威半导体", "半导体");
        put("INTC", "Intel Corp.", "英特尔", "半导体");
        put("QCOM", "Qualcomm Inc.", "高通", "半导体 / 通信");
        put("AVGO", "Broadcom Inc.", "博通", "半导体");
        put("MU", "Micron Technology Inc.", "美光科技", "半导体 / 存储");
        put("TXN", "Texas Instruments Inc.", "德州仪器", "半导体");
        put("ASML", "ASML Holding N.V.", "阿斯麦", "半导体设备");
        put("AMAT", "Applied Materials Inc.", "应用材料", "半导体设备");
        put("LRCX", "Lam Research Corp.", "泛林集团", "半导体设备");
        put("KLAC", "KLA Corp.", "科磊", "半导体设备");
        put("ARM", "Arm Holdings plc", "安谋", "半导体 IP");
        put("SMCI", "Super Micro Computer Inc.", "超微电脑", "服务器 / AI 硬件");
        put("DELL", "Dell Technologies Inc.", "戴尔", "硬件 / 服务器");
        put("HPQ", "HP Inc.", "惠普", "硬件");
        put("STX", "Seagate Technology Holdings", "希捷", "存储");
        put("WDC", "Western Digital Corp.", "西部数据", "存储");
        put("TSM", "Taiwan Semiconductor Manufacturing", "台积电", "半导体 / 代工");
        // ===== 科技巨头 =====
        put("AAPL", "Apple Inc.", "苹果", "消费电子 / 科技");
        put("MSFT", "Microsoft Corp.", "微软", "软件 / 云");
        put("GOOGL", "Alphabet Inc.", "谷歌", "互联网 / 云");
        put("GOOG", "Alphabet Inc.", "谷歌", "互联网 / 云");
        put("AMZN", "Amazon.com Inc.", "亚马逊", "电商 / 云");
        put("META", "Meta Platforms Inc.", "Meta 平台", "互联网 / 社交");
        put("TSLA", "Tesla Inc.", "特斯拉", "汽车 / 新能源");
        put("NFLX", "Netflix Inc.", "奈飞", "流媒体 / 娱乐");
        put("CRM", "Salesforce Inc.", "赛富时", "软件 / SaaS");
        put("ORCL", "Oracle Corp.", "甲骨文", "软件 / 数据库");
        put("ADBE", "Adobe Inc.", "奥多比", "软件 / 创意工具");
        put("INTU", "Intuit Inc.", "财捷", "软件 / 金融科技");
        put("NOW", "ServiceNow Inc.", null, "软件 / SaaS");
        put("SNOW", "Snowflake Inc.", null, "软件 / 数据云");
        put("PLTR", "Palantir Technologies Inc.", null, "软件 / 数据分析");
        put("SHOP", "Shopify Inc.", null, "电商 SaaS");
        put("UBER", "Uber Technologies Inc.", "优步", "出行 / 配送");
        put("ABNB", "Airbnb Inc.", "爱彼迎", "在线旅游");
        put("PYPL", "PayPal Holdings Inc.", "贝宝", "金融科技 / 支付");
        put("SQ", "Block Inc.", null, "金融科技 / 支付");
        put("COIN", "Coinbase Global Inc.", null, "加密货币交易");
        put("MSTR", "MicroStrategy Inc.", null, "软件 / 比特币");
        put("HOOD", "Robinhood Markets Inc.", null, "金融科技 / 券商");
        put("CSCO", "Cisco Systems Inc.", "思科", "网络设备");
        put("T", "AT&T Inc.", "美国电话电报", "电信");
        put("VZ", "Verizon Communications Inc.", "威瑞森", "电信");
        put("TMUS", "T-Mobile US Inc.", null, "电信");
        // ===== 金融 =====
        put("JPM", "JPMorgan Chase & Co.", "摩根大通", "金融 / 银行");
        put("BAC", "Bank of America Corp.", "美国银行", "金融 / 银行");
        put("C", "Citigroup Inc.", "花旗集团", "金融 / 银行");
        put("WFC", "Wells Fargo & Co.", "富国银行", "金融 / 银行");
        put("GS", "Goldman Sachs Group Inc.", "高盛", "金融 / 投行");
        put("MS", "Morgan Stanley", "摩根士丹利", "金融 / 投行");
        put("V", "Visa Inc.", "维萨", "金融科技 / 支付");
        put("MA", "Mastercard Inc.", "万事达", "金融科技 / 支付");
        put("AXP", "American Express Co.", "美国运通", "金融 / 信用卡");
        // ===== 零售 / 消费 =====
        put("WMT", "Walmart Inc.", "沃尔玛", "零售 / 大卖场");
        put("COST", "Costco Wholesale Corp.", "好市多", "零售 / 会员制");
        put("TGT", "Target Corp.", "塔吉特", "零售");
        put("HD", "Home Depot Inc.", "家得宝", "零售 / 家居建材");
        put("LOW", "Lowe's Companies Inc.", "劳氏", "零售 / 家居建材");
        put("MCD", "McDonald's Corp.", "麦当劳", "餐饮 / 快餐");
        put("SBUX", "Starbucks Corp.", "星巴克", "餐饮 / 咖啡");
        put("NKE", "Nike Inc.", "耐克", "消费 / 运动服饰");
        put("DIS", "Walt Disney Co.", "迪士尼", "传媒 / 娱乐");
        put("CMCSA", "Comcast Corp.", "康卡斯特", "传媒 / 通信");
        put("TJX", "TJX Companies Inc.", null, "零售 / 折扣");
        put("ROST", "Ross Stores Inc.", "罗斯百货", "零售 / 折扣");
        // ===== 能源 / 工业 / 运输 =====
        put("XOM", "Exxon Mobil Corp.", "埃克森美孚", "能源 / 石油");
        put("CVX", "Chevron Corp.", "雪佛龙", "能源 / 石油");
        put("COP", "ConocoPhillips", "康菲石油", "能源 / 石油");
        put("SLB", "Schlumberger Ltd.", "斯伦贝谢", "能源服务");
        put("BA", "Boeing Co.", "波音", "航空航天 / 军工");
        put("GE", "GE Aerospace", "通用电气航空", "航空航天 / 工业");
        put("CAT", "Caterpillar Inc.", "卡特彼勒", "工业 / 工程机械");
        put("DE", "Deere & Co.", "迪尔", "工业 / 农机");
        put("MMM", "3M Co.", null, "工业 / 材料");
        put("HON", "Honeywell International Inc.", "霍尼韦尔", "工业 / 自动化");
        put("UNP", "Union Pacific Corp.", "联合太平洋", "运输 / 铁路");
        put("FDX", "FedEx Corp.", "联邦快递", "运输 / 物流");
        put("UPS", "United Parcel Service Inc.", "联合包裹", "运输 / 物流");
        put("AAL", "American Airlines Group", "美国航空", "航空");
        put("DAL", "Delta Air Lines Inc.", "达美航空", "航空");
        put("UAL", "United Airlines Holdings", "美联航", "航空");
        put("ET", "Energy Transfer LP", null, "能源 / 管道运输");
        put("ENB", "Enbridge Inc.", "安桥", "能源 / 管道运输");
        // ===== 医疗 =====
        put("JNJ", "Johnson & Johnson", "强生", "医疗 / 制药");
        put("PFE", "Pfizer Inc.", "辉瑞", "医疗 / 制药");
        put("MRK", "Merck & Co. Inc.", "默沙东", "医疗 / 制药");
        put("ABBV", "AbbVie Inc.", "艾伯维", "医疗 / 制药");
        put("LLY", "Eli Lilly and Co.", "礼来", "医疗 / 制药");
        put("UNH", "UnitedHealth Group Inc.", "联合健康", "医疗 / 保险");
        put("CVS", "CVS Health Corp.", null, "医疗 / 零售药房");
        put("AMGN", "Amgen Inc.", "安进", "医疗 / 生物制药");
        put("GILD", "Gilead Sciences Inc.", "吉利德", "医疗 / 生物制药");
        put("BMY", "Bristol-Myers Squibb Co.", "百时美施贵宝", "医疗 / 制药");
        put("TMO", "Thermo Fisher Scientific Inc.", "赛默飞世尔", "医疗 / 生命科学");
        put("DHR", "Danaher Corp.", "丹纳赫", "医疗 / 生命科学");
        put("ABT", "Abbott Laboratories", "雅培", "医疗 / 器械");
        // ===== 消费品牌 =====
        put("PG", "Procter & Gamble Co.", "宝洁", "消费 / 日化");
        put("KO", "Coca-Cola Co.", "可口可乐", "消费 / 饮料");
        put("PEP", "PepsiCo Inc.", "百事", "消费 / 食品饮料");
        put("PM", "Philip Morris International", "菲利普莫里斯", "消费 / 烟草");
        put("MO", "Altria Group Inc.", "奥驰亚", "消费 / 烟草");
        put("CL", "Colgate-Palmolive Co.", "高露洁", "消费 / 日化");
        put("KMB", "Kimberly-Clark Corp.", "金佰利", "消费 / 纸品");
        // ===== 中概股 =====
        put("BABA", "Alibaba Group Holding Ltd.", "阿里巴巴", "互联网 / 电商");
        put("PDD", "PDD Holdings Inc.", "拼多多", "互联网 / 电商");
        put("JD", "JD.com Inc.", "京东", "互联网 / 电商");
        put("BIDU", "Baidu Inc.", "百度", "互联网 / AI");
        put("NIO", "NIO Inc.", "蔚来", "汽车 / 新能源");
        put("XPEV", "XPeng Inc.", "小鹏汽车", "汽车 / 新能源");
        put("LI", "Li Auto Inc.", "理想汽车", "汽车 / 新能源");
        put("YUMC", "Yum China Holdings", "百胜中国", "餐饮");
        put("BILI", "Bilibili Inc.", "哔哩哔哩", "互联网 / 视频");
        put("TME", "Tencent Music Entertainment", "腾讯音乐", "互联网 / 音乐");
        put("NTES", "NetEase Inc.", "网易", "互联网 / 游戏");
        put("ZTO", "ZTO Express (Cayman) Inc.", "中通快递", "物流 / 快递");
        put("BEKE", "KE Holdings Inc.", "贝壳", "房产经纪");
        // ===== 其他 =====
        put("RIOT", "Riot Platforms Inc.", null, "加密货币 / 挖矿");
        put("MARA", "Marathon Digital Holdings", null, "加密货币 / 挖矿");
        put("CLSK", "CleanSpark Inc.", null, "加密货币 / 挖矿");
        put("EA", "Electronic Arts Inc.", "艺电", "游戏");
        put("TTWO", "Take-Two Interactive Software", null, "游戏");
    }

    private static void put(String symbol, String name, String nameZh, String industry) {
        KNOWN.put(symbol, new CompanyInfo(name, nameZh, industry));
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
