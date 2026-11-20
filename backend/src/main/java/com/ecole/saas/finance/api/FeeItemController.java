package com.ecole.saas.finance.api;

import com.ecole.saas.finance.application.FeeItemService;
import com.ecole.saas.finance.domain.FeeCategory;
import com.ecole.saas.finance.domain.FeeItem;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalogue des frais (scolarité, inscription, annexes) isolé par établissement (tenant issu du JWT).
 */
@RestController
@RequestMapping("/api/v1/finance/fee-items")
public class FeeItemController {

    private final FeeItemService feeItemService;

    public FeeItemController(FeeItemService feeItemService) {
        this.feeItemService = feeItemService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<FeeItemResponse>> listFeeItems(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID gradeLevelId,
            @RequestParam(required = false) FeeCategory category,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = FinancePagination.of(
                page, size, sortBy, direction, FinancePagination.FEE_ITEM_SORT_FIELDS, "code", Sort.Direction.ASC);
        Page<FeeItemResponse> items = feeItemService
                .listFeeItems(academicYearId, gradeLevelId, category, active, search, pageable)
                .map(FeeItemResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(items));
    }

    @GetMapping("/{feeItemId}")
    public ResponseEntity<FeeItemResponse> getFeeItem(@PathVariable UUID feeItemId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(FeeItemResponse.from(feeItemService.getFeeItem(feeItemId)));
    }

    @PostMapping
    public ResponseEntity<FeeItemResponse> createFeeItem(@Valid @RequestBody CreateFeeItemRequest request) {
        FeeItem created = feeItemService.createFeeItem(
                request.academicYearId(),
                request.gradeLevelId(),
                request.code(),
                request.label(),
                request.category(),
                request.frequency(),
                request.amountXof(),
                request.mandatory(),
                request.description());
        return ResponseEntity.created(URI.create("/api/v1/finance/fee-items/" + created.getId()))
                .cacheControl(CacheControl.noStore())
                .body(FeeItemResponse.from(created));
    }

    @PutMapping("/{feeItemId}")
    public ResponseEntity<FeeItemResponse> updateFeeItem(
            @PathVariable UUID feeItemId,
            @Valid @RequestBody UpdateFeeItemRequest request) {
        FeeItem updated = feeItemService.updateFeeItem(
                feeItemId,
                request.gradeLevelId(),
                request.label(),
                request.category(),
                request.frequency(),
                request.amountXof(),
                request.mandatory(),
                request.description());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(FeeItemResponse.from(updated));
    }

    @PostMapping("/{feeItemId}/activate")
    public ResponseEntity<FeeItemResponse> activateFeeItem(@PathVariable UUID feeItemId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(FeeItemResponse.from(feeItemService.activateFeeItem(feeItemId)));
    }

    @PostMapping("/{feeItemId}/deactivate")
    public ResponseEntity<FeeItemResponse> deactivateFeeItem(@PathVariable UUID feeItemId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(FeeItemResponse.from(feeItemService.deactivateFeeItem(feeItemId)));
    }
}
