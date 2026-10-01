package com.nexamart.backend.service;

/** Single place for geographic distance in the backend. Do not compare raw lat/lng elsewhere. */
public final class GeoDistance {
  public static final double EARTH_RADIUS_KM = 6371.0088;

  private GeoDistance() {}

  /** Great-circle distance in kilometres using the Haversine formula. */
  public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  /** Latitude/longitude box that fully contains the circle; used only as a cheap SQL pre-filter. */
  public static double[] boundingBox(double lat, double lng, double radiusKm) {
    double dLat = Math.toDegrees(radiusKm / EARTH_RADIUS_KM);
    double cos = Math.max(Math.cos(Math.toRadians(lat)), 1e-6);
    double dLng = Math.min(180, Math.toDegrees(radiusKm / (EARTH_RADIUS_KM * cos)));
    return new double[] {lat - dLat, lat + dLat, lng - dLng, lng + dLng};
  }
}
