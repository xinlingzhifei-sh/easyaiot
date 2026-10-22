package com.basiclab.iot.system.supervision;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionEvidenceItemDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionEvidenceItemMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionEvidenceItemMapperTest {

    @Test
    void selectByEventIdFiltersEvidenceItemsAndOrdersByCreateTime() {
        initTableInfo();
        List<SupervisionEvidenceItemDO> evidenceItems = List.of(new SupervisionEvidenceItemDO().setId(3001L));
        CapturingMapperHandler handler = new CapturingMapperHandler(evidenceItems);
        SupervisionEvidenceItemMapper mapper = handler.createProxy();

        List<SupervisionEvidenceItemDO> result = mapper.selectByEventId(1001L);

        assertSame(evidenceItems, result);
        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionEvidenceItemDO> queryWrapper =
                (LambdaQueryWrapperX<SupervisionEvidenceItemDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("event_id"));
        assertTrue(sqlSegment.contains("create_time"));
        assertTrue(sqlSegment.contains("id"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(1001L));
    }

    private static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(SupervisionEvidenceItemDO.class) != null) {
            return;
        }
        MapperBuilderAssistant builderAssistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(builderAssistant, SupervisionEvidenceItemDO.class);
    }

    private static final class CapturingMapperHandler implements InvocationHandler {

        private final List<SupervisionEvidenceItemDO> result;
        private Wrapper<SupervisionEvidenceItemDO> queryWrapper;

        private CapturingMapperHandler(List<SupervisionEvidenceItemDO> result) {
            this.result = result;
        }

        private SupervisionEvidenceItemMapper createProxy() {
            return (SupervisionEvidenceItemMapper) Proxy.newProxyInstance(
                    SupervisionEvidenceItemMapper.class.getClassLoader(),
                    new Class[]{SupervisionEvidenceItemMapper.class},
                    this
            );
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("selectList".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof Wrapper) {
                queryWrapper = (Wrapper<SupervisionEvidenceItemDO>) args[0];
                return result;
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            throw new UnsupportedOperationException(method.toString());
        }

        private Wrapper<SupervisionEvidenceItemDO> queryWrapper() {
            return queryWrapper;
        }

    }

}
