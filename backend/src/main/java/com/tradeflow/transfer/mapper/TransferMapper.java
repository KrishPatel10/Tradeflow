package com.tradeflow.transfer.mapper;

import com.tradeflow.transfer.dto.TransferResponse;
import com.tradeflow.transfer.entity.Transfer;

public final class TransferMapper {

    private TransferMapper() {
    }

    public static TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse(transfer.getId(), transfer.getFromAccount().getId(),
                transfer.getToAccount().getId(), transfer.getAmount(), transfer.getCurrency(), transfer.getStatus(),
                transfer.getFailureReason(), transfer.getCreatedAt(), transfer.getCompletedAt());
    }
}
