package com.pro.ereportbacken.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseDTOTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesStablePagedJsonContract() throws Exception {
        var page = new PageImpl<>(
                List.of("second", "third"),
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt")),
                5
        );

        PageResponseDTO<String> response = PageResponseDTO.from(page);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertThat(json.get("content").size()).isEqualTo(2);
        assertThat(json.get("totalElements").asLong()).isEqualTo(5);
        assertThat(json.get("totalPages").asInt()).isEqualTo(3);
        assertThat(json.get("size").asInt()).isEqualTo(2);
        assertThat(json.get("number").asInt()).isEqualTo(1);
        assertThat(json.get("numberOfElements").asInt()).isEqualTo(2);
        assertThat(json.get("first").asBoolean()).isFalse();
        assertThat(json.get("last").asBoolean()).isFalse();
        assertThat(json.get("empty").asBoolean()).isFalse();
        assertThat(json.at("/pageable/offset").asLong()).isEqualTo(2);
        assertThat(json.at("/pageable/pageNumber").asInt()).isEqualTo(1);
        assertThat(json.at("/pageable/pageSize").asInt()).isEqualTo(2);
        assertThat(json.at("/pageable/paged").asBoolean()).isTrue();
        assertThat(json.at("/pageable/unpaged").asBoolean()).isFalse();
        assertThat(json.at("/sort/sorted").asBoolean()).isTrue();
        assertThat(json.at("/sort/unsorted").asBoolean()).isFalse();
    }

    @Test
    void handlesUnpagedResultsWithoutReadingUnsupportedPageableProperties() {
        var page = new PageImpl<>(List.of("only"), Pageable.unpaged(), 1);

        PageResponseDTO<String> response = PageResponseDTO.from(page);

        assertThat(response.pageable().paged()).isFalse();
        assertThat(response.pageable().unpaged()).isTrue();
        assertThat(response.pageable().offset()).isZero();
        assertThat(response.number()).isZero();
        assertThat(response.totalPages()).isEqualTo(1);
    }
}
