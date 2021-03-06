package com.hyrax.microservice.project.service.api;

import com.hyrax.microservice.project.service.domain.SingleTask;
import com.hyrax.microservice.project.service.domain.Task;
import com.hyrax.microservice.project.service.domain.TaskFilterDetails;

import java.util.List;
import java.util.Optional;

public interface TaskService {

    List<Task> findAllByBoardNameAndColumnName(String boardName, String columnName, TaskFilterDetails taskFilterDetails);

    Optional<SingleTask> findSingleTask(String boardName, String columnName, Long taskId);

    void create(String boardName, String columnName, String taskName, String description, String requestedBy);

    void update(String boardName, String columnName, Long taskId, String taskName, String description, String requestedBy);

    void updatePosition(String boardName, String columnName, Long taskId, long from, long to, String requestedBy);

    void updatePositionBetweenColumns(String boardName, String columnName, Long taskId, String newColumnName, String requestedBy);

    void remove(String boardName, String columnName, Long taskId, String requestedBy);
}
