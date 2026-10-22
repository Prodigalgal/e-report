export const isVerificationCommentRequired = (decision) => (
    decision === 'APPROVED' || decision === 'REJECTED'
);

export const buildVerificationCommand = ({
    responseId,
    decision,
    comment,
    expectedVersion,
}) => {
    const normalizedComment = String(comment || '').trim();
    return {
        responseId,
        decision,
        comment: normalizedComment,
        expectedVersion,
    };
};
