package com.otboo.team03.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.event.NotificationEventType;
import com.otboo.team03.notification.outbox.service.OutboxEventPublisher;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WeatherAlertServiceTest {

  @Mock
  private WeatherChangeDetector weatherChangeDetector;

  @Mock
  private ProfileRepository profileRepository;

  @Mock
  private OutboxEventPublisher outboxEventPublisher;

  @InjectMocks
  private WeatherAlertServiceImpl weatherAlertService;

  @Captor
  private ArgumentCaptor<NotificationEvent> eventCaptor;

  @Captor
  private ArgumentCaptor<String> partitionKeyCaptor;

  private Weather createWeather(Double lat, Double lon) {
    return Weather.builder()
        .latitude(lat)
        .longitude(lon)
        .forecastedAt(Instant.now())
        .forecastAt(Instant.now().plusSeconds(3600))
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .precipitationAmount(0.0)
        .precipitationProbability(0.0)
        .temperature(15.0)
        .humidity(50.0)
        .windSpeed(2.0)
        .build();
  }

  private Profile createProfileWithUser(UUID userId, Double lat, Double lon) {
    User user = new User("test@otboo.io", "password123!", "테스터");
    ReflectionTestUtils.setField(user, "id", userId);
    return new Profile(user, null, null, null, lat, lon, null, List.of("서울특별시", "강남구"));
  }

  @Test
  @DisplayName("날씨 급변이 감지되면 해당 거점의 사용자들에게 알림 이벤트를 Outbox로 발행한다")
  void sendWeatherAlertsIfChanged_whenAlertDetected_publishesNotificationEvents() {
    // given
    Double lat = 37.4979;
    Double lon = 127.0276;
    List<Weather> weathers = List.of(createWeather(lat, lon));

    WeatherAlertInfo alertInfo = new WeatherAlertInfo("비/눈 예보 알림", "오후에 비 예보가 있습니다. 우산을 챙기세요!");

    given(weatherChangeDetector.detect(weathers)).willReturn(Optional.of(alertInfo));

    UUID user1Id = UUID.randomUUID();
    UUID user2Id = UUID.randomUUID();
    Profile profile1 = createProfileWithUser(user1Id, lat, lon);
    Profile profile2 = createProfileWithUser(user2Id, lat, lon);
    given(profileRepository.findAllByLatitudeAndLongitude(lat, lon))
        .willReturn(List.of(profile1, profile2));

    // when
    weatherAlertService.checkAndSendAlerts(lat, lon, weathers);

    // then
    verify(outboxEventPublisher, times(2))
        .publish(eventCaptor.capture(), eq("WEATHER"), partitionKeyCaptor.capture());

    List<NotificationEvent> publishedEvents = eventCaptor.getAllValues();
    List<String> partitionKeys = partitionKeyCaptor.getAllValues();

    //receiverId 일치 확인
    NotificationEvent event1 = publishedEvents.get(0);
    assertThat(event1.receiverId()).isEqualTo(user1Id);
    assertThat(event1.eventType()).isEqualTo(NotificationEventType.WEATHER_ALERT);
    assertThat(event1.title()).isEqualTo("비/눈 예보 알림");
    assertThat(event1.content()).isEqualTo("오후에 비 예보가 있습니다. 우산을 챙기세요!");
    assertThat(event1.level()).isEqualTo(NotificationLevel.INFO);
    assertThat(partitionKeys.get(0)).isEqualTo(user1Id.toString());

    NotificationEvent event2 = publishedEvents.get(1);
    assertThat(event2.receiverId()).isEqualTo(user2Id);
    assertThat(partitionKeys.get(1)).isEqualTo(user2Id.toString());
  }

  @Test
  @DisplayName("날씨 급변이 감지되지 않으면 알림 이벤트를 발행하지 않는다")
  void sendWeatherAlertsIfChanged_whenNoAlertDetected_doesNotPublish() {
    // given
    Double lat = 37.4979;
    Double lon = 127.0276;
    List<Weather> weathers = List.of(createWeather(lat, lon));
    given(weatherChangeDetector.detect(weathers)).willReturn(Optional.empty());

    // when
    weatherAlertService.checkAndSendAlerts(lat, lon, weathers);

    // then
    verify(profileRepository, never()).findAllByLatitudeAndLongitude(any(), any());
    verify(outboxEventPublisher, never()).publish(any(), any(), any());
  }

  @Test
  @DisplayName("날씨 급변이 감지되었으나 해당 거점에 등록된 사용자가 없으면 이벤트를 발행하지 않는다")
  void checkAndSendAlerts_whenNoProfilesFound_doesNotPublish() {
    // given: 날씨 급변은 있지만 해당 거점 유저가 0명인 경우
    Double lat = 37.4979;
    Double lon = 127.0276;
    List<Weather> weathers = List.of(createWeather(lat, lon));

    WeatherAlertInfo alertInfo = new WeatherAlertInfo("비/눈 예보 알림", "비 예보가 있습니다.");

    given(weatherChangeDetector.detect(weathers)).willReturn(Optional.of(alertInfo));
    given(profileRepository.findAllByLatitudeAndLongitude(lat, lon)).willReturn(Collections.emptyList());

    // when
    weatherAlertService.checkAndSendAlerts(lat, lon, weathers);

    // then
    verify(outboxEventPublisher, never()).publish(any(), any(), any());
  }

  @Test
  @DisplayName("거점 프로필 목록이 null인 경우 알림을 발행하지 않고 안전하게 종료된다")
  void checkAndSendAlerts_whenProfilesNull_doesNotPublish() {
    // given
    given(weatherChangeDetector.detect(any())).willReturn(Optional.of(new WeatherAlertInfo("비 예보", "우산 챙기세요")));
    given(profileRepository.findAllByLatitudeAndLongitude(anyDouble(), anyDouble())).willReturn(null);

    // when
    weatherAlertService.checkAndSendAlerts(37.5, 127.0, List.of(createWeather(37.5, 127.0)));

    // then
    verify(outboxEventPublisher, never()).publish(any(), any(), any());
  }

  @Test
  @DisplayName("프로필의 userId가 null인 경우 해당 프로필은 건너뛰고 정상 유저에게만 알림을 발행한다")
  void checkAndSendAlerts_whenProfileUserIdNull_skipsAndPublishesOthers() {
    // given
    UUID validUserId = UUID.randomUUID();
    Profile nullUserProfile = new Profile(null, null, null, null, 37.5, 127.0, null, List.of("서울"));
    Profile validProfile = createProfileWithUser(validUserId, 37.5, 127.0);
    given(weatherChangeDetector.detect(any())).willReturn(Optional.of(new WeatherAlertInfo("비 예보", "우산 챙기세요")));
    given(profileRepository.findAllByLatitudeAndLongitude(anyDouble(), anyDouble()))
        .willReturn(List.of(nullUserProfile, validProfile));

    // when
    weatherAlertService.checkAndSendAlerts(37.5, 127.0, List.of(createWeather(37.5, 127.0)));

    // then
    verify(outboxEventPublisher, times(1)).publish(any(), any(), eq(validUserId.toString()));
  }


}