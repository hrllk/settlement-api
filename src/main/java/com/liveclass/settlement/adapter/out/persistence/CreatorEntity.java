package com.liveclass.settlement.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 크리에이터. 정산 계산 자체에는 안 쓰이지만 creator-3 때문에 필요하다.
 *
 * <p>creator-3은 2025-03에 판매도 취소도 없다. 운영자 기간 집계는 실적 0인
 * 크리에이터도 목록에 0원으로 넣어야 하는데, 판매·취소 자료만으로는 그 존재를
 * 알 방법이 없다. {@code findAllCreatorIds()}가 이 테이블을 읽는다.
 *
 * <p>인덱스를 두지 않는다. PK 조회와 전체 스캔뿐이다.
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
