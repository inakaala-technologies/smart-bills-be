package com.bhive.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeoUtilsTest {

    @Test
    void acceptsOnlyFiniteCoordinatesWithinLatitudeAndLongitudeBounds() {
        assertTrue(GeoUtils.isValidCoordinates(90.0, 180.0));
        assertTrue(GeoUtils.isValidCoordinates(-90.0, -180.0));
        assertTrue(GeoUtils.isValidOptionalCoordinates(null, null));
        assertTrue(GeoUtils.isValidOptionalCoordinates(12.0, 77.0));
        assertFalse(GeoUtils.isValidCoordinates(null, 0.0));
        assertFalse(GeoUtils.isValidCoordinates(0.0, null));
        assertFalse(GeoUtils.isValidOptionalCoordinates(null, 0.0));
        assertFalse(GeoUtils.isValidCoordinates(Double.NaN, 0.0));
        assertFalse(GeoUtils.isValidCoordinates(0.0, Double.POSITIVE_INFINITY));
        assertFalse(GeoUtils.isValidCoordinates(90.1, 0.0));
        assertFalse(GeoUtils.isValidCoordinates(0.0, -180.1));
    }

    @Test
    void calculatesDistanceInKilometers() {
        assertEquals(0.0, GeoUtils.distanceKm(0.0, 0.0, 0.0, 0.0));
        assertEquals(111.195, GeoUtils.distanceKm(0.0, 0.0, 1.0, 0.0), 0.01);
    }

    @Test
    void clampsAntipodalDistanceAndRejectsInvalidPoints() {
        assertEquals(Math.PI * 6371.0, GeoUtils.distanceKm(0.0, 0.0, 0.0, 180.0), 0.001);
        assertThrows(IllegalArgumentException.class, () -> GeoUtils.distanceKm(91.0, 0.0, 0.0, 0.0));
    }
}