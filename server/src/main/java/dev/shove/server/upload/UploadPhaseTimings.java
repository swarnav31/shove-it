package dev.shove.server.upload;

public record UploadPhaseTimings(
        Long receiveHashMs,
        Long externalCopyMs,
        Long promoteMs,
        Long auditMs,
        Long totalMs,
        String failurePhase
) {
    static UploadPhaseTimings inProgress(
            Long receiveHashMs,
            Long externalCopyMs,
            Long promoteMs) {
        return new UploadPhaseTimings(receiveHashMs, externalCopyMs, promoteMs, null, null, null);
    }

    static UploadPhaseTimings completed(
            Long receiveHashMs,
            Long externalCopyMs,
            Long promoteMs,
            Long auditMs,
            Long totalMs) {
        return new UploadPhaseTimings(receiveHashMs, externalCopyMs, promoteMs, auditMs, totalMs, null);
    }

    static UploadPhaseTimings failed(
            Long receiveHashMs,
            Long externalCopyMs,
            Long promoteMs,
            Long auditMs,
            Long totalMs,
            String failurePhase) {
        return new UploadPhaseTimings(
                receiveHashMs,
                externalCopyMs,
                promoteMs,
                auditMs,
                totalMs,
                failurePhase);
    }
}
