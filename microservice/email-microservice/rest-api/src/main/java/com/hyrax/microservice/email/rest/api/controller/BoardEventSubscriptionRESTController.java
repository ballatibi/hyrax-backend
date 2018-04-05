package com.hyrax.microservice.email.rest.api.controller;

import com.hyrax.microservice.email.rest.api.domain.request.BoardEventSubscriptionRequest;
import com.hyrax.microservice.email.rest.api.domain.response.BoardEventSubscriptionResponse;
import com.hyrax.microservice.email.rest.api.security.AuthenticationUserDetailsHelper;
import com.hyrax.microservice.email.service.api.BoardEventSubscriptionService;
import com.hyrax.microservice.email.service.api.model.BoardEventSubscription;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/email/settings/board")
@Api(description = "Operations about board event subscriptions")
@AllArgsConstructor
public class BoardEventSubscriptionRESTController {

    private final AuthenticationUserDetailsHelper authenticationUserDetailsHelper;

    private final BoardEventSubscriptionService boardEventSubscriptionService;

    private final ConversionService conversionService;

    private final ModelMapper modelMapper;

    @GetMapping
    @ApiOperation(httpMethod = "GET", value = "Resource to list the board event subscription settings for the given user")
    public ResponseEntity<BoardEventSubscriptionResponse> retrieveBoardEventSubscriptionSettings() {
        final String username = authenticationUserDetailsHelper.getUsername();
        return ResponseEntity.ok(conversionService.convert(boardEventSubscriptionService.findByUsername(username), BoardEventSubscriptionResponse.class));
    }

    @PutMapping
    @ApiOperation(httpMethod = "PUT", value = "Resource to modify the board event subscription settings for the given user")
    public ResponseEntity<Void> saveOrUpdateBoardEventSubscriptionSettings(@RequestBody final BoardEventSubscriptionRequest boardEventSubscriptionRequest) {
        boardEventSubscriptionService.saveOrUpdate(modelMapper.map(boardEventSubscriptionRequest, BoardEventSubscription.class));
        return ResponseEntity.noContent().build();
    }

}