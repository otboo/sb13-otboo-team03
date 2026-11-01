package com.otboo.team03.profile.repository;

import com.otboo.team03.profile.dto.Gender;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ProfileRepositoryTest {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private ProfileRepository profileRepository;

  @Test
  @DisplayName("User와 연결된 Profile을 데이터베이스에 정상적으로 저장하고 조회할 수 있다")
  void saveAndFindProfile() {
    // given: 1. 먼저 회원을 저장합니다.
    User user = new User("woody@otboo.io", "password123!", "우디");
    User savedUser = userRepository.save(user);

    // given: 2. 저장된 회원의 정보를 가진 프로필을 만듭니다.
    Profile profile = new Profile(
        savedUser,
        "https://cdn.otboo.io/profiles/woody.png",
        Gender.MALE,
        LocalDate.of(1995, 5, 20),
        37.5665,
        126.9780,
        3,
        List.of("서울특별시", "중구", "명동")
    );

    // when: 3. 프로필을 DB에 저장합니다.
    Profile savedProfile = profileRepository.save(profile);

    // then: 4. User의 ID로 프로필을 조회했을 때 잘 찾아와지는지 검증합니다!
    Optional<Profile> foundProfile = profileRepository.findById(savedUser.getId());

    assertThat(foundProfile).isPresent();
    assertThat(foundProfile.get().getUserId()).isEqualTo(savedUser.getId());
    assertThat(foundProfile.get().getGender()).isEqualTo(Gender.MALE);
    assertThat(foundProfile.get().getLocationNames()).containsExactly("서울특별시", "중구", "명동");
  }
}