package com.ecole.saas.center.infrastructure;

import com.ecole.saas.center.domain.CenterScheduleSlot;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CenterScheduleSlotRepository extends JpaRepository<CenterScheduleSlot, UUID> {

    List<CenterScheduleSlot> findByGroupIdOrderByActiveDescDayOfWeekAscStartTimeAsc(UUID groupId);

    List<CenterScheduleSlot> findByGroupIdAndActiveTrue(UUID groupId);

    List<CenterScheduleSlot> findByGroupIdAndDayOfWeekAndActiveTrue(UUID groupId, DayOfWeek dayOfWeek);

    List<CenterScheduleSlot> findByTeacherIdAndDayOfWeekAndActiveTrue(UUID teacherId, DayOfWeek dayOfWeek);
}
