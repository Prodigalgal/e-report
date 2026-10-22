package com.pro.ereportbacken.model.pojo;

import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;

import java.text.Normalizer;
import java.util.Locale;

public record UserNameSearchKeys(String fullPinyin, String initials) {

    public static final int FULL_PINYIN_MAX_LENGTH = 640;
    public static final int INITIALS_MAX_LENGTH = 160;

    public static UserNameSearchKeys from(String name) {
        if (name == null || name.isBlank()) {
            return new UserNameSearchKeys("", "");
        }
        String normalizedName = Normalizer.normalize(name.trim(), Normalizer.Form.NFKC);
        String fullPinyin = normalizeSearchTerm(
                PinyinHelper.toPinyin(normalizedName, PinyinStyleEnum.INPUT, ""));
        String initials = normalizeSearchTerm(
                PinyinHelper.toPinyin(normalizedName, PinyinStyleEnum.FIRST_LETTER, ""));
        return new UserNameSearchKeys(
                truncate(fullPinyin, FULL_PINYIN_MAX_LENGTH),
                truncate(initials, INITIALS_MAX_LENGTH)
        );
    }

    public static String normalizeSearchTerm(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String keyboardPinyin = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace("u:", "v")
                .replace('ü', 'v')
                .replace('ǖ', 'v')
                .replace('ǘ', 'v')
                .replace('ǚ', 'v')
                .replace('ǜ', 'v');
        String normalized = Normalizer.normalize(keyboardPinyin, Normalizer.Form.NFD);
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(codePoint -> Character.getType(codePoint) != Character.NON_SPACING_MARK)
                .filter(Character::isLetterOrDigit)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    public static String escapeLikePattern(String value, char escapeCharacter) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String escape = String.valueOf(escapeCharacter);
        return value.replace(escape, escape + escape)
                .replace("%", escape + "%")
                .replace("_", escape + "_");
    }

    private static String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }
}
