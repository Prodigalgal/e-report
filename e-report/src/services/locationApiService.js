import apiClient from './apiClient';

export const fetchReverseGeocode = (gpsCoordinates) => {
    return apiClient.get('/location/reverse-geocode', {
        params: { gpsCoordinates },
    });
};
