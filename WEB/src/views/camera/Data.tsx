import { BasicColumn, FormProps } from '@/components/Table';
import { Tag, Tooltip } from 'ant-design-vue';
import { formatLocationSummary, hasDeviceLocation } from './utils/deviceLocation';
import { isNvrListRow } from './utils/deviceLabel';
import { isGb28181SipListRow } from './utils/gb28181DeviceGroup';

function renderDeviceType(record: Record<string, unknown>) {
  if (isGb28181SipListRow(record as { device_kind?: string; id?: string })) {
    return <Tag color="purple">国标设备</Tag>;
  }
  if (isNvrListRow(record as { device_kind?: string; id?: string })) {
    return <Tag color="blue">NVR</Tag>;
  }
  return <Tag color="default">直连摄像头</Tag>;
}

const accessStateLabel: Record<string, string> = {
  pending_config: '待配置',
  registering: '注册中',
  registered: '已注册',
  stream_online: '流在线',
  play_ready: '可播放',
  ai_ready: 'AI就绪',
  error: '异常',
};

const accessStateColor: Record<string, string> = {
  pending_config: 'default',
  registering: 'processing',
  registered: 'blue',
  stream_online: 'cyan',
  play_ready: 'green',
  ai_ready: 'success',
  error: 'red',
};

function renderAccessState(record: Record<string, any>) {
  const accessState = record.access_state || {};
  const state = accessState.state || 'pending_config';
  const reason = accessState.reason_message || accessState.reason_code || '';
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', minWidth: '0' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '4px', flexWrap: 'wrap' }}>
        <Tag color={accessStateColor[state] || 'default'}>{accessStateLabel[state] || state}</Tag>
        <Tag color={accessState.play_ready ? 'green' : 'default'}>
          {accessState.play_ready ? '播放就绪' : '未就绪'}
        </Tag>
        {accessState.ai_ready ? <Tag color="cyan">AI就绪</Tag> : null}
      </div>
      {reason ? (
        <Tooltip title={reason}>
          <span style={{ color: 'rgba(0,0,0,0.45)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {reason}
          </span>
        </Tooltip>
      ) : null}
    </div>
  );
}

export function getBasicColumns(): BasicColumn[] {
  return [
    {
      title: '接入状态',
      dataIndex: 'access_state',
      width: 220,
      customRender: ({ record }) => renderAccessState(record),
    },
    {
      title: '设备名称',
      dataIndex: 'name',
      width: 180,
      ellipsis: true,
    },
    {
      title: '类型',
      dataIndex: 'device_kind',
      width: 108,
      customRender: ({ record }) => renderDeviceType(record),
    },
    {
      title: '在线状态',
      dataIndex: 'online',
      width: 88,
      customRender: ({ text }) => (
        <Tag color={text ? 'green' : 'red'}>{text ? '在线' : '离线'}</Tag>
      ),
    },
    {
      title: 'IP地址',
      dataIndex: 'ip',
      width: 140,
      ellipsis: true,
      customRender: ({ text, record }) => {
        if (isNvrListRow(record) && record.port) {
          return `${text || '—'}:${record.port}`;
        }
        return text || '—';
      },
    },
    {
      title: '设备型号',
      dataIndex: 'model',
      width: 120,
      ellipsis: true,
      customRender: ({ text }) => text || '—',
    },
    {
      title: '制造商',
      dataIndex: 'manufacturer',
      width: 100,
      ellipsis: true,
      customRender: ({ text }) => text || '—',
    },
    {
      title: '位置',
      dataIndex: 'has_location',
      width: 140,
      defaultHidden: true,
      customRender: ({ record }) => {
        if (hasDeviceLocation(record)) {
          return formatLocationSummary(record);
        }
        return '未设置';
      },
    },
    {
      title: '所属NVR',
      dataIndex: 'nvr_label',
      width: 120,
      ellipsis: true,
      customRender: ({ text }) => text || '—',
    },
    {
      title: '通道数',
      dataIndex: 'channel_count',
      width: 72,
      align: 'center',
      customRender: ({ text, record }) => {
        if (isGb28181SipListRow(record) || isNvrListRow(record)) {
          return text ?? record.channel_count ?? '—';
        }
        return '—';
      },
    },
    {
      title: '地图坐标',
      dataIndex: 'has_location',
      width: 168,
      ellipsis: true,
      customRender: ({ record }) => {
        if (hasDeviceLocation(record)) {
          return formatLocationSummary(record);
        }
        return <span style={{ color: 'rgba(0,0,0,0.25)' }}>未设置</span>;
      },
    },
    {
      title: '转发状态',
      dataIndex: 'stream_status',
      width: 96,
      align: 'center',
    },
    {
      title: '操作',
      dataIndex: 'action',
      width: 300,
      fixed: 'right',
      align: 'center',
    },
  ];
}

export function getFormConfig(): Partial<FormProps> {
  return {
    labelWidth: 80,
    baseColProps: { span: 6 },
    showAdvancedButton: false,
    // 8 + 6 + 10 = 24，筛选项与查询/重置同一行且按钮靠右
    actionColOptions: {
      span: 10,
      style: { textAlign: 'right' },
    },
    schemas: [
      {
        field: 'deviceName',
        label: '设备名称',
        component: 'Input',
        colProps: { span: 8 },
        componentProps: {
          placeholder: '名称 / IP 模糊搜索',
        },
      },
      {
        field: 'online',
        label: '在线状态',
        component: 'Select',
        colProps: { span: 6 },
        componentProps: {
          options: [
            { value: '', label: '全部' },
            { value: true, label: '在线' },
            { value: false, label: '离线' },
          ],
        },
      },
    ],
  };
}
