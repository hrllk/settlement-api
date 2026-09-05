package com.liveclass.settlement.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 크리에이터. 계산에는 안 쓰이지만, 실적 0인 크리에이터를 운영자 목록에 넣으려면
 * 판매·취소 자료만으로는 존재를 알 수 없어 필요하다.
 */
@Entity
@Table(name = "creators")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatorEntity {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    public CreatorEntity(String id, String name) {
        this.id = id;
        this.name = name;
    }
}
