package com.example.financialemail.workflow;

import com.example.financialemail.routing.DownstreamApi;

import java.util.List;

public record DownstreamApiResponse(
        DownstreamApi api,
        int httpStatus,
        boolean responseBodyPresent,
        List<DownstreamRecord> records,
        DownstreamFailureType failureType,
        String errorCode,
        String message) {

    public DownstreamApiResponse {
        records = records == null ? null : List.copyOf(records);
        failureType = failureType == null ? DownstreamFailureType.NONE : failureType;
    }

    public static DownstreamApiResponse success(DownstreamApi api, List<DownstreamRecord> records,
                                                String message) {
        return new DownstreamApiResponse(api, 200, true, records,
                DownstreamFailureType.NONE, null, message);
    }

    public static DownstreamApiResponse noContent(DownstreamApi api, String message) {
        return new DownstreamApiResponse(api, 204, false, List.of(),
                DownstreamFailureType.NONE, null, message);
    }

    public static DownstreamApiResponse failure(DownstreamApi api, int httpStatus,
                                                DownstreamFailureType failureType,
                                                String errorCode, String message) {
        return new DownstreamApiResponse(api, httpStatus, true, List.of(), failureType,
                errorCode, message);
    }
}
