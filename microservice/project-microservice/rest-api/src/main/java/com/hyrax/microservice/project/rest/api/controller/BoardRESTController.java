package com.hyrax.microservice.project.rest.api.controller;

import com.hyrax.microservice.project.rest.api.domain.response.ErrorResponse;
import com.hyrax.microservice.project.rest.api.security.AuthenticationUserDetailsHelper;
import com.hyrax.microservice.project.service.api.BoardService;
import com.hyrax.microservice.project.service.exception.BoardAlreadyExistsException;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping(path = "/board/{boardName}")
public class BoardRESTController {

    private static final Logger LOGGER = LoggerFactory.getLogger(BoardRESTController.class);

    private final BoardService boardService;

    private final AuthenticationUserDetailsHelper authenticationUserDetailsHelper;

    @PostMapping
    public ResponseEntity<Void> create(@PathVariable final String boardName) {
        final String requestedBy = authenticationUserDetailsHelper.getUsername();
        LOGGER.info("Received board creation request = [boardName={} requestedBy={}]", boardName, requestedBy);

        boardService.create(boardName, requestedBy);

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(BoardAlreadyExistsException.class)
    protected ResponseEntity<ErrorResponse> handleBoardAlreadyExistsException(final BoardAlreadyExistsException e) {
        logException(e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.builder()
                        .message(e.getMessage())
                        .build()
                );
    }

    private void logException(final Exception e) {
        LOGGER.error(e.getMessage(), e);
    }
}
