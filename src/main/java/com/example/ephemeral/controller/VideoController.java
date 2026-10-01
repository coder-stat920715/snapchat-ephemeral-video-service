package com.example.ephemeral.controller;

import com.example.ephemeral.dto.CompleteRequest;
import com.example.ephemeral.dto.CompleteResponse;
import com.example.ephemeral.dto.PlayTokenRequest;
import com.example.ephemeral.dto.PlayTokenResponse;
import com.example.ephemeral.dto.UploadRequest;
import com.example.ephemeral.dto.UploadRequestResponse;
import com.example.ephemeral.dto.VideoStatusResponse;
import com.example.ephemeral.service.VideoLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoLifecycleService lifecycleService;

    @PostMapping("/upload-request")
    public ResponseEntity<UploadRequestResponse> uploadRequest(@Valid @RequestBody UploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lifecycleService.requestUpload(request));
    }

    @PostMapping("/{videoId}/confirm")
    public ResponseEntity<VideoStatusResponse> confirm(@PathVariable UUID videoId) {
        return ResponseEntity.ok(lifecycleService.confirmUpload(videoId));
    }

    @PostMapping("/{videoId}/play-token")
    public ResponseEntity<PlayTokenResponse> playToken(@PathVariable UUID videoId,
                                                       @Valid @RequestBody PlayTokenRequest request) {
        return ResponseEntity.ok(lifecycleService.issuePlayToken(videoId, request));
    }

    @PostMapping("/{videoId}/complete")
    public ResponseEntity<CompleteResponse> complete(@PathVariable UUID videoId,
                                                     @Valid @RequestBody CompleteRequest request) {
        return ResponseEntity.ok(lifecycleService.complete(videoId, request));
    }
}
