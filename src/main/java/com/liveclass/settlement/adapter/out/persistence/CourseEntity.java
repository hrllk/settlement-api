package com.liveclass.settlement.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 강의. 판매를 크리에이터에 연결하는 유일한 경로다.
 *
 * <p>판매는 {@code course_id}만 갖는다. {@code findSales(from, to, creatorId)}는
 * {@code sales → courses}를 거쳐 {@code creator_id}로 좁히고,
 * {@code findCancels}는 {@code cancels → sales → courses}로 한 단계 더 간다.
 *
 * <p>판매에 {@code creator_id}를 비정규화하면 조인이 사라지지만, 강의 소유자가
 * 바뀔 때 판매 행을 전부 같이 고쳐야 하고 안 고치면 과거 정산이 조용히 틀어진다.
 * 데이터가 7건이라 조인 비용이 0이므로 정규화를 택했다.
 *
 * <p>Task 4의 {@code CourseNotFound} 판정도 이 테이블을 읽는다.
 */
@Entity
@Table(name = "courses", indexes = {
        @Index(name = "idx_courses_creator", columnList = "creator_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseEntity {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(name = "creator_id", length = 64, nullable = false)
    private String creatorId;

    @Column(nullable = false)
    private String title;

    public CourseEntity(String id, String creatorId, String title) {
        this.id = id;
        this.creatorId = creatorId;
        this.title = title;
    }
}
