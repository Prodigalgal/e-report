package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.BufferedObservationBatch;

import java.util.List;

public interface ObservationEventBuffer {

    void enqueue(BufferedObservationBatch batch);

    List<ClaimedBatch> claim(int maximumBatchCount);

    void acknowledge(ClaimedBatch batch);

    boolean recordFailure(ClaimedBatch batch);

    record ClaimedBatch(String serializedValue, BufferedObservationBatch batch) {
    }
}
