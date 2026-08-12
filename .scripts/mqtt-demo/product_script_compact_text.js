/**
 * 紧凑文本私有协议（yFeiEye EA 协议）
 * 复制到「产品管理 → 协议脚本」或通过 API 保存；须启用后热加载。
 *
 * 上行设备 → 平台（rawDataToProtocol）:
 *   EA|UP|<tenantId>|<requestId>|<TYPE>|<k=v;k=v>
 *   TYPE: PROP | EVENT | LOG | SVC_ACK | DESIRED_ACK
 *
 * 下行平台 → 设备（protocolToRawData）:
 *   EA|DN|<requestId>|<TYPE>|<identifier>|<payload>
 *   TYPE: SVC | SET
 *
 * 与 .scripts/mqtt-demo/04_codec_uplink.py / 05_codec_downlink.py 配套。
 */
function rawDataToProtocol(topic, bytes) {
  var text = jsUtil.bytesToUtf8(bytes);
  if (text.indexOf('EA|UP|') !== 0) {
    return bytes;
  }
  var parts = text.split('|');
  if (parts.length < 6) {
    throw 'EA uplink format error: ' + text;
  }
  var tenantId = parseInt(parts[2], 10);
  var requestId = parts[3];
  var type = parts[4];
  var kv = parts.slice(5).join('|');
  var params = parseKv(kv);
  var method = 'thing.property.post';
  var code = null;
  var msg = null;
  var data = null;
  if (type === 'PROP') {
    method = 'thing.property.post';
  } else if (type === 'EVENT') {
    method = 'thing.event.post';
  } else if (type === 'LOG') {
    method = 'thing.log.post';
  } else if (type === 'SVC_ACK') {
    method = 'thing.service.invoke';
    data = { success: true };
    code = 0;
    msg = mapGet(params, 'msg') || 'ok';
  } else if (type === 'DESIRED_ACK') {
    method = 'thing.property.set';
    data = { success: true };
    code = 0;
    msg = mapGet(params, 'msg') || 'ok';
  }
  return jsUtil.toStandardMessage(tenantId, requestId, method, params, data, code, msg);
}

function protocolToRawData(topic, message) {
  var method = String(mapGet(message, 'method') || '');
  var requestId = String(mapGet(message, 'requestId') || '');
  var params = mapGet(message, 'params') || {};
  var identifier = extractIdentifier(topic, method);
  var type = 'SVC';
  if (method.indexOf('property.set') >= 0 || (topic && topic.indexOf('/desired/set') >= 0)) {
    type = 'SET';
  } else {
    type = 'SVC';
  }
  // 禁止 JSON.stringify(Java Map)，会得到 {}
  var payload = typeof params === 'string' ? params : jsUtil.toJsonString(params);
  var line = 'EA|DN|' + requestId + '|' + type + '|' + identifier + '|' + payload;
  return jsUtil.utf8Bytes(line);
}

function parseKv(text) {
  var map = jsUtil.newMap();
  if (!text) return map;
  var pairs = String(text).split(';');
  for (var i = 0; i < pairs.length; i++) {
    var p = pairs[i];
    if (!p) continue;
    var idx = p.indexOf('=');
    if (idx <= 0) continue;
    map.put(p.substring(0, idx), p.substring(idx + 1));
  }
  return map;
}

function mapGet(obj, key) {
  if (obj == null) return null;
  if (typeof obj.get === 'function') {
    try { return obj.get(key); } catch (e) {}
  }
  return obj[key];
}

function extractIdentifier(topic, method) {
  if (topic) {
    var segs = String(topic).split('/');
    for (var i = 0; i < segs.length; i++) {
      if (segs[i] === 'invoke' && i + 1 < segs.length) {
        return segs[i + 1];
      }
    }
    if (String(topic).indexOf('/desired/set') >= 0) {
      return 'desired';
    }
  }
  return method || 'unknown';
}
