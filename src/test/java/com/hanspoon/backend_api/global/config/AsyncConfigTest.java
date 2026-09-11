package com.hanspoon.backend_api.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

    @Test
    void scanExecutorRunsOnlyTwoTasksAndRejectsInsteadOfQueueing() throws Exception {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfig().scanTaskExecutor(2, 2, 0);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Runnable blockingTask = () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };

        try {
            executor.execute(blockingTask);
            executor.execute(blockingTask);
            assertThat(started.await(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS))
                    .isTrue();

            assertThat(executor.getActiveCount()).isEqualTo(2);
            assertThat(executor.getThreadPoolExecutor().getQueue()).isEmpty();
            assertThatThrownBy(() -> executor.execute(() -> {})).isInstanceOf(TaskRejectedException.class);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
