package com.example.financialemail.routing;

import com.example.financialemail.domain.RequestClassification;
import com.example.financialemail.domain.RequestSubtype;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RequestRouterTest {
    private final RequestRouter router = new RequestRouter();

    static Stream<Arguments> routes() {
        return Stream.of(
                Arguments.of(RequestSubtype.PURCHASE, DownstreamApi.API_1),
                Arguments.of(RequestSubtype.REDEMPTION, DownstreamApi.API_1),
                Arguments.of(RequestSubtype.SWITCH, DownstreamApi.API_1),
                Arguments.of(RequestSubtype.SIP, DownstreamApi.API_2),
                Arguments.of(RequestSubtype.STP, DownstreamApi.API_3),
                Arguments.of(RequestSubtype.SWP, DownstreamApi.API_3),
                Arguments.of(RequestSubtype.PROSPECT, DownstreamApi.API_3),
                Arguments.of(RequestSubtype.NOMINEE_MODIFICATION, DownstreamApi.API_4),
                Arguments.of(RequestSubtype.ADDRESS_MODIFICATION, DownstreamApi.API_4),
                Arguments.of(RequestSubtype.BANK_MODIFICATION, DownstreamApi.API_4),
                Arguments.of(RequestSubtype.OTHER_MODIFICATION, DownstreamApi.API_4));
    }

    @ParameterizedTest
    @MethodSource("routes")
    void mapsTheNormalizedClassificationToTheExpectedApi(RequestSubtype subtype, DownstreamApi expectedApi) {
        RequestClassification classification = RequestClassification.fromSubtype(subtype);

        assertThat(router.route(classification)).contains(expectedApi);
    }

    @ParameterizedTest
    @MethodSource("unknownClassifications")
    void doesNotRouteUnknownOrMissingClassifications(RequestClassification classification) {
        assertThat(router.route(classification)).isEqualTo(Optional.empty());
    }

    static Stream<RequestClassification> unknownClassifications() {
        return Stream.of(RequestClassification.fromSubtype(RequestSubtype.UNKNOWN),
                new RequestClassification(null, null));
    }
}
