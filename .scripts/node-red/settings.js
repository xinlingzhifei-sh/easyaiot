/**
 * yFeiEye Node-RED settings
 * - 页面标题：yFeiEye
 * - 内置 4 条演示规则链只读保护（禁止 PUT/DELETE，POST /flows 时强制回填锁定副本）
 *
 * 由 docker-compose 挂载到容器 /data/settings.js
 * 锁定工程：同目录 easyaiot_flows_demo.json（只读挂载）
 */
const fs = require('fs');
const path = require('path');

const DEMO_FLOW_IDS = new Set([
    'easyaiot_demo_telemetry',
    'easyaiot_demo_alert',
    'easyaiot_demo_bridge',
    'easyaiot_demo_vision',
]);

const DEMO_LABELS = new Set([
    '设备遥测采集链路',
    '告警分级推送链路',
    '工控协议桥接链路',
    '视觉质检联动链路',
]);

const LOCKED_FLOWS_PATH = path.join(__dirname, 'easyaiot_flows_demo.json');

function loadLockedFlows() {
    try {
        const raw = fs.readFileSync(LOCKED_FLOWS_PATH, 'utf8');
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed) ? parsed : [];
    } catch (e) {
        return [];
    }
}

function isDemoTabId(id) {
    return !!(id && DEMO_FLOW_IDS.has(String(id)));
}

function belongsToDemo(node) {
    if (!node || typeof node !== 'object') {
        return false;
    }
    if (node.type === 'tab') {
        return isDemoTabId(node.id) || DEMO_LABELS.has(String(node.label || ''));
    }
    // 配置节点（如 mqtt-broker）用固定 demo_ 前缀
    if (!node.z && node.id && String(node.id).indexOf('demo_') === 0) {
        return true;
    }
    return isDemoTabId(node.z);
}

function enforceLockedDemos(flows) {
    if (!Array.isArray(flows)) {
        return flows;
    }
    const locked = loadLockedFlows();
    if (!locked.length) {
        return flows;
    }
    const kept = flows.filter((n) => !belongsToDemo(n));
    return kept.concat(locked);
}

function pathOnly(url) {
    return String(url || '').split('?')[0];
}

function demoGuardMiddleware(req, res, next) {
    const method = req.method;
    const p = pathOnly(req.path || req.url);

    // 禁止删除 / 更新单个演示页签
    if ((method === 'DELETE' || method === 'PUT') && p.indexOf('/flow/') === 0) {
        const id = decodeURIComponent(p.slice('/flow/'.length));
        if (isDemoTabId(id) || DEMO_LABELS.has(id)) {
            res.status(403).json({
                error: 'forbidden',
                message: 'yFeiEye 演示规则链为只读，禁止修改或删除',
                id: id,
            });
            return;
        }
    }

    // 全量部署：强制回填锁定演示链路，避免编辑器 Deploy 改删演示数据
    if (method === 'POST' && (p === '/flows' || p === '/flows/')) {
        try {
            if (Array.isArray(req.body)) {
                req.body = enforceLockedDemos(req.body);
            } else if (req.body && Array.isArray(req.body.flows)) {
                req.body.flows = enforceLockedDemos(req.body.flows);
            }
        } catch (e) {
            // 回填失败不阻断，交给后续 DELETE/PUT 保护 + seed 恢复
        }
    }

    next();
}

module.exports = {
    flowFile: 'flows.json',
    flowFilePretty: true,
    uiPort: process.env.PORT || 1880,

    /** 静态资源：演示只读脚本 */
    httpStatic: path.join(__dirname, 'public'),

    /** 页面 / 顶栏标题改为 yFeiEye */
    editorTheme: {
        page: {
            title: 'yFeiEye',
            scripts: ['/easyaiot-demo-guard.js'],
        },
        header: {
            title: 'yFeiEye',
        },
        tours: false,
        projects: {
            enabled: false,
            workflow: {
                mode: 'manual',
            },
        },
        codeEditor: {
            lib: 'monaco',
        },
    },

    /** 演示规则链只读保护 */
    httpAdminMiddleware: demoGuardMiddleware,

    functionGlobalContext: {
        easyaiotDemoFlowIds: Array.from(DEMO_FLOW_IDS),
    },

    logging: {
        console: {
            level: 'info',
            metrics: false,
            audit: false,
        },
    },

    exportGlobalContextKeys: false,
};
