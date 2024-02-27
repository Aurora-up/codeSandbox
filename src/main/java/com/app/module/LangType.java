package com.app.module;

/**
 * @author HDD
 * @date 2024年02月27日
 * @description 语言类型枚举
 */
public enum LangType {
  /**
   * 语言类型
   */
  /* 本机: 编译 -> 执行 */
  RUST("rust"),
  C("c"),
  CPP("cpp"),
  /* jvm: 编译 -> 执行 */
  JAVA("java"),
  /* 解释型语言 */
  PYTHON("python");
  
  private final String langName;

  LangType(String langName) {
    this.langName = langName;
  }

  public String getLangName() {
    return langName;
  }

  // 通过语言名称获取对应的枚举值
  public static LangType getByLangName(String langName) {
    for (LangType language : values()) {
      if (language.getLangName().equalsIgnoreCase(langName)) {
        return language;
      }
    }
    throw new IllegalArgumentException("还未实现对 " + langName + " 的支持");
  }
}
