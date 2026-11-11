package github.ponyhuang.gimi.domain.voice.model

/**
 * 千问音频 TTS 的系统音色表。
 *
 * 音色与模型绑定：3.1 与 3.0、flash 与 plus 各自的音色集合互不相同，
 * 跨模型使用会被服务端拒绝（`InvalidParameter`）。因此查询音色必须带上模型 ID，
 * 不能只按服务返回。
 *
 * 数据来源：阿里云百炼《Qwen-Audio-TTS音色列表》。
 *
 * 字段约定：[TtsVoice.language] 只放语种，[TtsVoice.description] 统一写成
 * 「声线特质 · 适用场景」，选择器只展示特质、场景仅参与搜索。
 */
object QwenAudioTtsVoices {
    /** 3.1 系列 flash 模型。 */
    const val MODEL_ID_31_FLASH = "qwen-audio-3.1-tts-flash"

    /** 3.1 系列 plus 模型；只开放四个音色。 */
    const val MODEL_ID_31_PLUS = "qwen-audio-3.1-tts-plus"

    /** 3.0 系列 flash 模型。 */
    const val MODEL_ID_30_FLASH = "qwen-audio-3.0-tts-flash"

    /** 3.0 系列 plus 模型。 */
    const val MODEL_ID_30_PLUS = "qwen-audio-3.0-tts-plus"

    private const val MULTILINGUAL_LANGUAGE = "中文、英文、方言、多语种"

    /**
     * 四个多语种音色覆盖同一套方言与外语，逐条罗列超出列表行的展示宽度，
     * 因此把语种清单放在场景段，让它只参与搜索。
     */
    private const val MULTILINGUAL_DESCRIPTION =
        "方言与多语种 · 上海话、广东话、东北话、重庆话、陕西话、云南话、宁波话、甘肃话、" +
            "日语、韩语、法语、德语、葡萄牙语、意大利语、越南语、印尼语"

    /** 3.1 系列 flash 的全部系统音色。 */
    val flash31: List<TtsVoice> = listOf(
        // ── 多语种与方言（同时也是 3.1-plus 时仅有的四个音色） ──
        TtsVoice("longanhuan_v3.1", "龙安欢", MULTILINGUAL_LANGUAGE, "女", MULTILINGUAL_DESCRIPTION, VoiceCategory.Multilingual),
        TtsVoice("longanlingxin_v3.1", "龙安灵心", MULTILINGUAL_LANGUAGE, "女", MULTILINGUAL_DESCRIPTION, VoiceCategory.Multilingual),
        TtsVoice("longanfengyue_v3.1", "龙安风悦", MULTILINGUAL_LANGUAGE, "女", MULTILINGUAL_DESCRIPTION, VoiceCategory.Multilingual),
        TtsVoice("xunanchuan_v3.1", "许南川", MULTILINGUAL_LANGUAGE, "男", MULTILINGUAL_DESCRIPTION, VoiceCategory.Multilingual),

        // ── 精品中文 ──
        TtsVoice("yuxiaoyun_v3.1", "于小云", "中文（普通话）", "女", "元气、亲切、自然 · 广告营销、广播、客服助手、旁白", VoiceCategory.QualityChinese),
        TtsVoice("qiaoxiaojiao_v3.1", "乔小娇", "中文（普通话）", "女", "俏丽、可爱 · 广告营销、客服助手、有声书", VoiceCategory.QualityChinese),
        TtsVoice("xiaxiaochen_v3.1", "夏小晨", "中文（普通话）", "女", "元气、明亮 · 广告营销、有声书", VoiceCategory.QualityChinese),
        TtsVoice("anmingyuan_v3.1", "安明远", "中文（普通话）", "男", "清亮、自然 · 广告营销、有声书、旁白", VoiceCategory.QualityChinese),
        TtsVoice("wenhuaiqing_v3.1", "温怀清", "中文（普通话）", "女", "清亮、柔和 · 儿童故事、客服助手、广告营销、新闻播报", VoiceCategory.QualityChinese),
        TtsVoice("anxiaolan_v3.1", "安小岚", "中文（普通话）", "女", "清甜、纯净 · 有声书、客服助手、旁白、新闻播报、广告营销", VoiceCategory.QualityChinese),
        TtsVoice("xieshurou_v3.1", "谢舒柔", "中文（普通话）", "女", "柔和、自然、知性 · 有声书、客服助手、旁白", VoiceCategory.QualityChinese),
        TtsVoice("baiqinglan_v3.1", "白清岚", "中文（普通话）", "女", "明亮、清纯 · 语音助手、客服助手", VoiceCategory.QualityChinese),
        TtsVoice("xuyuyuan_v3.1", "许玉远", "中文（普通话）", "女", "知性、成熟、质感 · 广告营销、新闻播报、旁白、客服助手、有声书", VoiceCategory.QualityChinese),
        TtsVoice("anruorou_v3.1", "安若柔", "中文（普通话）", "女", "气声、知性 · 旁白、语音助手", VoiceCategory.QualityChinese),
        TtsVoice("wenhuaizhi_v3.1", "闻怀之", "中文（普通话）", "女", "稳重、成熟 · 有声书、新闻播报、广告营销、客服助手、旁白", VoiceCategory.QualityChinese),
        TtsVoice("xiaoxingzhi_v3.1", "萧行之", "中文（普通话）", "女", "端庄、贵气 · 新闻播报、有声书、旁白、客服助手", VoiceCategory.QualityChinese),
        TtsVoice("guyunshu_v3.1", "顾云舒", "中文（普通话）", "女", "成熟、稳重 · 音乐电台、客服助手、有声书、旁白", VoiceCategory.QualityChinese),
        TtsVoice("huozhuoshi_v3.1", "霍拙石", "中文（普通话）", "男", "清亮 · 有声书、广告营销、旁白", VoiceCategory.QualityChinese),
        TtsVoice("yeqinghe_v3.1", "叶清禾", "中文（普通话）", "女", "亲切、温柔 · 有声书、广告营销、旁白、客服助手", VoiceCategory.QualityChinese),
        TtsVoice("yunhuanhuan_v3.1", "云欢欢", "中文（普通话）", "女", "高亢、热情 · 有声书、旁白、客服助手", VoiceCategory.QualityChinese),
        TtsVoice("xuxiaoqiao_v3.1", "徐小俏", "中文（普通话）", "女", "自然、俏皮 · 有声书、旁白、客服助手", VoiceCategory.QualityChinese),
        TtsVoice("baianran_v3.1", "白安然", "中文（普通话）", "女", "低沉、浑厚、气声 · 配音讲解、有声书、旁白", VoiceCategory.QualityChinese),
        TtsVoice("xuyanchu_v3.1", "许言初", "中文（普通话）", "女", "沉稳、磁性 · 新闻播报、有声书", VoiceCategory.QualityChinese),
        TtsVoice("yezhiqing_v3.1", "叶知晴", "中文（普通话）", "女", "轻快、自然 · 儿童故事、客服助手、语音助手", VoiceCategory.QualityChinese),
        TtsVoice("andi_v3.1", "安迪", "中文（普通话）", "男", "ABC口音 · 语音助手", VoiceCategory.QualityChinese),
        TtsVoice("anyuqing_v3.1", "安语晴", "中文（普通话）", "女", "甜妹 · 语音助手、旁白、新闻播报", VoiceCategory.QualityChinese),
        TtsVoice("linxiaoling_v3.1", "林小灵", "中文（普通话）", "女", "甜妹 · 语音助手", VoiceCategory.QualityChinese),
        TtsVoice("longfeifei_v3.1", "龙菲菲", "中文（普通话）", "女", "甜妹 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("longjing_v3.1", "龙婧", "中文（普通话）", "女", "质感、沉稳 · 新闻播报", VoiceCategory.QualityChinese),
        TtsVoice("longqiang_v3.1", "龙嫱", "中文（普通话）", "女", "浪漫、风情 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("longyan_v3.1", "龙颜", "中文（普通话）", "女", "温暖、自然、亲切 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("longxiaobai_v3.1", "龙小白", "中文（普通话）", "女", "磁性、质感 · 新闻播报", VoiceCategory.QualityChinese),
        TtsVoice("longyue_v3.1", "龙悦", "中文（普通话）", "女", "温暖、磁性 · 有声书", VoiceCategory.QualityChinese),
        TtsVoice("longcheng_v3.1", "龙橙", "中文（普通话）", "男", "智慧、磁性 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("longlaotie_v3.1", "龙老铁", "中文（普通话）", "男", "东北、直率 · 方言", VoiceCategory.QualityChinese),
        TtsVoice("longshao_v3.1", "龙邵", "中文（普通话）", "男", "积极、元气 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("longshu_v3.1", "龙书", "中文（普通话）", "男", "沉稳、磁性 · 新闻播报", VoiceCategory.QualityChinese),
        TtsVoice("longze_v3.1", "龙泽", "中文（普通话）", "男", "温暖、元气、自然 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("longhao_v3.1", "龙浩", "中文（普通话）", "男", "多情、忧郁 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("longjin_v3.1", "龙津", "中文（普通话）", "男", "优雅、温润 · 诗词朗诵", VoiceCategory.QualityChinese),
        TtsVoice("longnan_v3.1", "龙楠", "中文（普通话）", "男", "睿智 · 有声书", VoiceCategory.QualityChinese),
        TtsVoice("longshuo_v3.1", "龙硕", "中文（普通话）", "男", "博才、干练 · 新闻播报", VoiceCategory.QualityChinese),
        TtsVoice("longtian_v3.1", "龙天", "中文（普通话）", "男", "磁性、理智 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("longxiaocheng_v3.1", "龙小诚", "中文（普通话）", "男", "磁性、低音 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("loongbella_v3.1", "Bella", "中文、英文", "女", "干练、磁性、知性 · 英文、新闻播报", VoiceCategory.QualityChinese),

        // ── 精品英文 ──
        TtsVoice("Emily_v3.1", "Emily", "英文", "女", "英式女声", VoiceCategory.QualityEnglish),
        TtsVoice("Luna_v3.1", "Luna", "英文", "女", "英式女声", VoiceCategory.QualityEnglish),
        TtsVoice("Eric_v3.1", "Eric", "英文", "男", "英式男声", VoiceCategory.QualityEnglish),
        TtsVoice("Luca_v3.1", "Luca", "英文", "男", "英式男声", VoiceCategory.QualityEnglish),
        TtsVoice("Abby_v3.1", "Abby", "英文", "女", "美式女声", VoiceCategory.QualityEnglish),
        TtsVoice("Annie_v3.1", "Annie", "英文", "女", "美式女声", VoiceCategory.QualityEnglish),
        TtsVoice("Ava_v3.1", "Ava", "英文", "女", "美式女声", VoiceCategory.QualityEnglish),
        TtsVoice("Beth_v3.1", "Beth", "英文", "女", "美式女声", VoiceCategory.QualityEnglish),
        TtsVoice("Betty_v3.1", "Betty", "英文", "女", "美式女声", VoiceCategory.QualityEnglish),
        TtsVoice("Cally_v3.1", "Cally", "英文", "女", "美式女声", VoiceCategory.QualityEnglish),
        TtsVoice("Cindy_v3.1", "Cindy", "英文", "女", "美式女声", VoiceCategory.QualityEnglish),
        TtsVoice("Donna_v3.1", "Donna", "英文", "女", "美式女声", VoiceCategory.QualityEnglish),
        TtsVoice("Andy_v3.1", "Andy", "英文", "男", "美式男声", VoiceCategory.QualityEnglish),
        TtsVoice("Brian_v3.1", "Brian", "英文", "男", "美式男声", VoiceCategory.QualityEnglish),
        TtsVoice("David_v3.1", "David", "英文", "男", "美式男声", VoiceCategory.QualityEnglish),

        // ── 其他系统音色 ──
        TtsVoice("longanyuanfei_v3.1", "龙安元妃", "中文（普通话）", "女", "高傲妃子音 · 社交陪伴", VoiceCategory.Other),
        TtsVoice("longjielidou_v3.1", "龙杰力豆", "中文（普通话）", "儿童", "天真男童音 · 儿童陪伴", VoiceCategory.Other),
        TtsVoice("longanlingxi_v3.1", "龙安灵希", "中文（普通话）", "女", "可爱甜美音 · 社交陪伴（精品中文）", VoiceCategory.Other),
        TtsVoice("longhuohuo_v3.1", "龙火火", "中文（普通话）", "儿童", "顽皮少年音 · 角色音", VoiceCategory.Other),
        TtsVoice("longyingtao_v3.1", "龙应桃", "中文（普通话）", "女", "温柔淡定女 · 客服", VoiceCategory.Other),
        TtsVoice("longanya_v3.1", "龙安雅", "中文（普通话）", "女", "高雅气质女 · 社交陪伴", VoiceCategory.Other),
        TtsVoice("longwan_v3.1", "龙婉", "中文（普通话）", "女", "细腻柔声女 · 社交陪伴", VoiceCategory.Other),
        TtsVoice("longxing_v3.1", "龙星", "中文（普通话）", "女", "温婉邻家女 · 社交陪伴", VoiceCategory.Other),
        TtsVoice("longhua_v3.1", "龙华", "中文（普通话）", "女", "元气甜美女 · 社交陪伴", VoiceCategory.Other),
        TtsVoice("longhan_v3.1", "龙寒", "中文（普通话）", "男", "温暖痴情男 · 社交陪伴", VoiceCategory.Other),
        TtsVoice("longanzhi_v3.1", "龙安智", "中文（普通话）", "男", "睿智轻熟男 · 社交陪伴", VoiceCategory.Other),
        TtsVoice("longzhe_v3.1", "龙哲", "中文（普通话）", "男", "呆板大暖男 · 社交陪伴", VoiceCategory.Other),
        TtsVoice("longanyang_v3.1", "龙安洋", "中文（普通话）", "男", "阳光大男孩 · 社交陪伴（标杆音色）", VoiceCategory.Other),
        TtsVoice("libai_v3.1", "李白", "中文（普通话）", "男", "古代诗仙男 · 诗词朗诵", VoiceCategory.Other),
        TtsVoice("longling_v3.1", "龙铃", "中文（普通话）", "儿童", "稚气呆板女 · 童声", VoiceCategory.Other),
        TtsVoice("longniuniu_v3.1", "龙牛牛", "中文（普通话）", "儿童", "阳光男童声 · 消费电子-儿童有声书", VoiceCategory.Other),
        TtsVoice("longshanshan_v3.1", "龙闪闪", "中文（普通话）", "儿童", "戏剧化童声 · 消费电子-儿童有声书", VoiceCategory.Other),
        TtsVoice("longpaopao_v3.1", "龙泡泡", "中文（普通话）", "儿童", "飞天泡泡音 · 消费电子-儿童陪伴", VoiceCategory.Other),
        TtsVoice("loongstella_v3.1", "loongstella", "中文（普通话）", "女", "飒爽利落女 · 新闻播报", VoiceCategory.Other),
        TtsVoice("longyuan_v3.1", "龙媛", "中文（普通话）", "女", "温暖治愈女 · 有声书", VoiceCategory.Other),
        TtsVoice("longmiao_v3.1", "龙妙", "中文（普通话）", "女", "抑扬顿挫女 · 有声书", VoiceCategory.Other),
        TtsVoice("longsanshu_v3.1", "龙三叔", "中文（普通话）", "男", "沉稳质感男 · 有声书", VoiceCategory.Other),
        TtsVoice("longanli_v3.1", "龙安莉", "中文（普通话）", "女", "利落从容女 · 语音助手", VoiceCategory.Other),
        TtsVoice("longanwen_v3.1", "龙安温", "中文（普通话）", "女", "优雅知性女 · 语音助手", VoiceCategory.Other),
        TtsVoice("longanlang_v3.1", "龙安朗", "中文（普通话）", "男", "清爽利落男 · 语音助手", VoiceCategory.Other),
        TtsVoice("longxiaoxia_v3.1", "龙小夏", "中文（普通话）", "女", "沉稳权威女 · 语音助手", VoiceCategory.Other),
        TtsVoice("longanchong_v3.1", "龙安冲", "中文（普通话）", "男", "激情推销男 · 直播带货", VoiceCategory.Other),

        // ── 小语种 ──
        minority("zora_bg", "Zora", "Зора", "保加利亚语", "男"),
        minority("linde_nl", "Linde", "Linde", "荷兰语", "男"),
        minority("hajnal_hu", "Hajnal", "Hajnal", "匈牙利语", "女"),
        minority("senin_ro", "Senin", "Senin", "罗马尼亚语", "男"),
        minority("birk_da", "Birk", "Birk", "丹麦语", "男"),
        minority("lipa_hr", "Lipa", "Lipa", "克罗地亚语", "男"),
        minority("avgi_el", "Avgi", "Αυγή", "希腊语", "男"),
        minority("jitrenka_cs", "Jitrenka", "Jitřenka", "捷克语", "男"),
        minority("solglimt_nn", "Solglimt", "Solglimt", "挪威语", "男"),
        minority("glimma_sv", "Glimma", "Glimma", "瑞典语", "女"),
        minority("svar_hi", "Svar", "स्वर", "印地语", "男"),
        minority("iskra_sk", "Iskra", "Iskra", "斯洛伐克语", "女"),
        minority("aava_fi", "Aava", "Aava", "芬兰语", "男"),
        minority("sahar_ur", "Sahar", "سحر", "乌尔都语", "男"),
        minority("jasna_sr", "Jasna", "Јасна", "塞尔维亚语", "男"),
        minority("brisa_es_mx", "Brisa", "Brisa", "墨西哥西班牙语", "女"),
        minority("ausra_lt", "Ausra", "Aušra", "立陶宛语", "男"),
        minority("rasa_lv", "Rasa", "Rasa", "拉脱维亚语", "女"),
        minority("deniz_tr", "Deniz", "Deniz", "土耳其语", "男"),
        minority("ava_fa", "Ava", "آوا", "波斯语", "男"),
        minority("or_he", "Or", "אור", "希伯来语", "男"),
        minority("ayalguu_mn", "Ayalguu", "Аялгуу", "蒙古语", "男"),
        minority("zarja_sl", "Zarja", "Zarja", "斯洛文尼亚语", "男"),
        minority("ishiq_az", "Ishiq", "İşıq", "阿塞拜疆语", "男"),
        minority("zorza_pl", "Zorza", "Zorza", "波兰语", "男"),
        minority("alba_ca", "Alba", "Alba", "加泰罗尼亚语", "男"),
        minority("romduol_km", "Romduol", "រំដួល", "高棉语", "男"),
        minority("aurora_pt_br", "Aurora", "Aurora", "巴西葡萄牙语", "男"),
        minority("dao_lo", "Dao", "ດາວ", "老挝语", "男"),
        minority("tsiskari_ka", "Tsiskari", "ცისკარი", "格鲁吉亚语", "男"),
        minority("lente_af", "Lente", "Lente", "南非荷兰语", "女"),
        minority("kiran_ne", "Kiran", "किरण", "尼泊尔语", "男"),
        minority("zorka_be", "Zorka", "Зорка", "白俄罗斯语", "女"),
        minority("nilaav_ml", "Nilaav", "നിലാവ്", "马拉雅拉姆语", "女"),
        minority("staer_lb", "Staer", "Stär", "卢森堡语", "女"),
        minority("drita_sq", "Drita", "Drita", "阿尔巴尼亚语", "男"),
        minority("nila_ta", "Nila", "நிலா", "泰米尔语", "女"),
        minority("aruna_si", "Aruna", "අරුණ", "僧伽罗语", "男"),
        minority("vennela_te", "Vennela", "వెన్నెల", "泰卢固语", "男"),
        minority("sade_et", "Sade", "Säde", "爱沙尼亚语", "女"),
        minority("bituon_ceb", "Bituon", "Bituon", "宿务语", "男"),
        minority("vedrina_bs", "Vedrina", "Vedrina", "波斯尼亚语", "女"),
        minority("layaung_my", "LaYaung", "လရောင်", "缅甸语", "男"),
        minority("luys_hy", "Luys", "Լույս", "亚美尼亚语", "女"),
        minority("lumen_la", "Lumen", "Lumen", "拉丁语", "男"),
        minority("birta_is", "Birta", "Birta", "冰岛语", "男"),
        minority("zerniq_mt", "Zerniq", "Żerniq", "马耳他语", "女"),
        minority("nuru_sw", "Nuru", "Nuru", "斯瓦希里语", "女"),
        minority("arai_kk", "Arai", "Арай", "哈萨克语", "男"),
        minority("zetwal_crs", "Zetwal", "Zetwal", "塞舌尔克里奥尔语", "女"),
        minority("zoria_uk", "Zoria", "Зоря", "乌克兰语", "男"),
    )

    /** 3.1 plus 可用的四个音色 ID。 */
    private val PLUS_31_VOICE_IDS = setOf(
        "longanlingxin_v3.1",
        "xunanchuan_v3.1",
        "longanhuan_v3.1",
        "longanfengyue_v3.1",
    )

    /**
     * 3.1 系列 plus 的音色：只有四个通用音色可用，其余 3.1 音色仅 flash 提供。
     *
     * 直接从 [flash31] 里筛出来，避免两张表的元数据（名称、语言、描述）各自漂移；
     * 仅把这四个音色的分类改写为旗舰，它们在 plus 上就是官方主推的旗舰音色。
     */
    val plus31: List<TtsVoice> = flash31
        .filter { it.id in PLUS_31_VOICE_IDS }
        .map { it.copy(category = VoiceCategory.Flagship) }

    /** 3.0 系列 flash 的全部系统音色。 */
    val flash30: List<TtsVoice> = listOf(
        TtsVoice("longanfengyue", "龙安风悦", "中文（普通话）、英文", "女", "自然亲切音 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("longanyuanfei", "龙安元妃", "中文（普通话）、英文", "女", "高傲妃子音 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("longanlingxi", "龙安灵希", "中文（普通话）、英文", "女", "可爱甜美音 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("longanxiaoxin", "龙安小昕", "中文（普通话）、英文", "女", "亲切活泼音 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("longanhuan_v3.6", "龙安欢", "中文（普通话）、英文", "女", "开朗、热情 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("longjielidou_v3.6", "龙杰力豆", "中文（普通话）、英文", "儿童", "天真男童 · 儿童陪伴", VoiceCategory.Other),
        TtsVoice("longpaopao_v3.6", "龙泡泡", "中文（普通话）、英文", "儿童", "软糯可爱音 · 儿童陪伴", VoiceCategory.Other),
        TtsVoice("longhuohuo_v3.6", "龙火火", "中文（普通话）、英文", "儿童", "顽皮少年音 · 角色音", VoiceCategory.QualityChinese),
        TtsVoice("longchuanshu_v3.6", "龙川叔", "中文（普通话）、英文", "男", "川普大叔音 · 角色音", VoiceCategory.QualityChinese),
        TtsVoice("loongmary", "loongmary", "英文", "女", "温暖英音 · 社交陪伴", VoiceCategory.QualityEnglish),
        TtsVoice("loongeva_v3.6", "loongeva", "英文", "女", "高智美音 · 语音助手", VoiceCategory.QualityEnglish),
        TtsVoice("loongjohn", "loongJohn", "英文", "男", "沉稳亲切美音 · 社交陪伴", VoiceCategory.QualityEnglish),
    )

    /** 3.0 系列 plus 的音色：只有两个。 */
    val plus30: List<TtsVoice> = listOf(
        TtsVoice("longanlingxin", "龙安灵心", "中文（普通话）、英文", "女", "知心温暖音 · 社交陪伴", VoiceCategory.Flagship),
        TtsVoice("longanlufeng", "龙安鲁风", "中文（普通话）、英文", "男", "明亮开朗音 · 社交陪伴", VoiceCategory.Flagship),
    )

    /**
     * 小语种音色的 voice 参数带模型前缀，且后缀与音色名无关，只能逐条列出。
     *
     * @param suffix voice 参数去掉模型前缀后的部分，例如 `zora_bg`。
     * @param localName 该音色在本语种下的写法；与音色名相同时仍需传，选择器据此判断是否展示。
     */
    private fun minority(
        suffix: String,
        name: String,
        localName: String,
        language: String,
        gender: String,
    ): TtsVoice = TtsVoice(
        id = "qwen-audio-3.1-tts-flash-$suffix",
        name = name,
        language = language,
        gender = gender,
        category = VoiceCategory.Minority,
        localName = localName.takeIf { it != name },
    )

    /** 模型 ID 到音色表的映射；未收录的模型没有可用系统音色。 */
    private val voicesByModel: Map<String, List<TtsVoice>> = mapOf(
        MODEL_ID_31_FLASH to flash31,
        MODEL_ID_31_PLUS to plus31,
        MODEL_ID_30_FLASH to flash30,
        MODEL_ID_30_PLUS to plus30,
    )

    /** 按模型取音色；未收录的模型没有可用系统音色。 */
    fun forModel(modelId: String): List<TtsVoice> =
        voicesByModel[modelId].orEmpty()
}
