package com.phoenix.rtc.repository;

import com.phoenix.rtc.model.entity.RtcSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RtcSessionRepository extends JpaRepository<RtcSession, Long> {

    /**
     * 根据房间名称查询会话
     */
    Optional<RtcSession> findByRoomName(String roomName);

    /**
     * 根据发起人查询进行中的会话
     */
    List<RtcSession> findByInitiatorIdAndStatus(String initiatorId, Integer status);

    /**
     * 查询用户参与的进行中会话
     */
    @Query("SELECT s FROM RtcSession s JOIN RtcParticipant p ON s.id = p.sessionId " +
           "WHERE p.userId = :userId AND s.status = :status")
    List<RtcSession> findActiveSessionsByUserId(String userId, Integer status);

    /**
     * 查询最近的会话记录
     */
    @Query("SELECT s FROM RtcSession s WHERE s.initiatorId = :userId ORDER BY s.createdAt DESC")
    List<RtcSession> findRecentSessionsByUser(String userId);
}
