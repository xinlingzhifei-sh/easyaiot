package com.basiclab.iot.sink.service.device.impl;

import com.basiclab.iot.common.core.KeyValue;
import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.core.util.TenantUtils;
import com.basiclab.iot.sink.biz.dto.IotDeviceRespDTO;
import com.basiclab.iot.sink.dal.dataobject.DeviceDO;
import com.basiclab.iot.sink.dal.mapper.DeviceMapper;
import com.basiclab.iot.sink.service.device.DeviceService;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;

import static com.basiclab.iot.common.utils.cache.CacheUtils.buildAsyncReloadingCache;

/**
 * DeviceServiceImpl
 *
 * @author reese
 * @email reese
 */

@Service
@Slf4j
public class DeviceServiceImpl implements DeviceService {

    private static final Duration CACHE_EXPIRE = Duration.ofMinutes(1);

    /**
     * Guava LoadingCache 不允许 null；用哨兵表示「查无」，避免抛 UncheckedExecutionException。
     */
    private static final IotDeviceRespDTO NOT_FOUND = new IotDeviceRespDTO();

    /**
     * 通过 id 查询设备的缓存
     */
    private final LoadingCache<TenantDeviceIdKey, IotDeviceRespDTO> deviceCaches = buildAsyncReloadingCache(
            CACHE_EXPIRE,
            new CacheLoader<TenantDeviceIdKey, IotDeviceRespDTO>() {
                @Override
                public IotDeviceRespDTO load(TenantDeviceIdKey key) {
                    return TenantUtils.execute(key.getTenantId(), () -> {
                        Long id = key.getDeviceId();
                        DeviceDO deviceDO = deviceMapper.selectById(id);
                        if (deviceDO == null) {
                            log.warn("[getDevice][设备不存在 id={} tenantId={}]", id, key.getTenantId());
                            return NOT_FOUND;
                        }
                        IotDeviceRespDTO device = convertToDTO(deviceDO);
                        // 相互缓存
                        deviceCaches2.put(new TenantDeviceKey(device.getTenantId(),
                                device.getProductIdentification(), device.getDeviceIdentification()), device);
                        return device;
                    });
                }
            });

    /**
     * 通过 productIdentification + deviceIdentification 查询设备的缓存
     */
    private final LoadingCache<TenantDeviceKey, IotDeviceRespDTO> deviceCaches2 = buildAsyncReloadingCache(
            CACHE_EXPIRE,
            new CacheLoader<TenantDeviceKey, IotDeviceRespDTO>() {
                @Override
                public IotDeviceRespDTO load(TenantDeviceKey key) {
                    return TenantUtils.execute(key.getTenantId(), () -> {
                        KeyValue<String, String> kv = new KeyValue<>(
                                key.getProductIdentification(), key.getDeviceIdentification());
                        DeviceDO deviceDO = deviceMapper.selectByProductIdentificationAndDeviceIdentification(
                                kv.getKey(), kv.getValue());
                        if (deviceDO == null) {
                            log.warn("[getDevice][设备不存在 product={}/device={} tenantId={}，请确认 Topic 与库中标识一致]",
                                    kv.getKey(), kv.getValue(), key.getTenantId());
                            return NOT_FOUND;
                        }
                        IotDeviceRespDTO device = convertToDTO(deviceDO);
                        // 相互缓存
                        deviceCaches.put(new TenantDeviceIdKey(device.getTenantId(), device.getId()), device);
                        return device;
                    });
                }
            });

    @Resource
    private DeviceMapper deviceMapper;

    @Override
    public IotDeviceRespDTO getDevice(String productIdentification, String deviceIdentification) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        IotDeviceRespDTO device = deviceCaches2.getUnchecked(new TenantDeviceKey(
                tenantId, productIdentification, deviceIdentification));
        return device == NOT_FOUND ? null : device;
    }

    @Override
    public IotDeviceRespDTO getDeviceIgnoreTenant(String productIdentification, String deviceIdentification) {
        Boolean oldIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setIgnore(true);
            DeviceDO deviceDO = deviceMapper.selectByProductIdentificationAndDeviceIdentification(
                    productIdentification, deviceIdentification);
            if (deviceDO == null) {
                log.warn("[getDeviceIgnoreTenant][设备不存在 product={}/device={}]",
                        productIdentification, deviceIdentification);
                return null;
            }
            return convertToDTO(deviceDO);
        } finally {
            TenantContextHolder.setIgnore(oldIgnore);
        }
    }

    @Override
    public IotDeviceRespDTO getDevice(Long id) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        IotDeviceRespDTO device = deviceCaches.getUnchecked(new TenantDeviceIdKey(tenantId, id));
        return device == NOT_FOUND ? null : device;
    }

    @Override
    public DeviceDO getDeviceForAuth(String clientId, String userName, String password, String deviceStatus, String protocolType) {
        return deviceMapper.selectByClientIdAndUserNameAndPasswordAndDeviceStatusAndProtocolType(
                clientId, userName, password, deviceStatus, protocolType);
    }

    /**
     * 将 DeviceDO 转换为 IotDeviceRespDTO
     */
    private IotDeviceRespDTO convertToDTO(DeviceDO deviceDO) {
        IotDeviceRespDTO dto = new IotDeviceRespDTO();
        dto.setId(deviceDO.getId());
        dto.setProductIdentification(deviceDO.getProductIdentification());
        dto.setDeviceIdentification(deviceDO.getDeviceIdentification());
        dto.setProtocolType(deviceDO.getProtocolType());
        dto.setIpAddress(deviceDO.getIpAddress());
        dto.setExtension(deviceDO.getExtension());
        dto.setTenantId(deviceDO.getTenantId());
        dto.setDeviceType(deviceDO.getDeviceType());
        dto.setParentIdentification(deviceDO.getParentIdentification());
        return dto;
    }

    @Override
    public DeviceDO getDeviceForProtocolAuth(String clientId, String productIdentification, String deviceIdentification,
                                             String deviceStatus, String protocolType) {
        return deviceMapper.selectDeviceForAuth(clientId, productIdentification, deviceIdentification,
                deviceStatus, protocolType);
    }

    @Value
    private static class TenantDeviceIdKey {
        Long tenantId;
        Long deviceId;
    }

    @Value
    private static class TenantDeviceKey {
        Long tenantId;
        String productIdentification;
        String deviceIdentification;
    }
}

