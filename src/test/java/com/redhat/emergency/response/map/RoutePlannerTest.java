package com.redhat.emergency.response.map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.redhat.emergency.response.model.Location;
import com.redhat.emergency.response.model.MissionStep;
import io.smallrye.mutiny.Uni;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RoutePlannerTest {

    RoutePlanner routePlanner;

    private WireMockServer mockServer;

    @BeforeEach
    void setup() {
        mockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        mockServer.start();
        routePlanner = new RoutePlanner();
        setField(routePlanner, "mapboxUrl", "http://localhost:" + mockServer.port());
        setField(routePlanner, "accessToken", "pk.replaceme");
    }

    @AfterEach
    void tearDown() {
        mockServer.stop();
    }

    @Test
    void testRoutePlanner() throws IOException {

        String url = "/directions/v5/mapbox/driving/-77.90999,34.18323;-77.84856,34.18408;-77.949,34.1706?access_token=pk.replaceme&geometries=polyline6&overview=full&steps=true";
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("mapbox/directions.json");
        String json = IOUtils.toString(is, Charset.defaultCharset());
        mockServer.stubFor(get(urlEqualTo(url))
                .willReturn(aResponse().withStatus(200).withBody(json)));
        Location start = Location.of(new BigDecimal("34.18323"), new BigDecimal("-77.90999"));
        Location destination = Location.of(new BigDecimal("34.1706"), new BigDecimal("-77.949"));
        Location waypoint = Location.of(new BigDecimal("34.18408"), new BigDecimal("-77.84856"));
        Uni<List<MissionStep>> uni = routePlanner.getDirections(start, destination, waypoint);
        List<MissionStep> steps = uni.await().indefinitely();

        assertThat(steps, notNullValue());
        assertThat(steps.size(), equalTo(22));
        assertThat(steps.get(0).isWayPoint(), equalTo(false));
        assertThat(steps.get(0).isDestination(), equalTo(false));
        assertThat(steps.get(13).isWayPoint(), equalTo(true));
        assertThat(steps.get(13).isDestination(), equalTo(false));
        assertThat(steps.get(21).isWayPoint(), equalTo(false));
        assertThat(steps.get(21).isDestination(), equalTo(true));
    }

    @Test
    void testRoutePlannerBadToken() throws IOException {

        String url = "/directions/v5/mapbox/driving/-87.90999,34.18323;-87.84856,34.18408;-87.949,34.1706?access_token=pk.replaceme&geometries=polyline6&overview=full&steps=true";
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("mapbox/directions.json");
        String json = IOUtils.toString(is, Charset.defaultCharset());
        mockServer.stubFor(get(urlEqualTo(url))
                .willReturn(aResponse().withStatus(401).withBody("{\"message\": \"Not Authorized - Invalid Token\"}")));
        Location start = Location.of(new BigDecimal("34.18323"), new BigDecimal("-87.90999"));
        Location destination = Location.of(new BigDecimal("34.1706"), new BigDecimal("-87.949"));
        Location waypoint = Location.of(new BigDecimal("34.18408"), new BigDecimal("-87.84856"));

        Uni<List<MissionStep>> uni = Uni.createFrom().voidItem().onItem()
                .transformToUni(v -> routePlanner.getDirections(start, destination, waypoint));

        try {
            List<MissionStep> steps = uni.await().indefinitely();
            assertThat("Not expected", false);
        } catch (RoutePlannerException e) {
            assertThat("Expected", true);
        }

    }

    @Test
    void testRoutePlannerNoRouteFoundForProfileDriving() throws IOException {

        String urlProfileDriving = "/directions/v5/mapbox/driving/-77.90999,34.18323;-77.84856,34.18408;-77.949,34.1706?access_token=pk.replaceme&geometries=polyline6&overview=full&steps=true";
        String urlProfileCycling = "/directions/v5/mapbox/cycling/-77.90999,34.18323;-77.84856,34.18408;-77.949,34.1706?access_token=pk.replaceme&geometries=polyline6&overview=full&steps=true";
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("mapbox/directions.json");
        String json = IOUtils.toString(is, Charset.defaultCharset());
        mockServer.stubFor(get(urlEqualTo(urlProfileDriving))
                .willReturn(aResponse().withStatus(200).withBody("{\"code\": \"NoRoute\"," +
                        "    \"message\": \"No route found\"," +
                        "    \"routes\": []}")));
        mockServer.stubFor(get(urlEqualTo(urlProfileCycling))
                .willReturn(aResponse().withStatus(200).withBody(json)));
        Location start = Location.of(new BigDecimal("34.18323"), new BigDecimal("-77.90999"));
        Location destination = Location.of(new BigDecimal("34.1706"), new BigDecimal("-77.949"));
        Location waypoint = Location.of(new BigDecimal("34.18408"), new BigDecimal("-77.84856"));

        Uni<List<MissionStep>> uni = Uni.createFrom().voidItem().onItem()
                .transformToUni(v -> routePlanner.getDirections(start, destination, waypoint));

        List<MissionStep> steps = uni.await().indefinitely();

        assertThat(steps.size(), equalTo(22));
        assertThat(steps.get(0).isWayPoint(), equalTo(false));
        assertThat(steps.get(0).isDestination(), equalTo(false));
        assertThat(steps.get(13).isWayPoint(), equalTo(true));
        assertThat(steps.get(13).isDestination(), equalTo(false));
        assertThat(steps.get(21).isWayPoint(), equalTo(false));
        assertThat(steps.get(21).isDestination(), equalTo(true));
    }

    @Test
    void testRoutePlannerNoRouteFound() throws IOException {

        String urlProfileDriving = "/directions/v5/mapbox/driving/-77.90999,34.18323;-77.84856,34.18408;-77.949,34.1706?access_token=pk.replaceme&geometries=polyline6&overview=full&steps=true";
        String urlProfileCycling = "/directions/v5/mapbox/cycling/-77.90999,34.18323;-77.84856,34.18408;-77.949,34.1706?access_token=pk.replaceme&geometries=polyline6&overview=full&steps=true";
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("mapbox/directions.json");
        String json = IOUtils.toString(is, Charset.defaultCharset());
        mockServer.stubFor(get(urlEqualTo(urlProfileDriving))
                .willReturn(aResponse().withStatus(200).withBody("{\"code\": \"NoRoute\"," +
                        "    \"message\": \"No route found\"," +
                        "    \"routes\": []}")));
        mockServer.stubFor(get(urlEqualTo(urlProfileCycling))
                .willReturn(aResponse().withStatus(200).withBody("{\"code\": \"NoRoute\"," +
                        "    \"message\": \"No route found\"," +
                        "    \"routes\": []}")));
        Location start = Location.of(new BigDecimal("34.18323"), new BigDecimal("-77.90999"));
        Location destination = Location.of(new BigDecimal("34.1706"), new BigDecimal("-77.949"));
        Location waypoint = Location.of(new BigDecimal("34.18408"), new BigDecimal("-77.84856"));

        Uni<List<MissionStep>> uni = Uni.createFrom().voidItem().onItem()
                .transformToUni(v -> routePlanner.getDirections(start, destination, waypoint));

        List<MissionStep> steps = uni.await().indefinitely();

        assertThat(steps.size(), equalTo(3));
        assertThat(steps.get(0).isWayPoint(), is(false));
        assertThat(steps.get(0).isDestination(), is(false));
        assertThat(steps.get(0).getLat(), equalTo(new BigDecimal("34.1832")));
        assertThat(steps.get(0).getLon(), equalTo(new BigDecimal("-77.9100")));
        assertThat(steps.get(1).isWayPoint(), is(true));
        assertThat(steps.get(1).isDestination(), is(false));
        assertThat(steps.get(1).getLat(), equalTo(new BigDecimal("34.1841")));
        assertThat(steps.get(1).getLon(), equalTo(new BigDecimal("-77.8486")));
        assertThat(steps.get(2).isWayPoint(), is(false));
        assertThat(steps.get(2).isDestination(), is(true));
        assertThat(steps.get(2).getLat(), equalTo(new BigDecimal("34.1706")));
        assertThat(steps.get(2).getLon(), equalTo(new BigDecimal("-77.9490")));
    }

    private void setField(Object targetObject, String name, Object value) {

        Class<?> targetClass = targetObject.getClass();
        Field field = findField(targetClass, name);
        if (field == null) {
            throw new IllegalArgumentException(String.format(
                    "Could not find field '%s' on %s", name, targetClass));
        }
        makeAccessible(field, targetObject);
        setField(field, targetObject, value);
    }

    private Field findField(Class<?> clazz, String name) {
        Class<?> searchType = clazz;
        while (Object.class != searchType && searchType != null) {
            Field[] fields = getDeclaredFields(searchType);
            for (Field field : fields) {
                if (name.equals(field.getName())) {
                    return field;
                }
            }
            searchType = searchType.getSuperclass();
        }
        return null;
    }

    private Field[] getDeclaredFields(Class<?> clazz) {
        try {
            return clazz.getDeclaredFields();
        }
        catch (Throwable ex) {
            throw new IllegalStateException("Failed to introspect Class [" + clazz.getName() +
                    "] from ClassLoader [" + clazz.getClassLoader() + "]", ex);
        }
    }

    private void makeAccessible(Field field, Object object) {
        if ((!Modifier.isPublic(field.getModifiers()) ||
                !Modifier.isPublic(field.getDeclaringClass().getModifiers()) ||
                Modifier.isFinal(field.getModifiers())) && !field.canAccess(object)) {
            field.setAccessible(true);
        }
    }

    private void setField(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        }
        catch (IllegalAccessException ex) {
            throw new IllegalStateException(
                    "Unexpected reflection exception - " + ex.getClass().getName() + ": " + ex.getMessage());
        }
    }

}
