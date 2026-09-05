package com.liveclass.settlement.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** findAll(Sort)만 쓴다. 실적 없는 크리에이터를 운영자 목록에 넣기 위함. */
public interface CreatorJpaRepository extends JpaRepository<CreatorEntity, String> {
}
