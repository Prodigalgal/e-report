export function buildTransferRowFailureParams(failureFilter) {
    return {
        validationStatus: failureFilter === 'VALIDATION_FAILED' ? 'INVALID' : undefined,
        executionStatus: failureFilter === 'EXECUTION_FAILED' ? 'FAILED' : undefined,
    };
}
