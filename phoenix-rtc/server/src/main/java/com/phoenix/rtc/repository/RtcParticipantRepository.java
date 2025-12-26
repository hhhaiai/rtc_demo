package com.phoenix.rtc.repository;

import com.phoenix.rtc.model.entity.RtcParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RtcParticipantRepository extends JpaRepository<RtcParticipant, Long> {

    /**
     * 查询某房间的所有参与者
     */
    List<RtcParticipant> findBySessionId(Long sessionId);

    /**
     * 查询用户在某个房间的参与记录
     */
    Optional<RtcParticipant> findBySessionIdAndUserId(Long sessionId, String userId);

    /**
     * 查询用户正在进行的通话
     */
    @Query("SELECT p FROM RtcParticipant p JOIN RtcSession s ON p.sessionId = s.id " +
           "WHERE p.userId = :userId AND s.status = 0 AND p.leaveTime IS NULL")
    List<RtcParticipant> findActiveParticipation(String userId);

    /**
     * 更新离开时间
     */
    @Modifying
    @Query("UPDATE RtcParticipant p SET p.leaveTime = :leaveTime, p.duration = :duration WHERE p.id = :id")
    void updateLeaveTime(Long id, LocalDateTime leaveTime, Integer duration);

    /**
     * 统计房间在线人数
     */
    @Query("SELECT COUNT(p) FROM RtcParticipant p JOIN RtcSession s ON p.sessionId = s.id " +
           "WHERE s.roomName = :roomName AND p.leaveTime IS NULL")
    Integer countOnlineParticipants(String roomName);
}
