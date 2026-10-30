package com.sigak.collection.runner

import com.sigak.collection.service.CollectionAutomationService
import org.springframework.scheduling.annotation.Scheduled

class CollectionAutomationScheduledJob(private val service: CollectionAutomationService) {
    // 고정 주기가 아니라 완료 후 대기하므로 외부 fetch가 지연돼도 다음 작업이 겹치지 않는다.
    @Scheduled(
        fixedDelayString = "\${sigak.collection.automation.fixed-delay:PT10M}",
        initialDelayString = "\${sigak.collection.automation.initial-delay:PT30S}"
    )
    fun tick() {
        service.runCycle()
    }
}
