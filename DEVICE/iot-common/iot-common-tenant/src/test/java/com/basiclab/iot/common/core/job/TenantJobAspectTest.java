package com.basiclab.iot.common.core.job;

import com.basiclab.iot.common.config.YudaoTenantAutoConfiguration;
import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.core.service.TenantFrameworkService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class TenantJobAspectTest {

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void tenantJobAspectAutoConfigurationDoesNotDependOnXxlJob() throws Exception {
        Method method = YudaoTenantAutoConfiguration.class.getMethod(
                "tenantJobAspect", TenantFrameworkService.class);

        assertThat(method.getAnnotation(ConditionalOnClass.class)).isNull();
    }

    @Test
    void annotatedJobRunsExactlyOnceInEachTenantContextThroughSpringAop() {
        TenantFrameworkService tenantFrameworkService = new TenantFrameworkService() {
            @Override
            public List<Long> getTenantIds() {
                return List.of(101L, 202L);
            }

            @Override
            public void validTenant(Long id) {
            }
        };
        RecordingJob target = new RecordingJob();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new TenantJobAspect(tenantFrameworkService));
        RecordingJob proxy = factory.getProxy();

        proxy.execute();

        assertThat(target.executedTenantIds).containsExactlyInAnyOrder(101L, 202L);
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void tenantFailurePropagatesToSchedulerInsteadOfLookingSuccessful() {
        TenantFrameworkService tenantFrameworkService = tenantFrameworkService(List.of(101L, 202L));
        FailingJob target = new FailingJob();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new TenantJobAspect(tenantFrameworkService));
        FailingJob proxy = factory.getProxy();

        assertThatThrownBy(proxy::execute)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("202")
                .hasMessageContaining("tenant job failed");
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    private static TenantFrameworkService tenantFrameworkService(List<Long> tenantIds) {
        return new TenantFrameworkService() {
            @Override
            public List<Long> getTenantIds() {
                return tenantIds;
            }

            @Override
            public void validTenant(Long id) {
            }
        };
    }

    public static class RecordingJob {

        private final Queue<Long> executedTenantIds = new ConcurrentLinkedQueue<>();

        @TenantJob
        public String execute() {
            executedTenantIds.add(TenantContextHolder.getRequiredTenantId());
            return "ok";
        }
    }

    public static class FailingJob {

        @TenantJob
        public String execute() {
            Long tenantId = TenantContextHolder.getRequiredTenantId();
            if (tenantId == 202L) {
                throw new IllegalStateException("boom");
            }
            return "ok";
        }
    }
}
