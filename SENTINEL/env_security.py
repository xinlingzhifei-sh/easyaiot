"""Agent 子进程环境变量边界。"""

import re
from typing import Any, Dict, Iterable, Mapping, Optional


_BLOCKED_ENV_KEYS = {
    'BASH_ENV',
    'ENV',
    'GCONV_PATH',
    'IFS',
    'LOCPATH',
    'NODE_OPTIONS',
    'PATH',
    'PYTHONHOME',
    'PYTHONPATH',
    'RUBYOPT',
    'SHELLOPTS',
    'SSLKEYLOGFILE',
    'VIRTUAL_ENV',
}
_SAFE_IDENTIFIER = re.compile(r'^[A-Za-z0-9._-]{1,128}$')


def validate_node_identifier(value: Any) -> str:
    text = str(value or '').strip()
    if _SAFE_IDENTIFIER.fullmatch(text) is None:
        raise ValueError('nodeId 仅允许 1-128 位字母、数字、点、下划线和连字符')
    return text


def sanitize_environment_overrides(
    values: Optional[Mapping[str, Any]],
    allowed_keys: Iterable[str],
) -> Dict[str, str]:
    if values is None:
        return {}
    if not isinstance(values, Mapping):
        raise ValueError('env 必须是键值对象')

    allowed = {str(key).upper() for key in allowed_keys}
    result: Dict[str, str] = {}
    for raw_key, raw_value in values.items():
        key = str(raw_key).strip().upper()
        if (
            not key
            or key in _BLOCKED_ENV_KEYS
            or key.startswith('LD_')
            or key.startswith('DYLD_')
            or key.startswith('PYTHON')
        ):
            raise ValueError(f'不允许覆盖进程启动环境变量: {key or "<empty>"}')
        if key not in allowed:
            raise ValueError(f'不支持的部署环境变量: {key}')

        value = str(raw_value)
        if len(value) > 4096 or any(char in value for char in ('\0', '\r', '\n')):
            raise ValueError(f'部署环境变量值无效: {key}')
        result[key] = value
    return result
