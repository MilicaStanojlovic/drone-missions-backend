package com.project.drone_missions.web.controller.bid;

import com.project.drone_missions.business.service.bid.BidService;
import com.project.drone_missions.web.dto.bid.BidRequest;
import com.project.drone_missions.web.dto.bid.BidResponse;
import com.project.drone_missions.web.mapper.bid.BidMapper;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1")
public class BidController {
    private final BidService service;
    private final BidMapper mapper;

    /** Place the caller's bid on a mission, or update their pending one. */
    @PostMapping("/missions/{missionId}/bids")
    @PreAuthorize("hasRole('PILOT')")
    public ResponseEntity<BidResponse> place(@PathVariable Long missionId,
                                             @Valid @RequestBody BidRequest request,
                                             @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(mapper.toResponse(
                service.place(missionId, userId, request.amount(), request.message())));
    }

    /** The mission's owner sees every bid; anyone else sees only their own. */
    @GetMapping("/missions/{missionId}/bids")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BidResponse>> listForMission(@PathVariable Long missionId,
                                                            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(service.listForMission(missionId, userId).stream()
                .map(mapper::toResponse)
                .toList());
    }

    /** Every bid the calling pilot has placed, with statuses. */
    @GetMapping("/bids/my")
    @PreAuthorize("hasRole('PILOT')")
    public ResponseEntity<List<BidResponse>> myBids(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(service.myBids(userId).stream()
                .map(mapper::toResponse)
                .toList());
    }

    /** Withdraw (delete) the caller's pending bid. */
    @DeleteMapping("/bids/{id}")
    @PreAuthorize("hasRole('PILOT')")
    public ResponseEntity<Void> withdraw(@PathVariable Long id,
                                         @AuthenticationPrincipal Long userId) {
        service.withdraw(id, userId);
        return ResponseEntity.noContent().build();
    }

    /** Accept one bid: rejects the rest and awards the mission to its pilot. */
    @PostMapping("/bids/{id}/accept")
    @PreAuthorize("hasRole('DESIGNER')")
    public ResponseEntity<BidResponse> accept(@PathVariable Long id,
                                              @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(mapper.toResponse(service.accept(id, userId)));
    }
}
