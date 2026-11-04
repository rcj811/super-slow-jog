package com.rcj811.slowjog

/** 跑步陪伴角色 */
enum class Mascot(val label: String) {
    DUCK("鴨子"),
    DOG("小狗"),
    CAT("小貓"),
    CAPYBARA("水豚"),
    BOY("小男孩"),
    GIRL("小女孩"),
    RABBIT("兔子"),
    PANDA("熊貓"),
    PHOTO("我的照片");

    companion object {
        fun fromId(id: String?): Mascot = entries.firstOrNull { it.name == id } ?: DUCK
    }
}
