package com.hanspoon.backend_api.domain.user.entity;

import com.hanspoon.backend_api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    @Column(name = "language_code", length = 2, nullable = false)
    private String languageCode;

    private User(String email, String nickname, String languageCode) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.nickname = nickname;
        this.languageCode = languageCode;
    }

    public static User create(String email, String nickname, String languageCode) {
        String resolvedLanguage = (languageCode == null || languageCode.isBlank()) ? "en" : languageCode;
        return new User(email, nickname, resolvedLanguage);
    }

    public void changeLanguage(String languageCode) {
        if (languageCode != null && !languageCode.isBlank()) {
            this.languageCode = languageCode;
        }
    }

    public void changeNickname(String nickname) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
    }
}
