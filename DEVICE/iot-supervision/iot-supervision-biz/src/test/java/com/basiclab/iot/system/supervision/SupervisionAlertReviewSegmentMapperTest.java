package com.basiclab.iot.system.supervision;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewSegmentDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewSegmentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionAlertReviewSegmentMapperTest {

    @Test
    void transactionLockHashesTenantNamespaceAndCameraInsidePostgresTransaction() throws Exception {
        Method lockMethod = SupervisionAlertReviewSegmentMapper.class
                .getMethod("acquireTransactionLock", Long.class, String.class, String.class);
        Select select = lockMethod.getAnnotation(Select.class);

        assertTrue(select != null);
        assertTrue(lockMethod.getReturnType() == String.class);
        String sql = String.join(" ", select.value());
        assertTrue(sql.contains("pg_advisory_xact_lock"));
        assertTrue(sql.contains("hashtextextended"));
        assertTrue(sql.contains("tenantId"));
        assertTrue(sql.contains("namespace"));
        assertTrue(sql.contains("lockKey"));
        assertTrue(sql.contains("CAST(#{namespace} AS text)"));
        assertTrue(sql.contains("CAST(#{lockKey} AS text)"));
    }

    @Test
    void selectByReviewItemIdIgnoresSoftDeletedRowsToMatchPartialUniqueIndex() {
        initTableInfo();
        CapturingMapperHandler handler = new CapturingMapperHandler();
        SupervisionAlertReviewSegmentMapper mapper = handler.createProxy();

        mapper.selectByReviewItemId(9001L);

        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionAlertReviewSegmentDO> queryWrapper =
                (LambdaQueryWrapperX<SupervisionAlertReviewSegmentDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("review_item_id"));
        assertTrue(sqlSegment.contains("deleted"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(9001L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(0));
    }

    @Test
    void selectOverlappingIgnoresSoftDeletedRowsToMatchExclusionConstraint() {
        initTableInfo();
        CapturingMapperHandler handler = new CapturingMapperHandler();
        SupervisionAlertReviewSegmentMapper mapper = handler.createProxy();
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 10, 8, 30);

        mapper.selectOverlapping(1001L, "camera-01", startTime, startTime.plusMinutes(2));

        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionAlertReviewSegmentDO> queryWrapper =
                (LambdaQueryWrapperX<SupervisionAlertReviewSegmentDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("camera_id"));
        assertTrue(sqlSegment.contains("deleted"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue("camera-01"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(0));
    }

    @Test
    void selectLatestOpenUsesSmallintSoftDeleteValue() {
        initTableInfo();
        CapturingMapperHandler handler = new CapturingMapperHandler();
        SupervisionAlertReviewSegmentMapper mapper = handler.createProxy();

        mapper.selectLatestOpen(1001L, "camera-01");

        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionAlertReviewSegmentDO> queryWrapper =
                (LambdaQueryWrapperX<SupervisionAlertReviewSegmentDO>) handler.queryWrapper();
        assertTrue(queryWrapper.getSqlSegment().contains("deleted"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(0));
        assertTrue(!queryWrapper.getParamNameValuePairs().containsValue(false));
    }

    private static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(SupervisionAlertReviewSegmentDO.class) != null) {
            return;
        }
        MapperBuilderAssistant builderAssistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(builderAssistant, SupervisionAlertReviewSegmentDO.class);
    }

    private static final class CapturingMapperHandler implements InvocationHandler {

        private Wrapper<SupervisionAlertReviewSegmentDO> queryWrapper;

        private SupervisionAlertReviewSegmentMapper createProxy() {
            return (SupervisionAlertReviewSegmentMapper) Proxy.newProxyInstance(
                    SupervisionAlertReviewSegmentMapper.class.getClassLoader(),
                    new Class[]{SupervisionAlertReviewSegmentMapper.class},
                    this
            );
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("selectOne".equals(method.getName()) && args != null && args.length == 1
                    && args[0] instanceof Wrapper) {
                queryWrapper = (Wrapper<SupervisionAlertReviewSegmentDO>) args[0];
                return new SupervisionAlertReviewSegmentDO().setReviewItemId(9001L);
            }
            if ("selectList".equals(method.getName()) && args != null && args.length == 1
                    && args[0] instanceof Wrapper) {
                queryWrapper = (Wrapper<SupervisionAlertReviewSegmentDO>) args[0];
                return List.of(new SupervisionAlertReviewSegmentDO()
                        .setTenantId(1001L)
                        .setCameraId("camera-01")
                        .setStartTime(LocalDateTime.of(2026, 7, 10, 8, 29))
                        .setEndTime(LocalDateTime.of(2026, 7, 10, 8, 31)));
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            throw new UnsupportedOperationException(method.toString());
        }

        private Wrapper<SupervisionAlertReviewSegmentDO> queryWrapper() {
            return queryWrapper;
        }
    }
}
