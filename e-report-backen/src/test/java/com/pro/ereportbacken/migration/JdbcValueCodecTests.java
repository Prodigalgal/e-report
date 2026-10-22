package com.pro.ereportbacken.migration;

import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JdbcValueCodecTests {

    private static final JdbcSchemaInspector.Column UUID_COLUMN =
            new JdbcSchemaInspector.Column("id", Types.OTHER, "uuid", false);

    @Test
    void mapsMySqlUuidBytesWithoutApplyingTimePartSwap() {
        byte[] bytes = HexFormat.of().parseHex("1026baba6ccd780c95645b8c656024db");

        Object converted = JdbcValueCodec.convertForTarget(bytes, UUID_COLUMN);

        assertThat(converted).isEqualTo(UUID.fromString("1026baba-6ccd-780c-9564-5b8c656024db"));
        assertThat(JdbcValueCodec.canonicalValue(bytes))
                .isEqualTo("U1026baba6ccd780c95645b8c656024db");
    }

    @Test
    void rejectsNonUuidBinaryLengths() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> JdbcValueCodec.convertForTarget(new byte[15], UUID_COLUMN))
                .withMessageContaining("16 字节");
    }
}
