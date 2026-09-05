package com.example.financialemail.domain;

public record RequestClassification(
        RequestCategory category,
        RequestSubtype subtype) {

    public static RequestClassification fromSubtype(RequestSubtype subtype) {
        RequestSubtype normalizedSubtype = subtype == null ? RequestSubtype.UNKNOWN : subtype;
        RequestCategory category = switch (normalizedSubtype) {
            case PURCHASE, REDEMPTION, SWITCH -> RequestCategory.FINANCIAL_TRANSACTION;
            case SIP -> RequestCategory.NFT_SIP;
            case STP, SWP, PROSPECT -> RequestCategory.NFT_STP_SWP_PROSPECT;
            case NOMINEE_MODIFICATION, ADDRESS_MODIFICATION, BANK_MODIFICATION, OTHER_MODIFICATION ->
                    RequestCategory.NFT_MODIFICATION;
            case UNKNOWN -> RequestCategory.UNKNOWN;
        };
        return new RequestClassification(category, normalizedSubtype);
    }
}
