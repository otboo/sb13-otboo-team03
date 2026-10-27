package com.otboo.team03.global.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

@MappedSuperclass
@Getter
@ToString(callSuper = true)
public abstract class CreateEntity extends BaseEntity {


    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
