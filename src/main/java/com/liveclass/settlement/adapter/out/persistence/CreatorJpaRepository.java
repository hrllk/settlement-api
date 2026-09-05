package com.liveclass.settlement.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 비어 있다. {@code findAll}이 {@code JpaRepository}에서 온다.
 * {@code findAllCreatorIds()}가 쓴다.
 */
public interface CreatorJpaRepository extends JpaRepository<CreatorEntity, String> {
}
