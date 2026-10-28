
package com.otboo.team03.global.security;

import com.otboo.team03.user.dto.response.UserDto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class OtbooUserDetails implements UserDetails {

    private final UserDto userDto;
    private final String password;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + userDto.role().name()));
    }

    @Override
    public String getUsername() {
        return userDto.email();
    }

    @Override
    public boolean isAccountNonLocked() {
        return !userDto.locked();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof OtbooUserDetails that)) {
            return false;
        }

        return userDto.id().equals(that.userDto.id());
    }

    @Override
    public int hashCode() {
        return userDto.id().hashCode();
    }
}
