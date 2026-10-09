package com.bhive.common.util;

public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {
    }

    public static boolean isValidCoordinates(Double latitude, Double longitude) {
        return latitude != null && longitude != null
            && Double.isFinite(latitude) && Double.isFinite(longitude)
            && latitude >= -90 && latitude <= 90
            && longitude >= -180 && longitude <= 180;
    }

    public static boolean isValidOptionalCoordinates(Double latitude, Double longitude) {
        return latitude == null && longitude == null || isValidCoordinates(latitude, longitude);
    }

    public static double distanceKm(double latitudeA, double longitudeA, double latitudeB, double longitudeB) {
        if (!isValidCoordinates(latitudeA, longitudeA) || !isValidCoordinates(latitudeB, longitudeB)) {
            throw new IllegalArgumentException("Coordinates must be finite values within valid latitude and longitude ranges.");
        }

        double latitudeDelta = Math.toRadians(latitudeB - latitudeA);
        double longitudeDelta = Math.toRadians(longitudeB - longitudeA);
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
            + Math.cos(Math.toRadians(latitudeA)) * Math.cos(Math.toRadians(latitudeB))
            * Math.pow(Math.sin(longitudeDelta / 2), 2);
        double clampedHaversine = Math.min(1.0, Math.max(0.0, haversine));
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(clampedHaversine));
    }
}