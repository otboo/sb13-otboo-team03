package com.otboo.team03.directmessage.repository;

import com.otboo.team03.directmessage.entity.DirectMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {

    List<DirectMessage> findByDmKeyOrderByCreatedAtDescIdDesc(
            String dmKey,
            Pageable pageable
    );

    @Query("""
            select dm
            from DirectMessage dm
            where dm.dmKey = :dmKey
              and (
                    dm.createdAt < :cursor
                    or (dm.createdAt = :cursor and dm.id < :idAfter)
              )
            order by dm.createdAt desc, dm.id desc
            """)
    List<DirectMessage> findPageByDmKeyAfterCursor(
            @Param("dmKey") String dmKey,
            @Param("cursor") Instant cursor,
            @Param("idAfter") UUID idAfter,
            Pageable pageable
    );

    long countByDmKey(String dmKey);
}
