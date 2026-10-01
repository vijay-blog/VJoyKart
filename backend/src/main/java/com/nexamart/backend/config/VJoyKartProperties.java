package com.nexamart.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fixed VJoyKart Store location and delivery dispatch rules.
 *
 * <p>The store coordinates were resolved once from the official Google Maps share link
 * (https://maps.app.goo.gl/2j5t44Xc4DJJSvjR9) and are the backend source of truth. They are
 * never geocoded at runtime; override them through environment variables if the store moves.
 */
@ConfigurationProperties(prefix = "vjoykart")
public class VJoyKartProperties {
  private final Store store = new Store();
  private final Dispatch dispatch = new Dispatch();

  public Store getStore() { return store; }
  public Dispatch getDispatch() { return dispatch; }

  public static class Store {
    private String name = "VJoyKart Store";
    private double latitude = 17.3899091;
    private double longitude = 78.383089;
    private String mapsUrl = "https://maps.app.goo.gl/2j5t44Xc4DJJSvjR9";
    private String address = "Bharat Nagar, Ibrahim Bagh, Hyderabad, Telangana 500089";

    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double v) { latitude = v; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double v) { longitude = v; }
    public String getMapsUrl() { return mapsUrl; }
    public void setMapsUrl(String v) { mapsUrl = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { address = v; }
  }

  public static class Dispatch {
    /** Partners within this distance of the store are tried first. */
    private double initialRadiusKm = 5.0;
    /** If nobody is free inside the initial radius, partners up to this distance are tried. */
    private double fallbackRadiusKm = 8.0;
    /** A partner location older than this is treated as unknown (partner is not assignable). */
    private long locationMaxAgeSeconds = 300;
    /** Orders older than this are not auto-dispatched by the retry job (protects legacy/abandoned orders). */
    private long pendingOrderMaxAgeMinutes = 180;
    private boolean retryEnabled = true;

    public double getInitialRadiusKm() { return initialRadiusKm; }
    public void setInitialRadiusKm(double v) { initialRadiusKm = v; }
    public double getFallbackRadiusKm() { return fallbackRadiusKm; }
    public void setFallbackRadiusKm(double v) { fallbackRadiusKm = v; }
    public long getLocationMaxAgeSeconds() { return locationMaxAgeSeconds; }
    public void setLocationMaxAgeSeconds(long v) { locationMaxAgeSeconds = v; }
    public long getPendingOrderMaxAgeMinutes() { return pendingOrderMaxAgeMinutes; }
    public void setPendingOrderMaxAgeMinutes(long v) { pendingOrderMaxAgeMinutes = v; }
    public boolean isRetryEnabled() { return retryEnabled; }
    public void setRetryEnabled(boolean v) { retryEnabled = v; }
  }

  /** Fails fast on startup instead of silently dispatching against a broken store location. */
  public void validate() {
    if (store.latitude < -90 || store.latitude > 90 || store.longitude < -180 || store.longitude > 180
        || (store.latitude == 0 && store.longitude == 0)) {
      throw new IllegalStateException("vjoykart.store latitude/longitude are invalid.");
    }
    if (dispatch.initialRadiusKm <= 0 || dispatch.fallbackRadiusKm < dispatch.initialRadiusKm) {
      throw new IllegalStateException("vjoykart.dispatch radius must satisfy 0 < initial-radius-km <= fallback-radius-km.");
    }
    if (dispatch.locationMaxAgeSeconds <= 0) {
      throw new IllegalStateException("vjoykart.dispatch.location-max-age-seconds must be positive.");
    }
  }
}
