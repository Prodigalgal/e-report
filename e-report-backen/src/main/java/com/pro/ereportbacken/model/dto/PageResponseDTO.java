package com.pro.ereportbacken.model.dto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 与 Spring Data 序列化实现解耦的稳定 HTTP 分页契约。
 */
public record PageResponseDTO<T>(
        List<T> content,
        PageableMetadata pageable,
        boolean last,
        int totalPages,
        long totalElements,
        boolean first,
        int size,
        int number,
        SortMetadata sort,
        int numberOfElements,
        boolean empty
) {

    public PageResponseDTO {
        content = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(content, "content")));
        pageable = Objects.requireNonNull(pageable, "pageable");
        sort = Objects.requireNonNull(sort, "sort");
    }

    public static <T> PageResponseDTO<T> from(Page<T> page) {
        Objects.requireNonNull(page, "page");
        Pageable pageable = page.getPageable();
        boolean paged = pageable.isPaged();
        SortMetadata sort = SortMetadata.from(page.getSort());
        PageableMetadata pageableMetadata = new PageableMetadata(
                paged ? pageable.getOffset() : 0L,
                sort,
                paged ? pageable.getPageNumber() : page.getNumber(),
                paged ? pageable.getPageSize() : page.getSize(),
                paged,
                !paged
        );
        return new PageResponseDTO<>(
                page.getContent(),
                pageableMetadata,
                page.isLast(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.isFirst(),
                page.getSize(),
                page.getNumber(),
                sort,
                page.getNumberOfElements(),
                page.isEmpty()
        );
    }

    public record PageableMetadata(
            long offset,
            SortMetadata sort,
            int pageNumber,
            int pageSize,
            boolean paged,
            boolean unpaged
    ) {
        public PageableMetadata {
            sort = Objects.requireNonNull(sort, "sort");
        }
    }

    public record SortMetadata(boolean empty, boolean sorted, boolean unsorted) {

        private static SortMetadata from(Sort sort) {
            Objects.requireNonNull(sort, "sort");
            return new SortMetadata(sort.isEmpty(), sort.isSorted(), sort.isUnsorted());
        }
    }
}
