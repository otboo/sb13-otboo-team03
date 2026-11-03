package com.otboo.team03.clothes.entity;

import com.otboo.team03.global.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.otboo.team03.user.entity.User;

/**
 * 사용자가 등록한 의상의 기본 정보.
 * 의상별 속성 값은 ClothesAttribute에서 이 엔티티를 참조하여 관리한다.
 * ID는 BaseEntity에서 상속받는다.
 */
@Entity
@Table(name = "clothes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Clothes extends BaseEntity {

    // 사용자가 지정한 의상 이름. 예: 흰색 반팔 티셔츠
    @Column(nullable = false, length = 255)
    private String name;

    // 의상 분류. enum 순서 변경의 영향을 받지 않도록 이름을 문자열로 저장한다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClothesType type;

    // 기존 이미지 URL 보관용. 만료되는 S3 서명 URL은 이 필드에 저장하지 않는다.
    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    // 비공개 S3 파일의 영구 식별자. 예: clothes/UUID.png. 접근 URL과 구분한다.
    // 이미지가 없는 기존 의상은 null이며, DTO에는 key를 노출하지 않는다.
    @Column(name = "image_key", length = 1024)
    private String imageKey;

    // 여러 의상이 한 사용자를 참조한다. DB에는 기존 owner_id 외래 키를 사용한다.
    // 의상 삭제가 사용자 삭제로 전파되지 않도록 cascade는 지정하지 않는다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    public Clothes(String name, ClothesType type,
                   String imageUrl, User owner) {
        this.name = name;
        this.type = type;
        this.imageUrl = imageUrl;
        this.owner = owner;
    }
    /**
     * 업로드가 완료된 파일의 key를 보관한다.
     * null을 전달하면 기존 key가 제거되어 S3 이미지가 없는 상태가 된다.
     * 따라서 수정 요청에 이미지가 미첨부된 경우에는 Service에서 이 메서드를 호출하지 않아야 한다.
     * 미첨부를 updateImageKey(null)로 처리하면 기존 이미지 연결이 사라진다.
     * 새 이미지 업로드가 성공한 경우에만 반환된 key로 호출하여 교체한다.
     * 실제 S3 업로드·파일 삭제·서명 URL 생성은 이 엔티티에서 수행하지 않는다.
     */
    public void updateImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public void update(String name, ClothesType type) {
        // PATCH에서 생략되거나 null인 값은 기존 값을 유지한다.
        if (name != null) {
            this.name = name;
        }
        if (type != null) {
            this.type = type;
        }
    }
}
