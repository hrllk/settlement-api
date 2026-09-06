package com.liveclass.settlement.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 강의. 판매를 크리에이터에 잇는 유일한 경로다 — sales → courses → creator_id. */
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
