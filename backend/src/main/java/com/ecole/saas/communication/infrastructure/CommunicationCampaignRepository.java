package com.ecole.saas.communication.infrastructure;

import com.ecole.saas.communication.domain.CommunicationCampaign;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunicationCampaignRepository
        extends JpaRepository<CommunicationCampaign, UUID>, JpaSpecificationExecutor<CommunicationCampaign> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CommunicationCampaign c WHERE c.id = :id")
    Optional<CommunicationCampaign> findByIdForUpdate(@Param("id") UUID id);

    interface CampaignTotalsSummary {
        long getTotalCampaigns();

        long getTotalEstimatedCostXof();

        long getTotalActualCostXof();
    }

    Optional<CommunicationCampaign> findByIdempotencyKey(String idempotencyKey);

    Optional<CommunicationCampaign> findByReference(String reference);

    @Query("""
            SELECT COUNT(c.id) AS totalCampaigns,
                   COALESCE(SUM(c.estimatedCostXof), 0L) AS totalEstimatedCostXof,
                   COALESCE(SUM(c.actualCostXof), 0L) AS totalActualCostXof
            FROM CommunicationCampaign c
            """)
    CampaignTotalsSummary summarizeTotals();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE CommunicationCampaign c
            SET c.sentCount = CASE WHEN (c.sentCount + :sentDelta) < 0 THEN 0 ELSE (c.sentCount + :sentDelta) END,
                c.deliveredCount = CASE WHEN (c.deliveredCount + :deliveredDelta) < 0 THEN 0 ELSE (c.deliveredCount + :deliveredDelta) END,
                c.readCount = CASE WHEN (c.readCount + :readDelta) < 0 THEN 0 ELSE (c.readCount + :readDelta) END,
                c.updatedAt = :now
            WHERE c.id = :campaignId
            """)
    int incrementReadCounters(
            @Param("campaignId") UUID campaignId,
            @Param("sentDelta") int sentDelta,
            @Param("deliveredDelta") int deliveredDelta,
            @Param("readDelta") int readDelta,
            @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE CommunicationCampaign c
            SET c.queuedCount = CASE WHEN (c.queuedCount + :queuedDelta) < 0 THEN 0 ELSE (c.queuedCount + :queuedDelta) END,
                c.sentCount = CASE WHEN (c.sentCount + :sentDelta) < 0 THEN 0 ELSE (c.sentCount + :sentDelta) END,
                c.deliveredCount = CASE WHEN (c.deliveredCount + :deliveredDelta) < 0 THEN 0 ELSE (c.deliveredCount + :deliveredDelta) END,
                c.failedCount = CASE WHEN (c.failedCount + :failedDelta) < 0 THEN 0 ELSE (c.failedCount + :failedDelta) END,
                c.readCount = CASE WHEN (c.readCount + :readDelta) < 0 THEN 0 ELSE (c.readCount + :readDelta) END,
                c.actualCostXof = CASE WHEN (c.actualCostXof + :actualCostDelta) < 0L THEN 0L ELSE (c.actualCostXof + :actualCostDelta) END,
                c.status = CASE
                    WHEN c.status = com.ecole.saas.communication.CampaignStatus.CANCELLED
                        THEN com.ecole.saas.communication.CampaignStatus.CANCELLED
                    WHEN c.totalRecipients = 0
                        THEN com.ecole.saas.communication.CampaignStatus.COMPLETED
                    WHEN (CASE WHEN (c.queuedCount + :queuedDelta) < 0 THEN 0 ELSE (c.queuedCount + :queuedDelta) END) > 0
                        THEN com.ecole.saas.communication.CampaignStatus.QUEUED
                    WHEN (CASE WHEN (c.failedCount + :failedDelta) < 0 THEN 0 ELSE (c.failedCount + :failedDelta) END) = 0
                        THEN com.ecole.saas.communication.CampaignStatus.COMPLETED
                    WHEN (
                        (CASE WHEN (c.sentCount + :sentDelta) < 0 THEN 0 ELSE (c.sentCount + :sentDelta) END)
                        + (CASE WHEN (c.deliveredCount + :deliveredDelta) < 0 THEN 0 ELSE (c.deliveredCount + :deliveredDelta) END)
                        + (CASE WHEN (c.readCount + :readDelta) < 0 THEN 0 ELSE (c.readCount + :readDelta) END)
                    ) > 0
                        THEN com.ecole.saas.communication.CampaignStatus.PARTIALLY_FAILED
                    ELSE com.ecole.saas.communication.CampaignStatus.FAILED
                END,
                c.completedAt = CASE
                    WHEN c.status = com.ecole.saas.communication.CampaignStatus.CANCELLED THEN c.completedAt
                    WHEN c.completedAt IS NOT NULL THEN c.completedAt
                    WHEN c.totalRecipients = 0
                        OR (CASE WHEN (c.queuedCount + :queuedDelta) < 0 THEN 0 ELSE (c.queuedCount + :queuedDelta) END) = 0
                        THEN :now
                    ELSE c.completedAt
                END,
                c.updatedAt = :now
            WHERE c.id = :campaignId
            """)
    int applyCounterDeltasAndRefreshStatus(
            @Param("campaignId") UUID campaignId,
            @Param("queuedDelta") int queuedDelta,
            @Param("sentDelta") int sentDelta,
            @Param("deliveredDelta") int deliveredDelta,
            @Param("failedDelta") int failedDelta,
            @Param("readDelta") int readDelta,
            @Param("actualCostDelta") long actualCostDelta,
            @Param("now") Instant now);
}
