package com.pro.ereportbacken.model.pojo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserNameSearchKeysTests {

    @Test
    void createsFullPinyinAndInitialsForChineseName() {
        UserNameSearchKeys keys = UserNameSearchKeys.from("林智博");

        assertThat(keys.fullPinyin()).isEqualTo("linzhibo");
        assertThat(keys.initials()).isEqualTo("lzb");

        UserNameSearchKeys umlautKeys = UserNameSearchKeys.from("吕春珉");
        assertThat(umlautKeys.fullPinyin()).isEqualTo("lvchunmin");
        assertThat(umlautKeys.initials()).isEqualTo("lcm");
    }

    @Test
    void normalizesCaseSpacesAndConnectors() {
        assertThat(UserNameSearchKeys.normalizeSearchTerm(" LIN-ZHI_BO "))
                .isEqualTo("linzhibo");
        assertThat(UserNameSearchKeys.normalizeSearchTerm("Lǚ Chūn Mín"))
                .isEqualTo("lvchunmin");
        assertThat(UserNameSearchKeys.escapeLikePattern("a!_%", '!'))
                .isEqualTo("a!!!_!%");
    }

    @Test
    void entityLifecycleRefreshesDerivedKeys() {
        User user = new User();
        user.setName("林智博");

        user.refreshNameSearchKeys();
        assertThat(user.getNamePinyin()).isEqualTo("linzhibo");
        assertThat(user.getNamePinyinInitials()).isEqualTo("lzb");

        user.setName("林博");
        user.refreshNameSearchKeys();
        assertThat(user.getNamePinyin()).isEqualTo("linbo");
        assertThat(user.getNamePinyinInitials()).isEqualTo("lb");
    }
}
