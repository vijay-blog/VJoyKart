package com.nexamart.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.nexamart.backend.config.VJoyKartProperties;
import com.nexamart.backend.domain.DeliveryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** Store configuration, Haversine distance and the delivery status model. */
class DeliveryModelTest {

  @Test
  void storeDefaultsAreTheResolvedVJoyKartStoreCoordinates() {
    VJoyKartProperties props = new VJoyKartProperties();
    assertThat(props.getStore().getName()).isEqualTo("VJoyKart Store");
    assertThat(props.getStore().getLatitude()).isEqualTo(17.3899091);
    assertThat(props.getStore().getLongitude()).isEqualTo(78.383089);
    props.validate();
  }

  @Test
  void storeGoogleMapsUrlIsTheOfficialShareLink() {
    assertThat(new VJoyKartProperties().getStore().getMapsUrl()).isEqualTo("https://maps.app.goo.gl/2j5t44Xc4DJJSvjR9");
  }

  @Test
  void storeAndRadiusAreConfigurableThroughProperties() {
    var source = new MapConfigurationPropertySource(java.util.Map.of(
        "vjoykart.store.latitude", "17.5",
        "vjoykart.store.longitude", "78.5",
        "vjoykart.store.maps-url", "https://maps.example/store",
        "vjoykart.dispatch.initial-radius-km", "3",
        "vjoykart.dispatch.fallback-radius-km", "6",
        "vjoykart.dispatch.location-max-age-seconds", "120"));
    VJoyKartProperties props = new Binder(source).bind("vjoykart", VJoyKartProperties.class).get();
    assertThat(props.getStore().getLatitude()).isEqualTo(17.5);
    assertThat(props.getStore().getMapsUrl()).isEqualTo("https://maps.example/store");
    assertThat(props.getDispatch().getInitialRadiusKm()).isEqualTo(3);
    assertThat(props.getDispatch().getFallbackRadiusKm()).isEqualTo(6);
    assertThat(props.getDispatch().getLocationMaxAgeSeconds()).isEqualTo(120);
  }

  @Test
  void invalidStoreOrRadiusConfigurationFailsFast() {
    VJoyKartProperties zero = new VJoyKartProperties();
    zero.getStore().setLatitude(0);
    zero.getStore().setLongitude(0);
    assertThatThrownBy(zero::validate).isInstanceOf(IllegalStateException.class);

    VJoyKartProperties radius = new VJoyKartProperties();
    radius.getDispatch().setFallbackRadiusKm(2);
    assertThatThrownBy(radius::validate).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void haversineMatchesKnownDistances() {
    assertThat(GeoDistance.haversineKm(17.3899091, 78.383089, 17.3899091, 78.383089)).isEqualTo(0.0);
    // Store -> Charminar (17.3616, 78.4747) is ~10.2 km great-circle.
    assertThat(GeoDistance.haversineKm(17.3899091, 78.383089, 17.3616, 78.4747)).isCloseTo(10.2, within(0.2));
    // One degree of latitude ~ 111.2 km.
    assertThat(GeoDistance.haversineKm(17.0, 78.0, 18.0, 78.0)).isCloseTo(111.2, within(0.1));
    // Symmetric.
    assertThat(GeoDistance.haversineKm(17.36, 78.47, 17.38, 78.38)).isEqualTo(GeoDistance.haversineKm(17.38, 78.38, 17.36, 78.47));
  }

  @Test
  void boundingBoxContainsTheRadiusCircle() {
    double[] box = GeoDistance.boundingBox(17.3899091, 78.383089, 8);
    assertThat(GeoDistance.haversineKm(17.3899091, 78.383089, box[1], 78.383089)).isCloseTo(8, within(0.01));
    assertThat(GeoDistance.haversineKm(17.3899091, 78.383089, 17.3899091, box[3])).isGreaterThanOrEqualTo(7.99);
  }

  @Test
  void deliveryProgressHasExactlyFiveStepsInOrder() {
    assertThat(DeliveryStatus.PROGRESS_STEPS).containsExactly(DeliveryStatus.DELIVERY_ASSIGNED, DeliveryStatus.PACKING,
        DeliveryStatus.ON_THE_WAY, DeliveryStatus.ARRIVED, DeliveryStatus.DELIVERED);
    assertThat(DeliveryStatus.PROGRESS_STEPS.stream().map(DeliveryStatus::label).toList())
        .containsExactly("Delivery boy assigned", "Packing", "On the way", "Arrived", "Delivered");
  }

  @Test
  void onlySequentialTransitionsAreAllowed() {
    assertThat(DeliveryStatus.ORDER_PLACED.canTransitionTo(DeliveryStatus.DELIVERY_ASSIGNED)).isTrue();
    assertThat(DeliveryStatus.DELIVERY_ASSIGNED.canTransitionTo(DeliveryStatus.PACKING)).isTrue();
    assertThat(DeliveryStatus.PACKING.canTransitionTo(DeliveryStatus.ON_THE_WAY)).isTrue();
    assertThat(DeliveryStatus.ON_THE_WAY.canTransitionTo(DeliveryStatus.ARRIVED)).isTrue();
    assertThat(DeliveryStatus.ARRIVED.canTransitionTo(DeliveryStatus.DELIVERED)).isTrue();

    assertThat(DeliveryStatus.ORDER_PLACED.canTransitionTo(DeliveryStatus.PACKING)).isFalse();
    assertThat(DeliveryStatus.DELIVERY_ASSIGNED.canTransitionTo(DeliveryStatus.ON_THE_WAY)).isFalse();
    assertThat(DeliveryStatus.PACKING.canTransitionTo(DeliveryStatus.ARRIVED)).isFalse();
    assertThat(DeliveryStatus.ON_THE_WAY.canTransitionTo(DeliveryStatus.DELIVERED)).isFalse();
    assertThat(DeliveryStatus.PACKING.canTransitionTo(DeliveryStatus.DELIVERY_ASSIGNED)).isFalse();
    for (DeliveryStatus target : DeliveryStatus.values()) {
      assertThat(DeliveryStatus.DELIVERED.canTransitionTo(target)).isFalse();
      assertThat(DeliveryStatus.CANCELLED.canTransitionTo(target)).isFalse();
    }
  }

  @Test
  void parseAcceptsCanonicalNamesAndLegacyAliases() {
    assertThat(DeliveryStatus.parse("on_the_way")).isEqualTo(DeliveryStatus.ON_THE_WAY);
    assertThat(DeliveryStatus.parse("START_PACKING")).isEqualTo(DeliveryStatus.PACKING);
    assertThat(DeliveryStatus.parse("OUT_FOR_DELIVERY")).isEqualTo(DeliveryStatus.ON_THE_WAY);
    assertThat(DeliveryStatus.parse("COMPLETE")).isEqualTo(DeliveryStatus.DELIVERED);
    assertThat(DeliveryStatus.parse("teleport")).isNull();
  }
}
