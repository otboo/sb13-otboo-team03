package com.otboo.team03.global.domain;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

@MappedSuperclass
@Getter
@ToString(callSuper = true)
public abstract class UpdateEntity extends CreateEntity {

    @LastModifiedDate
    private Instant updatedAt;
}
