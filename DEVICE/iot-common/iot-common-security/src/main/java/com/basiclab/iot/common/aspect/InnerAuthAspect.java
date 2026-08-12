package com.basiclab.iot.common.aspect;

import com.basiclab.iot.common.annotations.InnerAuth;
import com.basiclab.iot.common.exception.InnerAuthException;
import com.basiclab.iot.common.service.RpcInternalAccess;
import com.basiclab.iot.common.utils.SecurityFrameworkUtils;
import com.basiclab.iot.common.utils.ServletUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * 内部服务调用验证处理
 *
 * @author reese
 * @email reese
 */
@Aspect
@Component
public class InnerAuthAspect implements Ordered
{
    private final RpcInternalAccess rpcInternalAccess;

    public InnerAuthAspect(RpcInternalAccess rpcInternalAccess)
    {
        this.rpcInternalAccess = rpcInternalAccess;
    }

    @Around("@annotation(innerAuth)")
    public Object innerAround(ProceedingJoinPoint point, InnerAuth innerAuth) throws Throwable
    {
        if (!rpcInternalAccess.isAllowed(ServletUtils.getRequest()))
        {
            throw new InnerAuthException("没有内部访问权限，不允许访问");
        }

        if (innerAuth.isUser() && SecurityFrameworkUtils.getLoginUser() == null)
        {
            throw new InnerAuthException("没有已验证的用户信息，不允许访问");
        }
        return point.proceed();
    }

    /**
     * 确保在权限认证aop执行前执行
     */
    @Override
    public int getOrder()
    {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
