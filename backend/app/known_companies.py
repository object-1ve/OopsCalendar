# -*- coding: utf-8 -*-
"""内置知名公司信息表:英文全称 + 中文名称 + 行业分类(中文)。

财报日历接口(Finnhub/FMP)不含公司名与行业,知名公司在此补充,
其余公司全称由 FinnhubSymbolService 的全量列表提供。
"""
from __future__ import annotations
from typing import NamedTuple


class CompanyInfo(NamedTuple):
    name: str
    name_zh: str | None
    industry: str


KNOWN: dict[str, CompanyInfo] = {
    "NVDA": CompanyInfo("NVIDIA Corp.", "英伟达", "半导体 / AI 芯片"),
    "AMD": CompanyInfo("Advanced Micro Devices Inc.", "超威半导体", "半导体"),
    "INTC": CompanyInfo("Intel Corp.", "英特尔", "半导体"),
    "QCOM": CompanyInfo("Qualcomm Inc.", "高通", "半导体 / 通信"),
    "AVGO": CompanyInfo("Broadcom Inc.", "博通", "半导体"),
    "MU": CompanyInfo("Micron Technology Inc.", "美光科技", "半导体 / 存储"),
    "TXN": CompanyInfo("Texas Instruments Inc.", "德州仪器", "半导体"),
    "ASML": CompanyInfo("ASML Holding N.V.", "阿斯麦", "半导体设备"),
    "AMAT": CompanyInfo("Applied Materials Inc.", "应用材料", "半导体设备"),
    "LRCX": CompanyInfo("Lam Research Corp.", "泛林集团", "半导体设备"),
    "KLAC": CompanyInfo("KLA Corp.", "科磊", "半导体设备"),
    "ARM": CompanyInfo("Arm Holdings plc", "安谋", "半导体 IP"),
    "SMCI": CompanyInfo("Super Micro Computer Inc.", "超微电脑", "服务器 / AI 硬件"),
    "DELL": CompanyInfo("Dell Technologies Inc.", "戴尔", "硬件 / 服务器"),
    "HPQ": CompanyInfo("HP Inc.", "惠普", "硬件"),
    "STX": CompanyInfo("Seagate Technology Holdings", "希捷", "存储"),
    "WDC": CompanyInfo("Western Digital Corp.", "西部数据", "存储"),
    "TSM": CompanyInfo("Taiwan Semiconductor Manufacturing", "台积电", "半导体 / 代工"),
    "AAPL": CompanyInfo("Apple Inc.", "苹果", "消费电子 / 科技"),
    "MSFT": CompanyInfo("Microsoft Corp.", "微软", "软件 / 云"),
    "GOOGL": CompanyInfo("Alphabet Inc.", "谷歌", "互联网 / 云"),
    "GOOG": CompanyInfo("Alphabet Inc.", "谷歌", "互联网 / 云"),
    "AMZN": CompanyInfo("Amazon.com Inc.", "亚马逊", "电商 / 云"),
    "META": CompanyInfo("Meta Platforms Inc.", "Meta 平台", "互联网 / 社交"),
    "TSLA": CompanyInfo("Tesla Inc.", "特斯拉", "汽车 / 新能源"),
    "NFLX": CompanyInfo("Netflix Inc.", "奈飞", "流媒体 / 娱乐"),
    "CRM": CompanyInfo("Salesforce Inc.", "赛富时", "软件 / SaaS"),
    "ORCL": CompanyInfo("Oracle Corp.", "甲骨文", "软件 / 数据库"),
    "ADBE": CompanyInfo("Adobe Inc.", "奥多比", "软件 / 创意工具"),
    "INTU": CompanyInfo("Intuit Inc.", "财捷", "软件 / 金融科技"),
    "UBER": CompanyInfo("Uber Technologies Inc.", "优步", "出行 / 配送"),
    "ABNB": CompanyInfo("Airbnb Inc.", "爱彼迎", "在线旅游"),
    "PYPL": CompanyInfo("PayPal Holdings Inc.", "贝宝", "金融科技 / 支付"),
    "CSCO": CompanyInfo("Cisco Systems Inc.", "思科", "网络设备"),
    "T": CompanyInfo("AT&T Inc.", "美国电话电报", "电信"),
    "VZ": CompanyInfo("Verizon Communications Inc.", "威瑞森", "电信"),
    "JPM": CompanyInfo("JPMorgan Chase & Co.", "摩根大通", "金融 / 银行"),
    "BAC": CompanyInfo("Bank of America Corp.", "美国银行", "金融 / 银行"),
    "C": CompanyInfo("Citigroup Inc.", "花旗集团", "金融 / 银行"),
    "WFC": CompanyInfo("Wells Fargo & Co.", "富国银行", "金融 / 银行"),
    "GS": CompanyInfo("Goldman Sachs Group Inc.", "高盛", "金融 / 投行"),
    "MS": CompanyInfo("Morgan Stanley", "摩根士丹利", "金融 / 投行"),
    "V": CompanyInfo("Visa Inc.", "维萨", "金融科技 / 支付"),
    "MA": CompanyInfo("Mastercard Inc.", "万事达", "金融科技 / 支付"),
    "AXP": CompanyInfo("American Express Co.", "美国运通", "金融 / 信用卡"),
    "WMT": CompanyInfo("Walmart Inc.", "沃尔玛", "零售 / 大卖场"),
    "COST": CompanyInfo("Costco Wholesale Corp.", "好市多", "零售 / 会员制"),
    "TGT": CompanyInfo("Target Corp.", "塔吉特", "零售"),
    "HD": CompanyInfo("Home Depot Inc.", "家得宝", "零售 / 家居建材"),
    "LOW": CompanyInfo("Lowe's Companies Inc.", "劳氏", "零售 / 家居建材"),
    "MCD": CompanyInfo("McDonald's Corp.", "麦当劳", "餐饮 / 快餐"),
    "SBUX": CompanyInfo("Starbucks Corp.", "星巴克", "餐饮 / 咖啡"),
    "NKE": CompanyInfo("Nike Inc.", "耐克", "消费 / 运动服饰"),
    "DIS": CompanyInfo("Walt Disney Co.", "迪士尼", "传媒 / 娱乐"),
    "CMCSA": CompanyInfo("Comcast Corp.", "康卡斯特", "传媒 / 通信"),
    "ROST": CompanyInfo("Ross Stores Inc.", "罗斯百货", "零售 / 折扣"),
    "XOM": CompanyInfo("Exxon Mobil Corp.", "埃克森美孚", "能源 / 石油"),
    "CVX": CompanyInfo("Chevron Corp.", "雪佛龙", "能源 / 石油"),
    "COP": CompanyInfo("ConocoPhillips", "康菲石油", "能源 / 石油"),
    "SLB": CompanyInfo("Schlumberger Ltd.", "斯伦贝谢", "能源服务"),
    "BA": CompanyInfo("Boeing Co.", "波音", "航空航天 / 军工"),
    "GE": CompanyInfo("GE Aerospace", "通用电气航空", "航空航天 / 工业"),
    "CAT": CompanyInfo("Caterpillar Inc.", "卡特彼勒", "工业 / 工程机械"),
    "DE": CompanyInfo("Deere & Co.", "迪尔", "工业 / 农机"),
    "HON": CompanyInfo("Honeywell International Inc.", "霍尼韦尔", "工业 / 自动化"),
    "UNP": CompanyInfo("Union Pacific Corp.", "联合太平洋", "运输 / 铁路"),
    "FDX": CompanyInfo("FedEx Corp.", "联邦快递", "运输 / 物流"),
    "UPS": CompanyInfo("United Parcel Service Inc.", "联合包裹", "运输 / 物流"),
    "AAL": CompanyInfo("American Airlines Group", "美国航空", "航空"),
    "DAL": CompanyInfo("Delta Air Lines Inc.", "达美航空", "航空"),
    "UAL": CompanyInfo("United Airlines Holdings", "美联航", "航空"),
    "ENB": CompanyInfo("Enbridge Inc.", "安桥", "能源 / 管道运输"),
    "JNJ": CompanyInfo("Johnson & Johnson", "强生", "医疗 / 制药"),
    "PFE": CompanyInfo("Pfizer Inc.", "辉瑞", "医疗 / 制药"),
    "MRK": CompanyInfo("Merck & Co. Inc.", "默沙东", "医疗 / 制药"),
    "ABBV": CompanyInfo("AbbVie Inc.", "艾伯维", "医疗 / 制药"),
    "LLY": CompanyInfo("Eli Lilly and Co.", "礼来", "医疗 / 制药"),
    "UNH": CompanyInfo("UnitedHealth Group Inc.", "联合健康", "医疗 / 保险"),
    "AMGN": CompanyInfo("Amgen Inc.", "安进", "医疗 / 生物制药"),
    "GILD": CompanyInfo("Gilead Sciences Inc.", "吉利德", "医疗 / 生物制药"),
    "BMY": CompanyInfo("Bristol-Myers Squibb Co.", "百时美施贵宝", "医疗 / 制药"),
    "TMO": CompanyInfo("Thermo Fisher Scientific Inc.", "赛默飞世尔", "医疗 / 生命科学"),
    "DHR": CompanyInfo("Danaher Corp.", "丹纳赫", "医疗 / 生命科学"),
    "ABT": CompanyInfo("Abbott Laboratories", "雅培", "医疗 / 器械"),
    "PG": CompanyInfo("Procter & Gamble Co.", "宝洁", "消费 / 日化"),
    "KO": CompanyInfo("Coca-Cola Co.", "可口可乐", "消费 / 饮料"),
    "PEP": CompanyInfo("PepsiCo Inc.", "百事", "消费 / 食品饮料"),
    "PM": CompanyInfo("Philip Morris International", "菲利普莫里斯", "消费 / 烟草"),
    "MO": CompanyInfo("Altria Group Inc.", "奥驰亚", "消费 / 烟草"),
    "CL": CompanyInfo("Colgate-Palmolive Co.", "高露洁", "消费 / 日化"),
    "KMB": CompanyInfo("Kimberly-Clark Corp.", "金佰利", "消费 / 纸品"),
    "BABA": CompanyInfo("Alibaba Group Holding Ltd.", "阿里巴巴", "互联网 / 电商"),
    "PDD": CompanyInfo("PDD Holdings Inc.", "拼多多", "互联网 / 电商"),
    "JD": CompanyInfo("JD.com Inc.", "京东", "互联网 / 电商"),
    "BIDU": CompanyInfo("Baidu Inc.", "百度", "互联网 / AI"),
    "NIO": CompanyInfo("NIO Inc.", "蔚来", "汽车 / 新能源"),
    "XPEV": CompanyInfo("XPeng Inc.", "小鹏汽车", "汽车 / 新能源"),
    "LI": CompanyInfo("Li Auto Inc.", "理想汽车", "汽车 / 新能源"),
    "YUMC": CompanyInfo("Yum China Holdings", "百胜中国", "餐饮"),
    "BILI": CompanyInfo("Bilibili Inc.", "哔哩哔哩", "互联网 / 视频"),
    "TME": CompanyInfo("Tencent Music Entertainment", "腾讯音乐", "互联网 / 音乐"),
    "NTES": CompanyInfo("NetEase Inc.", "网易", "互联网 / 游戏"),
    "ZTO": CompanyInfo("ZTO Express (Cayman) Inc.", "中通快递", "物流 / 快递"),
    "BEKE": CompanyInfo("KE Holdings Inc.", "贝壳", "房产经纪"),
    "EA": CompanyInfo("Electronic Arts Inc.", "艺电", "游戏"),
}


def get(symbol: str | None) -> CompanyInfo | None:
    if not symbol:
        return None
    return KNOWN.get(symbol.upper())


def all() -> dict[str, CompanyInfo]:
    return KNOWN
