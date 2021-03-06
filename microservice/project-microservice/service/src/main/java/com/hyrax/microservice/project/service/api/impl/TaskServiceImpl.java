package com.hyrax.microservice.project.service.api.impl;

import com.google.common.collect.Lists;
import com.hyrax.microservice.project.data.dao.BoardDAO;
import com.hyrax.microservice.project.data.dao.TaskDAO;
import com.hyrax.microservice.project.data.entity.SingleTaskEntity;
import com.hyrax.microservice.project.data.entity.TaskEntity;
import com.hyrax.microservice.project.service.api.TaskService;
import com.hyrax.microservice.project.service.api.impl.checker.TaskOperationChecker;
import com.hyrax.microservice.project.service.api.impl.helper.TaskEventEmailSenderHelper;
import com.hyrax.microservice.project.service.api.impl.helper.WatchedTaskEventEmailSenderHelper;
import com.hyrax.microservice.project.service.domain.SingleTask;
import com.hyrax.microservice.project.service.domain.Task;
import com.hyrax.microservice.project.service.domain.TaskFilterDetails;
import com.hyrax.microservice.project.service.exception.ResourceNotFoundException;
import com.hyrax.microservice.project.service.exception.column.ColumnDoesNotExistException;
import com.hyrax.microservice.project.service.exception.task.TaskAdditionOperationNotAllowedException;
import com.hyrax.microservice.project.service.exception.task.TaskRemovalOperationNotAllowedException;
import com.hyrax.microservice.project.service.exception.task.TaskUpdateOperationNotAllowedException;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.math.NumberUtils;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class TaskServiceImpl implements TaskService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskServiceImpl.class);

    private final TaskOperationChecker taskOperationChecker;

    private final TaskDAO taskDAO;

    private final BoardDAO boardDAO;

    private final ModelMapper modelMapper;

    private final TaskEventEmailSenderHelper taskEventEmailSenderHelper;

    private final WatchedTaskEventEmailSenderHelper watchedTaskEventEmailSenderHelper;

    @Override
    @Transactional(readOnly = true)
    public List<Task> findAllByBoardNameAndColumnName(final String boardName, final String columnName, final TaskFilterDetails taskFilterDetails) {
        return taskDAO.findAllByBoardNameAndColumnName(boardName, columnName, taskFilterDetails.getAssignedUsername(), taskFilterDetails.getLabelNames())
                .stream()
                .map(taskEntity -> modelMapper.map(taskEntity, Task.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SingleTask> findSingleTask(final String boardName, final String columnName, final Long taskId) {
        return taskDAO.findSingleTask(boardName, columnName, taskId)
                .map(singleTaskEntity -> modelMapper.map(singleTaskEntity, SingleTask.class));
    }

    @Override
    @Transactional
    public void create(final String boardName, final String columnName, final String taskName, final String description, final String requestedBy) {
        final boolean isOperationAllowed = taskOperationChecker.isOperationAllowed(boardName, requestedBy);

        if (isOperationAllowed) {
            try {
                LOGGER.info("Trying to save the task = [boardName={} columnName={} taskName={} description={}]", boardName, columnName, taskName, description);
                final Long taskId = taskDAO.save(boardName, columnName, taskName, description, requestedBy);
                taskEventEmailSenderHelper.sendTaskCreationEmail(boardDAO.findAllBoardMemberNameByBoardName(boardName), boardName, columnName, taskId, taskName, requestedBy);
                LOGGER.info("Task saving was successful [boardName={} columnName={} taskName={} description={}]", boardName, columnName, taskName, description);
            } catch (final DataIntegrityViolationException e) {
                final String errorMessage = String.format("Column does not exist [boardName=%s columnName=%s]", boardName, columnName);
                LOGGER.error(errorMessage, e);
                throw new ResourceNotFoundException(errorMessage);
            }
        } else {
            throw new TaskAdditionOperationNotAllowedException(requestedBy);
        }
    }

    @Override
    @Transactional
    public void update(final String boardName, final String columnName, final Long taskId, final String taskName, final String description, final String requestedBy) {
        final boolean isOperationAllowed = taskOperationChecker.isOperationAllowed(boardName, requestedBy);

        if (isOperationAllowed) {
            final Optional<SingleTaskEntity> singleTaskEntity = taskDAO.findSingleTask(boardName, taskId);
            taskDAO.update(boardName, columnName, taskId, taskName, description);
            singleTaskEntity.ifPresent(
                    taskEntity -> watchedTaskEventEmailSenderHelper.sendWatchedTaskUpdateEmail(taskEntity.getWatchedUsers(), boardName, columnName, taskId,
                            taskEntity.getTaskName(), taskEntity.getDescription(), taskName, description, requestedBy)
            );
        } else {
            throw new TaskUpdateOperationNotAllowedException(requestedBy);
        }
    }

    @Override
    @Transactional
    public void updatePosition(final String boardName, final String columnName, final Long taskId, final long from, final long to, final String requestedBy) {
        final boolean isOperationAllowed = taskOperationChecker.isOperationAllowed(boardName, requestedBy);

        if (isOperationAllowed) {

            final List<TaskEntity> up = Lists.newArrayList();
            final List<TaskEntity> down = Lists.newArrayList();
            if (from < to) {
                populateByFromUpToDown(boardName, columnName, taskId, to, up, down);
            } else if (from > to) {
                populateByFromDownToUp(boardName, columnName, taskId, to, up, down);
            }

            if (from != to) {
                updateTasksByIndex(boardName, columnName, up, NumberUtils.LONG_ZERO);
                updateTasksByIndex(boardName, columnName, down, to);
                taskDAO.updatePosition(boardName, columnName, taskId, to);
            }
        } else {
            throw new TaskUpdateOperationNotAllowedException(requestedBy);
        }
    }

    @Override
    @Transactional
    public void updatePositionBetweenColumns(final String boardName, final String columnName, final Long taskId, final String newColumnName, final String requestedBy) {
        final boolean isOperationAllowed = taskOperationChecker.isOperationAllowed(boardName, requestedBy);

        if (isOperationAllowed) {
            if (!columnName.equals(newColumnName)) {
                try {
                    taskDAO.updatePositionBetweenColumns(boardName, columnName, taskId, newColumnName);
                    refreshTaskIndexes(boardName, columnName);
                    refreshTaskIndexes(boardName, newColumnName);

                    taskDAO.findSingleTask(boardName, taskId)
                            .ifPresent(
                                    singleTaskEntity -> watchedTaskEventEmailSenderHelper.sendWatchedTaskMoveBetweenColumnsEmail(singleTaskEntity.getWatchedUsers(),
                                            boardName, taskId, singleTaskEntity.getTaskName(), columnName, newColumnName, requestedBy)
                            );

                } catch (final DataIntegrityViolationException e) {
                    final String errorMessage = String.format("Column does not exist with name=%s on board=%s", newColumnName, boardName);
                    LOGGER.error(errorMessage, e);
                    throw new ColumnDoesNotExistException(errorMessage);
                }
            }
        } else {
            throw new TaskUpdateOperationNotAllowedException(requestedBy);
        }
    }

    @Override
    @Transactional
    public void remove(final String boardName, final String columnName, final Long taskId, final String requestedBy) {
        final boolean isOperationAllowed = taskOperationChecker.isOperationAllowed(boardName, requestedBy);
        if (isOperationAllowed) {
            final Optional<SingleTaskEntity> taskEntity = taskDAO.findSingleTask(boardName, columnName, taskId);
            taskDAO.delete(boardName, columnName, taskId);
            refreshTaskIndexes(boardName, columnName);
            if (taskEntity.isPresent()) {
                taskEventEmailSenderHelper.sendTaskRemovalEmail(boardDAO.findAllBoardMemberNameByBoardName(boardName), boardName, columnName,
                        taskId, taskEntity.get().getTaskName(), requestedBy);
            }
        } else {
            throw new TaskRemovalOperationNotAllowedException(requestedBy);
        }
    }

    private void populateByFromUpToDown(final String boardName, final String columnName, final Long taskId, final Long newColumnIndex,
                                        final List<TaskEntity> up, final List<TaskEntity> down) {
        taskDAO.findAllByBoardNameAndColumnName(boardName, columnName)
                .stream()
                .filter(task -> task.getTaskId().longValue() != taskId.longValue())
                .forEach(task -> {
                    if (task.getTaskIndex() <= newColumnIndex) {
                        up.add(task);
                    } else {
                        down.add(task);
                    }
                });
    }

    private void populateByFromDownToUp(final String boardName, final String columnName, final Long taskId, final Long newColumnIndex,
                                        final List<TaskEntity> up, final List<TaskEntity> down) {
        taskDAO.findAllByBoardNameAndColumnName(boardName, columnName)
                .stream()
                .filter(task -> task.getTaskId().longValue() != taskId.longValue())
                .forEach(task -> {
                    if (task.getTaskIndex() < newColumnIndex) {
                        up.add(task);
                    } else {
                        down.add(task);
                    }
                });
    }

    private void updateTasksByIndex(final String boardName, final String columnName, final List<TaskEntity> tasks, final Long startIndex) {
        final AtomicLong index = new AtomicLong(startIndex);

        tasks.forEach(task -> taskDAO.updatePosition(boardName, columnName, task.getTaskId(), index.incrementAndGet()));
    }

    private void refreshTaskIndexes(final String boardName, final String columnName) {
        final AtomicLong index = new AtomicLong(NumberUtils.LONG_ZERO);

        taskDAO.findAllByBoardNameAndColumnName(boardName, columnName)
                .forEach(task -> taskDAO.updatePosition(boardName, columnName, task.getTaskId(), index.incrementAndGet()));
    }
}
