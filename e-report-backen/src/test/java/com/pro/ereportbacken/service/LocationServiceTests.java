package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.ReverseGeocodeResponseDTO;
import com.pro.ereportbacken.model.dto.ReverseGeocodeResolutionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class LocationServiceTests {

    private final LocationService locationService = new LocationService(null, new MapService());

    @Test
    void doesNotUseRoadOrBuildingAsVillageOrStreet() throws Exception {
        ReverseGeocodeResponseDTO response = locationService.parseAmapReverseGeocodeResponse(
                "117.364960,24.519241",
                """
                        {
                          "status": "1",
                          "regeocode": {
                            "formatted_address": "福建省漳州市南靖县山城镇嘉兴路网络运维中心",
                            "addressComponent": {
                              "township": "山城镇",
                              "neighborhood": {"name": "[]"},
                              "building": {"name": "网络运维中心"},
                              "streetNumber": {"street": "杉行街", "number": "1号"}
                            },
                            "pois": []
                          }
                        }
                        """
        );

        assertThat(response.township()).isEqualTo("山城镇");
        assertThat(response.villageOrStreet()).isEmpty();
        assertThat(response.formattedAddress()).contains("嘉兴路");
        assertThat(response.resolutionStatus()).isEqualTo(ReverseGeocodeResolutionStatus.UNRESOLVED);
        assertThat(response.candidates()).isEmpty();
    }

    @Test
    void confirmsAdministrativeNeighborhoodFromAddressComponent() throws Exception {
        ReverseGeocodeResponseDTO neighborhoodResponse = locationService.parseAmapReverseGeocodeResponse(
                "117.364960,24.519241",
                responseWithLocality("翠湖社区", "杉行街", "[]", "")
        );

        assertThat(neighborhoodResponse.villageOrStreet()).isEqualTo("翠湖社区");
        assertThat(neighborhoodResponse.resolutionStatus()).isEqualTo(ReverseGeocodeResolutionStatus.CONFIRMED);
        assertThat(neighborhoodResponse.candidates()).extracting("name").containsExactly("翠湖社区");
    }

    @Test
    void returnsNearbyVillageAsCandidateInsteadOfSilentlySelectingIt() throws Exception {
        ReverseGeocodeResponseDTO poiResponse = locationService.parseAmapReverseGeocodeResponse(
                "117.364960,24.519241",
                responseWithLocality("[]", "杉行街", "靖城村", "地名地址信息;普通地名;村庄级地名")
        );

        assertThat(poiResponse.villageOrStreet()).isEmpty();
        assertThat(poiResponse.resolutionStatus()).isEqualTo(ReverseGeocodeResolutionStatus.NEEDS_CONFIRMATION);
        assertThat(poiResponse.candidates()).extracting("name", "category")
                .containsExactly(tuple("靖城村", "行政村"));
    }

    @Test
    void confirmsVillageCandidateWhenFormattedAddressContainsItsName() throws Exception {
        ReverseGeocodeResponseDTO response = locationService.parseAmapReverseGeocodeResponse(
                "117.364960,24.519241",
                responseWithFormattedAddress("福建省漳州市南靖县山城镇靖城村嘉兴路", "靖城村")
        );

        assertThat(response.villageOrStreet()).isEqualTo("靖城村");
        assertThat(response.resolutionStatus()).isEqualTo(ReverseGeocodeResolutionStatus.CONFIRMED);
    }

    private String responseWithLocality(String neighborhood, String street, String poiName, String poiType) {
        return """
                {
                  "status": "1",
                  "regeocode": {
                    "formatted_address": "测试地址",
                    "addressComponent": {
                      "township": "山城镇",
                      "neighborhood": {"name": "%s"},
                      "streetNumber": {"street": "%s"}
                    },
                    "pois": [{"name": "%s", "type": "%s"}]
                  }
                }
                """.formatted(neighborhood, street, poiName, poiType);
    }

    private String responseWithFormattedAddress(String formattedAddress, String poiName) {
        return """
                {
                  "status": "1",
                  "regeocode": {
                    "formatted_address": "%s",
                    "addressComponent": {
                      "township": "山城镇",
                      "adcode": "350627",
                      "neighborhood": {"name": "[]"}
                    },
                    "pois": [{
                      "name": "%s",
                      "typecode": "190108",
                      "distance": "320"
                    }]
                  }
                }
                """.formatted(formattedAddress, poiName);
    }
}
