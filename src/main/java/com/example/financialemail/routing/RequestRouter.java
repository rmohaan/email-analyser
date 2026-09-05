package com.example.financialemail.routing;

import com.example.financialemail.domain.RequestCategory;
import com.example.financialemail.domain.RequestClassification;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RequestRouter {

    public Optional<DownstreamApi> route(RequestClassification classification) {
        if (classification == null || classification.category() == null) {
            return Optional.empty();
        }

        return switch (classification.category()) {
            case FINANCIAL_TRANSACTION -> Optional.of(DownstreamApi.API_1);
            case NFT_SIP -> Optional.of(DownstreamApi.API_2);
            case NFT_STP_SWP_PROSPECT -> Optional.of(DownstreamApi.API_3);
            case NFT_MODIFICATION -> Optional.of(DownstreamApi.API_4);
            case UNKNOWN -> Optional.empty();
        };
    }
}
