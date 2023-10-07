package com.example.telegrambot.repository;

import com.example.telegrambot.entity.Task;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;


@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    @Transactional
    @Modifying
    @Query("update Task t set t.alarmActive=false where t.userId =?1")
    void deactivateAlarmsForUser(Long userId);

    @Modifying
    @Query("update  Task t set t.alarmActive = true where t.id = ?1")
    void setAlarmActive(Long id);
    @Modifying
    @Transactional
    @Query("delete  Task t where t.userId =  ?1")
    void deleteByUserId(Long userId);
}
